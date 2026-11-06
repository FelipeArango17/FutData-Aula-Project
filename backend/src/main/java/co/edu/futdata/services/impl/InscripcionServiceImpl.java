package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.DeportistaMapper;
import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.domain.entities.Acudiente;
import co.edu.futdata.domain.entities.DatosAcudiente;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.rules.ReglasAcudiente;
import co.edu.futdata.domain.rules.ReglasDeportista;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IAcudienteDAO;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IInscripcionService;
import co.edu.futdata.services.facade.InscripcionEntrada;
import co.edu.futdata.services.facade.InscripcionResultado;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Inscripción única (bloque 2B): crea el deportista, crea o reutiliza al acudiente por correo y los vincula con su
 * parentesco, todo o nada.
 *
 * <p>Orden: (1) autoriza (solo el ADMINISTRADOR, falla cerrado); (2) limpia y valida <b>todo junto</b>, con los campos
 * prefijados {@code deportista.<campo>} y {@code acudiente.<campo>}; (3) revisa el documento repetido <b>antes</b> de
 * abrir la transacción; (4) {@link #prepararCuenta}: decide la cuenta de acceso y, si hace falta, la crea en Supabase
 * Auth; (5) una transacción corta con {@code usuario} y su rol (si la cuenta es nueva), el deportista, el acudiente y
 * el vínculo; si falla, {@link #compensarCuenta}; (6) {@link #enviarEnlace} después del commit (regla 9).
 *
 * <p>Un acudiente que ya existe por correo se reutiliza <b>sin cambiar sus datos</b> (los nuevos se ignoran).
 *
 * <p><b>Registros:</b> nunca correos, nombres, teléfonos, documentos ni el id de la cuenta; ante una falla, solo el
 * tipo de la excepción.
 */
@Service
public class InscripcionServiceImpl implements IInscripcionService {

    static final String DOCUMENTO_REPETIDO = "deportista.documento: ya está registrado en otro deportista";
    static final String CORREO_EN_CARRERA = "acudiente.correo: otra inscripción registró ese correo al mismo tiempo; "
            + "vuelve a intentarlo";
    static final String CORREO_DE_OTRO_ROL = "acudiente.correo: ese correo ya pertenece a una cuenta con otro rol";
    static final int MAX_NOMBRE_DEPORTISTA = 100;

    private static final Logger log = LoggerFactory.getLogger(InscripcionServiceImpl.class);

    private final IDeportistaDAO deportistas;
    private final IAcudienteDAO acudientes;
    private final IUsuarioDAO usuarios;
    private final CuentasDeAcceso cuentas;
    private final TransactionOperations transaccion;
    private final Clock reloj;

    /** @param reloj el de {@code RelojConfig} (hora de Colombia); las pruebas pasan uno fijo */
    public InscripcionServiceImpl(IDeportistaDAO deportistas, IAcudienteDAO acudientes, IUsuarioDAO usuarios,
                                  CuentasDeAcceso cuentas, TransactionOperations transaccion, Clock reloj) {
        this.deportistas = deportistas;
        this.acudientes = acudientes;
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /** Sin {@code @Transactional}: la transacción es corta y programática; lo externo (Auth, n8n) queda fuera de ella. */
    @Override
    public InscripcionResultado inscribir(InscripcionEntrada entrada, Solicitante solicitante) {
        exigirAdministrador(solicitante);
        if (entrada == null) {
            throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        }
        Deportista deportista = limpiarDeportista(entrada.deportista());
        DatosAcudiente acudiente = ReglasAcudiente.limpiar(entrada.acudiente());
        validar(deportista, acudiente);
        if (deportistas.existeDocumento(deportista.getDocumento())) {
            throw documentoRepetido();
        }

        Cuenta cuenta = prepararCuenta(acudiente);
        InscripcionResultado resultado;
        try {
            resultado = transaccion.execute(estado -> guardar(deportista, acudiente, cuenta));
        } catch (RuntimeException e) {
            compensarCuenta(cuenta);
            throw e;
        }
        Boolean correoEnviado = enviarEnlace(cuenta, acudiente);
        return new InscripcionResultado(resultado.deportista(), resultado.acudiente(), resultado.reutilizado(),
                resultado.cuentaCreada(), resultado.parentesco(), correoEnviado);
    }

    // --- Autorización y validación --------------------------------------------------------------------------------

    /** Falla cerrado: cualquier rol que no sea ADMINISTRADOR, un rol vacío o un solicitante nulo es un 403. */
    private static void exigirAdministrador(Solicitante solicitante) {
        if (solicitante == null || !solicitante.esAdministrador()) {
            throw new SinPermisoException();
        }
    }

    private static Deportista limpiarDeportista(DeportistaRequestDTO datos) {
        Deportista limpio = ReglasDeportista.limpiar(
                DeportistaMapper.toEntity(datos == null ? new DeportistaRequestDTO(null, null, null, null, null, null, null)
                        : datos));
        limpio.setActivo(true);
        return limpio;
    }

    /** Todos los errores juntos, de deportista y acudiente, con el campo prefijado. */
    private void validar(Deportista deportista, DatosAcudiente acudiente) {
        Map<String, String> errores = new LinkedHashMap<>();
        // La regla del deportista recibe el tipo nunca nulo: una lista inmutable no admite contains(null)
        String tipo = deportista.getTipoDocumento();
        if (tipo == null) {
            deportista.setTipoDocumento("");
        }
        Map<String, String> deDeportista = new LinkedHashMap<>(
                ReglasDeportista.validar(deportista, LocalDate.now(reloj)));
        largoMaximo(deDeportista, "nombre", deportista.getNombre());
        largoMaximo(deDeportista, "apellido", deportista.getApellido());
        deDeportista.forEach((campo, mensaje) -> errores.put("deportista." + campo, mensaje));
        ReglasAcudiente.validar(acudiente).forEach((campo, mensaje) -> errores.put("acudiente." + campo, mensaje));
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
    }

    /** La regla del deportista no acota nombre y apellido; la columna sí (100): sin esto sería un 500 al insertar. */
    private static void largoMaximo(Map<String, String> errores, String campo, String valor) {
        if (!errores.containsKey(campo) && valor != null && valor.length() > MAX_NOMBRE_DEPORTISTA) {
            errores.put(campo, "debe tener como máximo " + MAX_NOMBRE_DEPORTISTA + " caracteres");
        }
    }

    // --- Transacción ----------------------------------------------------------------------------------------------

    /** Deportista, acudiente y vínculo en una sola transacción: si algo falla, no queda nada. */
    private InscripcionResultado guardar(Deportista deportista, DatosAcudiente datos, Cuenta cuenta) {
        UUID idDeportista;
        try {
            idDeportista = deportistas.crear(deportista);
        } catch (DatoDuplicadoException e) {
            throw documentoRepetido(); // otra inscripción pasó la revisión previa al mismo tiempo
        }
        registrarCuentaEnLaBase(cuenta, datos);
        Resolucion acudiente = resolverAcudiente(datos, cuenta);
        acudientes.vincular(acudiente.acudiente().id(), idDeportista, datos.parentesco());
        Deportista guardado = deportistas.buscarPorId(idDeportista, LocalDate.now(reloj).getYear())
                .orElseThrow(() -> new IllegalStateException("El deportista recién creado no se encuentra"));
        return new InscripcionResultado(guardado, acudiente.acudiente(), acudiente.reutilizado(), cuenta.creada(),
                datos.parentesco(), null);
    }

    private record Resolucion(Acudiente acudiente, boolean reutilizado) {
    }

    /**
     * Reutiliza al acudiente con ese correo sin cambiar sus datos (y le enlaza la cuenta si no tenía); si no hay, la
     * ficha que ya tenga la cuenta reutilizada (puede tener otro correo); si tampoco, lo crea con la cuenta.
     */
    private Resolucion resolverAcudiente(DatosAcudiente datos, Cuenta cuenta) {
        Optional<Acudiente> existente = acudientes.buscarPorCorreo(datos.correo());
        if (existente.isPresent()) {
            return new Resolucion(enlazarCuentaAlAcudiente(existente.get(), cuenta), true);
        }
        if (cuenta.idUsuario() != null && !cuenta.creada()) {
            Optional<Acudiente> deLaCuenta = acudientes.buscarPorUsuario(cuenta.idUsuario());
            if (deLaCuenta.isPresent()) {
                return new Resolucion(deLaCuenta.get(), true);
            }
        }
        try {
            return new Resolucion(acudientes.insertar(datos.nombre(), datos.apellido(), datos.correo(),
                    datos.telefono(), cuenta.idUsuario()), false);
        } catch (DatoDuplicadoException e) {
            // Otra inscripción creó al acudiente después de buscarlo: se deshace todo y se pide reintentar
            throw new ConflictoException(CORREO_EN_CARRERA, List.of("acudiente.correo"));
        }
    }

    private static ConflictoException documentoRepetido() {
        return new ConflictoException(DOCUMENTO_REPETIDO, List.of("deportista.documento"));
    }

    private static ConflictoException correoEnCarrera() {
        return new ConflictoException(CORREO_EN_CARRERA, List.of("acudiente.correo"));
    }

    // --- La cuenta de acceso del acudiente (regla 9, con CuentasDeAcceso del bloque 5) -------------------------------

    /**
     * La cuenta de acceso que la inscripción usará o creará. {@code idUsuario} nulo = el acudiente queda sin cuenta
     * (no ocurre: siempre se resuelve una); {@code creada} = la inscripción la creó en Supabase Auth, así que hay que
     * compensarla si la base falla y enviarle el enlace después del commit.
     */
    private record Cuenta(UUID idUsuario, boolean creada) {
    }

    /**
     * Antes de la transacción, en este orden: (1) la ficha de acudiente con ese correo ya tiene cuenta: se usa esa;
     * (2) hay un {@code usuario} con ese correo: si tiene el rol ACUDIENTE se reutiliza su cuenta, si no es un
     * {@code 409}; (3) si no, se crea la cuenta en Supabase Auth sin contraseña. Nada de esto envía correos.
     *
     * @throws ConflictoException {@code 409} en {@code acudiente.correo}: cuenta de otro rol, o Auth ya tenía el correo
     *                            (otra inscripción al mismo tiempo)
     * @throws co.edu.futdata.datasource.external.AuthNoDisponibleException {@code 503}: no se guardó nada
     */
    private Cuenta prepararCuenta(DatosAcudiente acudiente) {
        Optional<UUID> deLaFicha = acudientes.buscarPorCorreo(acudiente.correo()).map(Acudiente::idUsuario);
        if (deLaFicha.isPresent()) {
            return new Cuenta(deLaFicha.get(), false);
        }
        Optional<Usuario> usuario = usuarios.buscarPorCorreo(acudiente.correo());
        if (usuario.isPresent()) {
            if (!usuarios.tieneRol(usuario.get().getId(), Solicitante.ACUDIENTE)) {
                throw new ConflictoException(CORREO_DE_OTRO_ROL, List.of("acudiente.correo"));
            }
            return new Cuenta(usuario.get().getId(), false);
        }
        try {
            return new Cuenta(cuentas.crearCuentaAuth(acudiente.correo()), true);
        } catch (ConflictoException e) {
            throw correoEnCarrera(); // Auth ya tenía el correo, pero no había usuario: otra petición va por delante
        }
    }

    /** Dentro de la transacción, antes de resolver al acudiente: {@code usuario} y su rol ACUDIENTE si la cuenta es nueva. */
    private void registrarCuentaEnLaBase(Cuenta cuenta, DatosAcudiente acudiente) {
        if (!cuenta.creada()) {
            return;
        }
        try {
            cuentas.registrarUsuario(cuenta.idUsuario(), acudiente.nombre(), acudiente.apellido(), acudiente.correo(),
                    Solicitante.ACUDIENTE);
        } catch (ConflictoException e) {
            throw correoEnCarrera(); // otra petición creó el usuario con ese correo después de revisarlo
        }
    }

    /**
     * Dentro de la transacción, para una ficha que ya existía: si no tenía cuenta, le enlaza la de la inscripción. Si
     * no se puede (otra petición la enlazó primero, o esa cuenta ya es de otra ficha), {@code 409} de reintento.
     */
    private Acudiente enlazarCuentaAlAcudiente(Acudiente existente, Cuenta cuenta) {
        if (existente.idUsuario() != null || cuenta.idUsuario() == null) {
            return existente;
        }
        boolean enlazado;
        try {
            enlazado = acudientes.enlazarUsuario(existente.id(), cuenta.idUsuario());
        } catch (DatoDuplicadoException e) {
            throw correoEnCarrera();
        }
        if (!enlazado) {
            throw correoEnCarrera();
        }
        return new Acudiente(existente.id(), existente.nombre(), existente.apellido(), existente.correo(),
                existente.telefono(), cuenta.idUsuario());
    }

    /**
     * Si la transacción falló: borra la cuenta de Auth que esta inscripción creó. Nunca lanza, para no ocultar la
     * excepción original; si el borrado falla, solo registra su tipo (ni el correo ni el id de la cuenta).
     */
    private void compensarCuenta(Cuenta cuenta) {
        if (cuenta.creada()) {
            cuentas.intentarEliminarCuentaAuth(cuenta.idUsuario()).ifPresent(tipo -> log.error(
                    "Inscripción: la base no guardó y no se pudo borrar la cuenta de Auth recién creada ({}); "
                            + "hay que borrarla a mano", tipo));
        }
    }

    /**
     * Después del commit; nunca deshace la inscripción. Solo para una cuenta creada por esta inscripción: genera el
     * enlace y pide a n8n el correo de bienvenida.
     *
     * @return {@code true} si n8n lo aceptó; {@code false} si algo falló (Auth, {@code FRONTEND_URL}, n8n, o un
     *         {@code 400} porque n8n aún no conoce la plantilla); {@code null} si no había cuenta nueva
     */
    private Boolean enviarEnlace(Cuenta cuenta, DatosAcudiente acudiente) {
        if (!cuenta.creada()) {
            return null;
        }
        try {
            cuentas.enviarEnlace(acudiente.correo(), acudiente.nombre(), CuentasDeAcceso.PLANTILLA_BIENVENIDA_ACUDIENTE);
            return true;
        } catch (RuntimeException e) {
            log.warn("Inscripción: el acudiente quedó inscrito pero no se envió el enlace para crear la contraseña ({})",
                    e.getClass().getSimpleName());
            return false;
        }
    }
}

