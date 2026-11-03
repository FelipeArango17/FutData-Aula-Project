package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.api.dto.EntrenadorMapper;
import co.edu.futdata.api.dto.EntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import co.edu.futdata.datasource.external.NotificacionException;
import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.rules.ReglasEntrenador;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.DemasiadasSolicitudesException;
import co.edu.futdata.services.facade.IEntrenadorService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.security.LimiteDeFrecuencia;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class EntrenadorServiceImpl implements IEntrenadorService {

    static final String NO_EXISTE = "No existe un entrenador con ese id.";
    static final String YA_REGISTRADO = "ya está registrado en otro entrenador";
    static final String INACTIVO = "El entrenador está inactivo. Actívalo antes de asignarle categorías.";
    static final String INACTIVO_REENVIO = "El entrenador está inactivo. Actívalo antes de reenviarle el enlace.";
    static final String SIN_CORREO = "El entrenador no tiene correo. Agrégalo antes de enviarle el enlace.";
    static final String ESPERA_REENVIO = "Ya se envió un enlace a este entrenador hace menos de un minuto. Espera un momento.";
    static final String CORREO_CON_CUENTA = "No se puede cambiar el correo de un entrenador que ya tiene cuenta de acceso: "
            + "es el correo con el que inicia sesión.";
    static final String ROL = "ENTRENADOR";
    /** Reenvío: 1 envío por minuto por entrenador; el alta cuenta como un envío. */
    static final Duration VENTANA_ENVIO = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(EntrenadorServiceImpl.class);

    private final IEntrenadorDAO entrenadores;
    private final ICategoriaDAO categorias;
    private final CuentasDeAcceso cuentas;
    private final TransactionOperations transaccion;
    private final Clock reloj;
    /** En memoria: se pierde al reiniciar y no se comparte entre instancias (ver LimiteDeFrecuencia). */
    private final LimiteDeFrecuencia envios;

    /**
     * @param transaccion para lo que va en una transacción explícita (alta, reenvío y cambio de estado), porque las
     *                    llamadas a Supabase Auth y a n8n quedan fuera de ella
     * @param reloj       hora de Colombia ({@code config.RelojConfig}); en las pruebas, uno fijo para la fecha de nacimiento
     */
    public EntrenadorServiceImpl(IEntrenadorDAO entrenadores, ICategoriaDAO categorias, CuentasDeAcceso cuentas,
                                 TransactionOperations transaccion, Clock reloj) {
        this.entrenadores = entrenadores;
        this.categorias = categorias;
        this.cuentas = cuentas;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.envios = new LimiteDeFrecuencia(1, VENTANA_ENVIO, reloj);
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

    /**
     * Alta con cuenta de acceso (regla 9). (1) Crea la cuenta de Supabase Auth sin contraseña. (2) En una transacción:
     * el entrenador, {@code usuario}, {@code usuario_rol} (ENTRENADOR) e {@code id_usuario}; si falla, borra la cuenta
     * de Auth. (3) Después del commit genera el enlace y pide el correo: si algo falla ahí, el alta sigue en pie y
     * responde {@code correoEnviado=false} (se usa "Reenviar enlace").
     */
    @Override
    public EntrenadorResponseDTO crear(EntrenadorRequestDTO datos) {
        Entrenador nuevo = validar(datos);
        revisarDuplicados(nuevo, null);
        cuentas.exigirCorreoLibre(nuevo.getCorreo());
        UUID idUsuario = cuentas.crearCuentaAuth(nuevo.getCorreo());
        UUID id = conCompensacion(idUsuario, () -> {
            UUID creado = guardando(() -> entrenadores.crear(nuevo));
            registrarCuenta(creado, idUsuario, nuevo);
            return creado;
        });
        boolean enviado = enviarEnAlta(id, nuevo.getCorreo(), nuevo.getNombre());
        return conCorreoEnviado(consultar(id), enviado);
    }

    /**
     * Reenvía el enlace para crear la contraseña. Sin cuenta, la crea (con la misma compensación del alta); con cuenta,
     * genera un enlace nuevo, que invalida el anterior. A diferencia del alta, una falla sí se responde como error.
     */
    @Override
    public void reenviarEnlace(UUID id) {
        Entrenador entrenador = buscar(id);
        if (!entrenador.isActivo()) {
            throw new ConflictoException(INACTIVO_REENVIO, List.of("activo"));
        }
        if (entrenador.getIdUsuario() == null && entrenador.getCorreo() == null) {
            throw new ConflictoException(SIN_CORREO, List.of("correo"));
        }
        Optional<Duration> espera = envios.intentar(id);
        if (espera.isPresent()) {
            throw new DemasiadasSolicitudesException(ESPERA_REENVIO, espera.get());
        }
        try {
            String correo;
            if (entrenador.getIdUsuario() == null) {
                correo = entrenador.getCorreo();
                cuentas.exigirCorreoLibre(correo);
                UUID idUsuario = cuentas.crearCuentaAuth(correo);
                conCompensacion(idUsuario, () -> {
                    registrarCuenta(id, idUsuario, entrenador);
                    return id;
                });
            } else {
                correo = cuentas.correoDeCuenta(entrenador.getIdUsuario());
            }
            cuentas.enviarEnlace(correo, entrenador.getNombre(), CuentasDeAcceso.PLANTILLA_BIENVENIDA_ENTRENADOR);
        } catch (NotificacionException e) {
            // El enlace nuevo ya existe (invalidó el anterior): el cupo del minuto se queda gastado
            throw e;
        } catch (RuntimeException e) {
            // No se generó ningún enlace: se puede reintentar enseguida
            envios.liberar(id);
            throw e;
        }
    }

    @Override
    @Transactional
    public EntrenadorResponseDTO editar(UUID id, EntrenadorRequestDTO datos) {
        Entrenador actual = buscar(id);
        Entrenador cambios = validar(datos);
        cambios.setId(id);
        // Deuda: cambiar el correo no actualiza usuario.correo ni Supabase Auth, así que con cuenta se bloquea
        if (actual.getIdUsuario() != null && !mismoCorreo(actual.getCorreo(), cambios.getCorreo())) {
            throw new ConflictoException(CORREO_CON_CUENTA, List.of("correo"));
        }
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
        Entrenador entrenador = buscar(id);
        if (!entrenador.isActivo() && !pedidas.isEmpty()) {
            throw new ConflictoException(INACTIVO, List.of("activo"));
        }

        // Bloquea las categorías hasta el final de la transacción: dos asignaciones a la vez no se pisan
        Map<String, Categoria> existentes = categorias.listarParaActualizar().stream()
                .collect(Collectors.toMap(Categoria::getNombre, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<String> sinCrear = pedidas.stream().filter(nombre -> !existentes.containsKey(nombre)).toList();
        if (!sinCrear.isEmpty()) {
            throw DatosInvalidosException.de("categorias", enumerar(sinCrear)
                    + (sinCrear.size() == 1 ? " todavía no está creada" : " todavía no están creadas")
                    + " en la base de datos");
        }

        List<Categoria> nuevas = existentes.values().stream()
                .filter(c -> pedidas.contains(c.getNombre()) && !id.equals(c.getIdEntrenador()))
                .toList();
        List<Categoria> deOtros = nuevas.stream().filter(c -> c.getIdEntrenador() != null).toList();
        if (!deOtros.isEmpty() && !peticion.reasignacionConfirmada()) {
            boolean una = deOtros.size() == 1;
            throw new ConflictoException(enumerar(deOtros.stream().map(Categoria::getNombre).toList())
                    + (una ? " ya tiene otro entrenador. Confirma la reasignación para pasarla"
                           : " ya tienen otro entrenador. Confirma la reasignación para pasarlas")
                    + " a este entrenador.", List.of("categorias"));
        }

        // Las que tenía y no pidió quedan sin entrenador (decisión D7)
        List<UUID> quitadas = existentes.values().stream()
                .filter(c -> id.equals(c.getIdEntrenador()) && !pedidas.contains(c.getNombre()))
                .map(Categoria::getId)
                .toList();

        categorias.asignarEntrenador(nuevas.stream().map(Categoria::getId).toList(), id);
        categorias.asignarEntrenador(quitadas, null);
        return consultar(id);
    }

    /**
     * Con cuenta: primero Supabase Auth (baneo o restauración), después la base en una transacción (entrenador,
     * {@code usuario.activo} y, al desactivar, sus sesiones verificadas). Si la base falla, Auth se revierte. Sin
     * cuenta: solo la fila. Las franjas no se tocan (quedan con {@code entrenadorActivo=false}).
     */
    @Override
    public EntrenadorResponseDTO cambiarEstado(UUID id, CambiarEstadoEntrenadorRequestDTO peticion) {
        if (peticion == null || peticion.activo() == null) {
            throw DatosInvalidosException.de("activo", "es obligatorio (true o false)");
        }
        Entrenador entrenador = buscar(id);
        boolean activo = peticion.activo();
        if (entrenador.isActivo() == activo) {
            return EntrenadorMapper.toDTO(entrenador);
        }
        UUID idUsuario = entrenador.getIdUsuario();
        // false si la cuenta no existe en Auth y se está desactivando: se sigue con la base y no hay nada que revertir
        boolean authCambiado = idUsuario != null && cuentas.cambiarAccesoAuth(idUsuario, activo);
        try {
            transaccion.executeWithoutResult(estado -> cambiarEstadoEnBase(id, idUsuario, activo));
        } catch (RuntimeException e) {
            if (authCambiado) {
                cuentas.revertirAccesoAuth(idUsuario, !activo);
            }
            throw e;
        }
        return consultar(id);
    }

    private void cambiarEstadoEnBase(UUID id, UUID idUsuario, boolean activo) {
        if (!activo) {
            // Bloquea las categorías y suelta las suyas: un inactivo no dirige ninguna (decisión D8)
            List<UUID> suyas = categorias.listarParaActualizar().stream()
                    .filter(c -> id.equals(c.getIdEntrenador()))
                    .map(Categoria::getId)
                    .toList();
            categorias.asignarEntrenador(suyas, null);
            // TODO HU-026: cuando exista el módulo de entrenamientos, decidir qué pasa con sus entrenamientos
            // PROGRAMADO futuros (avisar o reasignar) antes de desactivarlo.
        }
        if (!entrenadores.cambiarActivo(id, activo)) {
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        if (idUsuario != null) {
            cuentas.cambiarAccesoBase(idUsuario, activo);
        }
    }

    /** Iguales sin distinguir mayúsculas ni espacios al principio o al final. */
    private static boolean mismoCorreo(String a, String b) {
        String x = a == null ? "" : a.strip().toLowerCase(Locale.ROOT);
        String y = b == null ? "" : b.strip().toLowerCase(Locale.ROOT);
        return x.equals(y);
    }

    /** {@code usuario}, {@code usuario_rol} e {@code id_usuario}, en la transacción de quien llama. */
    private void registrarCuenta(UUID idEntrenador, UUID idUsuario, Entrenador datos) {
        cuentas.registrarUsuario(idUsuario, datos.getNombre(), datos.getApellido(), datos.getCorreo(), ROL);
        if (!entrenadores.vincularUsuario(idEntrenador, idUsuario)) {
            // Otro reenvío lo vinculó primero, o el entrenador ya no existe: se deshace todo
            throw new ConflictoException("El entrenador ya tiene una cuenta o ya no existe.", List.of("id"));
        }
    }

    /** Ejecuta {@code escritura} en una transacción; si falla (incluido el commit), borra la cuenta de Auth. */
    private <T> T conCompensacion(UUID idUsuario, Supplier<T> escritura) {
        try {
            return transaccion.execute(estado -> escritura.get());
        } catch (RuntimeException e) {
            cuentas.eliminarCuentaAuth(idUsuario);
            throw e;
        }
    }

    /** Después del commit del alta. Nunca falla: el resultado va en {@code correoEnviado}. */
    private boolean enviarEnAlta(UUID id, String correo, String nombre) {
        envios.intentar(id); // el alta cuenta como un envío
        try {
            cuentas.enviarEnlace(correo, nombre, CuentasDeAcceso.PLANTILLA_BIENVENIDA_ENTRENADOR);
            return true;
        } catch (NotificacionException e) {
            log.warn("Alta del entrenador {}: el enlace se generó pero el correo no salió ({})", id, e.getClass().getSimpleName());
            return false;
        } catch (RuntimeException e) {
            envios.liberar(id);
            log.warn("Alta del entrenador {}: no se generó el enlace ({})", id, e.getClass().getSimpleName());
            return false;
        }
    }

    private static EntrenadorResponseDTO conCorreoEnviado(EntrenadorResponseDTO e, boolean enviado) {
        return new EntrenadorResponseDTO(e.id(), e.nombre(), e.apellido(), e.tipoDocumento(), e.documento(),
                e.fechaNacimiento(), e.telefono(), e.correo(), e.especialidad(), e.categorias(), e.horarioTrabajo(),
                e.direccion(), e.activo(), enviado);
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
                    + " del club (" + CATEGORIAS_OFICIALES + ")");
        }
        return pedidas;
    }

    /** Los nombres oficiales para el mensaje de error: "Párvulos, Cachorros, Semillero, Sub-11, ... o Sub-16". */
    private static final String CATEGORIAS_OFICIALES = String.join(", ",
            ReglasEntrenador.CATEGORIAS.subList(0, ReglasEntrenador.CATEGORIAS.size() - 1))
            + " o " + ReglasEntrenador.CATEGORIAS.getLast();

    /** {@code Semillero}, {@code Semillero y Sub-11}, {@code Semillero, Sub-11 y Sub-12}, en el orden oficial. */
    private static String enumerar(List<String> nombres) {
        List<String> ordenados = ReglasEntrenador.enOrdenOficial(nombres);
        if (ordenados.size() == 1) {
            return ordenados.get(0);
        }
        return String.join(", ", ordenados.subList(0, ordenados.size() - 1)) + " y " + ordenados.getLast();
    }
}
