package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.FranjaHorarioMapper;
import co.edu.futdata.api.dto.FranjaHorarioRequestDTO;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.domain.rules.ReglasEntrenador;
import co.edu.futdata.domain.rules.ReglasFranja;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IFranjaHorarioService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FranjaHorarioServiceImpl implements IFranjaHorarioService {

    static final String NO_EXISTE = "No existe una franja con ese id.";
    static final String CRUCE = "Ese entrenador ya tiene otra franja que se cruza con este horario";
    static final String CATEGORIA_NO_EXISTE = "no existe una categoría con ese id";
    static final String ENTRENADOR_NO_EXISTE = "no existe un entrenador con ese id";
    static final String ENTRENADOR_INACTIVO = "el entrenador está inactivo; actívalo o elige otro";

    private final IFranjaHorarioDAO franjas;
    private final IEntrenadorDAO entrenadores;

    public FranjaHorarioServiceImpl(IFranjaHorarioDAO franjas, IEntrenadorDAO entrenadores) {
        this.franjas = franjas;
        this.entrenadores = entrenadores;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FranjaHorarioResponseDTO> listar(String categoria, UUID idEntrenador, String sede) {
        Map<String, String> errores = new LinkedHashMap<>();
        String filtroCategoria = vacioANull(categoria);
        if (filtroCategoria != null && !ReglasEntrenador.esCategoria(filtroCategoria)) {
            errores.put("categoria", "debe ser una de " + String.join(", ", ReglasEntrenador.CATEGORIAS));
        }
        String filtroSede = vacioANull(sede);
        if (filtroSede != null && !ReglasFranja.esSede(filtroSede)) {
            errores.put("sede", "debe ser " + String.join(" o ", ReglasFranja.SEDES));
        }
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        return franjas.listar(filtroCategoria, idEntrenador, filtroSede).stream()
                .map(FranjaHorarioMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FranjaHorarioResponseDTO consultar(UUID id) {
        return FranjaHorarioMapper.toDTO(buscar(id));
    }

    @Override
    @Transactional
    public FranjaHorarioResponseDTO crear(FranjaHorarioRequestDTO datos) {
        FranjaHorario nueva = validar(datos, null);
        UUID id = guardando(() -> franjas.crear(nueva));
        return consultar(id);
    }

    @Override
    @Transactional
    public FranjaHorarioResponseDTO actualizar(UUID id, FranjaHorarioRequestDTO datos) {
        FranjaHorario actual = buscar(id);
        FranjaHorario cambios = validar(datos, actual);
        cambios.setId(id);
        if (!guardando(() -> franjas.actualizar(cambios))) {
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        return consultar(id);
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        if (!franjas.eliminar(id)) {
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
    }

    private FranjaHorario buscar(UUID id) {
        return franjas.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
    }

    /**
     * Reglas de {@link ReglasFranja} más las que dependen de otros registros, todas juntas en un solo {@code 400}.
     *
     * @param actual la franja guardada en un {@code PUT}; {@code null} en un {@code POST}. Si el {@code PUT} conserva
     *               el mismo entrenador, no se revisa si está activo
     */
    private FranjaHorario validar(FranjaHorarioRequestDTO datos, FranjaHorario actual) {
        if (datos == null) {
            throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        }
        ReglasFranja.Entrada e = ReglasFranja.limpiar(FranjaHorarioMapper.toEntrada(datos));

        Optional<String> categoria = e.idCategoria() == null ? Optional.empty()
                : franjas.nombreDeCategoria(e.idCategoria());
        Map<String, String> errores = new LinkedHashMap<>(ReglasFranja.validar(e, categoria.orElse(null)));
        if (e.idCategoria() != null && categoria.isEmpty()) {
            errores.put("idCategoria", CATEGORIA_NO_EXISTE);
        }

        if (e.idEntrenador() != null) {
            Optional<Entrenador> entrenador = entrenadores.buscarPorId(e.idEntrenador());
            boolean mismoQueAntes = actual != null && Objects.equals(e.idEntrenador(), actual.getIdEntrenador());
            if (entrenador.isEmpty()) {
                errores.put("idEntrenador", ENTRENADOR_NO_EXISTE);
            } else if (!entrenador.get().isActivo() && !mismoQueAntes) {
                errores.put("idEntrenador", ENTRENADOR_INACTIVO);
            }
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        FranjaHorario f = new FranjaHorario();
        f.setIdCategoria(e.idCategoria());
        f.setDia(e.dia());
        f.setHoraInicio(ReglasFranja.hora(e.horaInicio()).orElseThrow());
        f.setHoraFin(ReglasFranja.hora(e.horaFin()).orElseThrow());
        f.setSede(e.sede());
        f.setIdEntrenador(e.idEntrenador());
        f.setGrupo(e.grupo());
        return f;
    }

    /** El cruce lo decide la restricción de la base (sin consulta previa): si la rechaza, es un {@code 409}. */
    private static <T> T guardando(Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (CruceDeHorarioException ex) {
            throw new ConflictoException(CRUCE, List.of("idEntrenador"));
        }
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
