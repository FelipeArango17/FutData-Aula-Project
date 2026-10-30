package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.api.dto.EntrenadorMapper;
import co.edu.futdata.api.dto.EntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.rules.ReglasEntrenador;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IEntrenadorService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.FormatoColombiano;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntrenadorServiceImpl implements IEntrenadorService {

    static final String NO_EXISTE = "No existe un entrenador con ese id.";
    static final String YA_REGISTRADO = "ya está registrado en otro entrenador";

    private final IEntrenadorDAO entrenadores;
    private final ICategoriaDAO categorias;
    private final Clock reloj;

    @Autowired
    public EntrenadorServiceImpl(IEntrenadorDAO entrenadores, ICategoriaDAO categorias) {
        this(entrenadores, categorias, Clock.system(FormatoColombiano.ZONA));
    }

    /** Para las pruebas: con un reloj fijo para la fecha de nacimiento. */
    EntrenadorServiceImpl(IEntrenadorDAO entrenadores, ICategoriaDAO categorias, Clock reloj) {
        this.entrenadores = entrenadores;
        this.categorias = categorias;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntrenadorResponseDTO> listar() {
        return entrenadores.listar().stream().map(EntrenadorMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EntrenadorResponseDTO consultar(UUID id) {
        return EntrenadorMapper.toDTO(buscar(id));
    }

    @Override
    @Transactional
    public EntrenadorResponseDTO crear(EntrenadorRequestDTO datos) {
        Entrenador nuevo = validar(datos);
        revisarDuplicados(nuevo, null);
        UUID id = guardando(() -> entrenadores.crear(nuevo));
        return consultar(id);
    }

    @Override
    @Transactional
    public EntrenadorResponseDTO editar(UUID id, EntrenadorRequestDTO datos) {
        buscar(id);
        Entrenador cambios = validar(datos);
        cambios.setId(id);
        revisarDuplicados(cambios, id);
        if (!guardando(() -> entrenadores.actualizar(cambios))) {
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        return consultar(id);
    }

    @Override
    @Transactional
    public EntrenadorResponseDTO asignarCategorias(UUID id, AsignarCategoriasRequestDTO peticion) {
        Set<String> pedidas = categoriasPedidas(peticion);
        buscar(id);

        // Bloquea las categorías hasta el final de la transacción: dos asignaciones a la vez no se pisan
        Map<String, Categoria> existentes = categorias.listarParaActualizar().stream()
                .collect(Collectors.toMap(Categoria::getNombre, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<String> sinCrear = pedidas.stream().filter(nombre -> !existentes.containsKey(nombre)).toList();
        if (!sinCrear.isEmpty()) {
            throw DatosInvalidosException.de("categorias", enumerar(sinCrear)
                    + (sinCrear.size() == 1 ? " todavía no está creada" : " todavía no están creadas")
                    + " en la base de datos");
        }

        List<String> quitadas = existentes.values().stream()
                .filter(c -> id.equals(c.getIdEntrenador()) && !pedidas.contains(c.getNombre()))
                .map(Categoria::getNombre)
                .toList();
        if (!quitadas.isEmpty()) {
            boolean una = quitadas.size() == 1;
            throw new ConflictoException((una ? "No se puede quitar " : "No se pueden quitar ") + enumerar(quitadas)
                    + ": cada categoría debe tener un entrenador. Para "
                    + (una ? "quitársela, asígnala" : "quitárselas, asígnalas") + " a otro entrenador.",
                    List.of("categorias"));
        }

        List<Categoria> deOtros = existentes.values().stream()
                .filter(c -> pedidas.contains(c.getNombre()) && !id.equals(c.getIdEntrenador()))
                .toList();
        if (!deOtros.isEmpty() && !peticion.reasignacionConfirmada()) {
            boolean una = deOtros.size() == 1;
            throw new ConflictoException(enumerar(deOtros.stream().map(Categoria::getNombre).toList())
                    + (una ? " ya tiene otro entrenador. Confirma la reasignación para pasarla"
                           : " ya tienen otro entrenador. Confirma la reasignación para pasarlas")
                    + " a este entrenador.", List.of("categorias"));
        }

        categorias.asignarEntrenador(deOtros.stream().map(Categoria::getId).toList(), id);
        return consultar(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarCategorias() {
        Map<UUID, Entrenador> porId = entrenadores.listar().stream()
                .collect(Collectors.toMap(Entrenador::getId, Function.identity()));
        return categorias.listar().stream()
                .map(c -> EntrenadorMapper.toDTO(c, porId.get(c.getIdEntrenador())))
                .toList();
    }

    private Entrenador buscar(UUID id) {
        return entrenadores.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
    }

    private Entrenador validar(EntrenadorRequestDTO datos) {
        if (datos == null) {
            throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        }
        Entrenador limpio = ReglasEntrenador.limpiar(EntrenadorMapper.toEntity(datos));
        Map<String, String> errores = ReglasEntrenador.validar(limpio, LocalDate.now(reloj));
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return limpio;
    }

    /** Documento primero y correo después, como el formulario del frontend. Sin datos del otro entrenador. */
    private void revisarDuplicados(Entrenador e, UUID excluirId) {
        List<String> repetidos = new ArrayList<>();
        if (entrenadores.existeDocumento(e.getTipoDocumento(), e.getDocumento(), excluirId)) {
            repetidos.add("documento");
        }
        if (entrenadores.existeCorreo(e.getCorreo(), excluirId)) {
            repetidos.add("correo");
        }
        if (!repetidos.isEmpty()) {
            throw duplicado(repetidos);
        }
    }

    /** La revisión previa no cubre dos peticiones al mismo tiempo: si la base las rechaza, es el mismo 409. */
    private static <T> T guardando(Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (DatoDuplicadoException e) {
            throw duplicado(List.of(e.getCampo()));
        }
    }

    private static ConflictoException duplicado(List<String> campos) {
        String mensaje = campos.stream().map(campo -> campo + ": " + YA_REGISTRADO).collect(Collectors.joining("; "));
        return new ConflictoException(mensaje, campos);
    }

    private static Set<String> categoriasPedidas(AsignarCategoriasRequestDTO peticion) {
        if (peticion == null || peticion.categorias() == null) {
            throw DatosInvalidosException.de("categorias", "es obligatoria (puede ser una lista vacía)");
        }
        Set<String> pedidas = new LinkedHashSet<>();
        for (String nombre : peticion.categorias()) {
            if (nombre == null || nombre.isBlank()) {
                throw DatosInvalidosException.de("categorias", "no puede tener nombres vacíos");
            }
            pedidas.add(nombre.strip());
        }
        List<String> desconocidas = pedidas.stream().filter(n -> !ReglasEntrenador.esCategoria(n)).toList();
        if (!desconocidas.isEmpty()) {
            throw DatosInvalidosException.de("categorias", enumerar(desconocidas)
                    + (desconocidas.size() == 1 ? " no es una categoría" : " no son categorías")
                    + " del club (Sub-8, Sub-10, Sub-12, Sub-14 o Sub-16)");
        }
        return pedidas;
    }

    /** {@code Sub-8}, {@code Sub-8 y Sub-10}, {@code Sub-8, Sub-10 y Sub-12}, en el orden oficial. */
    private static String enumerar(List<String> nombres) {
        List<String> ordenados = ReglasEntrenador.enOrdenOficial(nombres);
        if (ordenados.size() == 1) {
            return ordenados.get(0);
        }
        return String.join(", ", ordenados.subList(0, ordenados.size() - 1)) + " y " + ordenados.getLast();
    }
}
