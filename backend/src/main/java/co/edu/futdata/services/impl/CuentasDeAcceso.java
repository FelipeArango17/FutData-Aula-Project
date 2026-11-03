package co.edu.futdata.services.impl;

import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.AuthException;
import co.edu.futdata.datasource.external.AuthNoDisponibleException;
import co.edu.futdata.datasource.external.CuentaAuthNoEncontradaException;
import co.edu.futdata.datasource.external.CorreoYaRegistradoException;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Piezas de las cuentas de acceso con enlace para crear la contraseña (regla 9), sin contraseña temporal. Las usa el
 * alta y el reenvío del enlace de entrenadores; el bloque 2B las usará para los acudientes.
 *
 * <p>No abre transacciones: quien la usa decide qué va junto en la base y cuándo compensar en Supabase Auth.
 * <b>Nunca</b> registra el token ni el enlace; en los logs va solo el id de la cuenta y el tipo de falla.
 *
 * <p>Deuda: el administrador y el tesorero se siguen creando con la invitación de Supabase
 * ({@code UsuarioServiceImpl} e {@code ISupabaseAuthAdmin.invitar}), no con este camino.
 */
@Component
public class CuentasDeAcceso {

    private static final Logger log = LoggerFactory.getLogger(CuentasDeAcceso.class);

    static final String OTRA_CUENTA = "Ese correo ya pertenece a otra cuenta";
    static final String CUENTA_AUTH_INEXISTENTE =
            "La cuenta de acceso de este entrenador no existe en Supabase Auth; revisa la configuración";
    public static final String PLANTILLA_BIENVENIDA_ENTRENADOR = "BIENVENIDA_ENTRENADOR";
    /**
     * Vigencia del enlace que se anuncia en el correo: 1 hora. Debe coincidir con la de Supabase Auth
     * ({@code mailer_otp_exp = 3600}, Email OTP Expiration). Si vence, el administrador lo reenvía.
     */
    public static final int VIGENCIA_ENLACE_HORAS = 1;

    private final ISupabaseAuthAdmin auth;
    private final IUsuarioDAO usuarios;
    private final ISesionVerificadaDAO sesiones;
    private final INotificador notificador;
    private final FrontendProperties frontend;

    public CuentasDeAcceso(ISupabaseAuthAdmin auth, IUsuarioDAO usuarios, ISesionVerificadaDAO sesiones,
                           INotificador notificador, FrontendProperties frontend) {
        this.auth = auth;
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.notificador = notificador;
        this.frontend = frontend;
    }

    /** {@code 409} si ya hay un usuario de FutData con ese correo (sin distinguir mayúsculas). */
    void exigirCorreoLibre(String correo) {
        if (usuarios.existeCorreo(correo)) {
            throw otraCuenta();
        }
    }

    /**
     * Crea la cuenta de Supabase Auth sin contraseña.
     *
     * @throws ConflictoException        si Supabase ya tiene una cuenta con ese correo ({@code 409})
     * @throws AuthNoDisponibleException si Supabase Auth no responde bien ({@code 503})
     */
    UUID crearCuentaAuth(String correo) {
        try {
            return auth.crearUsuario(correo);
        } catch (CorreoYaRegistradoException e) {
            throw otraCuenta();
        }
    }

    /** Fila de {@code usuario} y su rol, en la transacción de quien llama. Un correo repetido es el mismo 409. */
    void registrarUsuario(UUID id, String nombre, String apellido, String correo, String rol) {
        Usuario nuevo = new Usuario();
        nuevo.setId(id);
        nuevo.setNombre(nombre);
        nuevo.setApellido(apellido);
        nuevo.setCorreo(correo);
        try {
            usuarios.crear(nuevo);
            usuarios.asignarRol(id, rol);
        } catch (DuplicateKeyException | DatoDuplicadoException e) {
            throw otraCuenta();
        }
    }

    /** Compensación: borra la cuenta de Auth recién creada porque la base falló. Si no se puede, solo lo registra. */
    void eliminarCuentaAuth(UUID id) {
        try {
            auth.eliminar(id);
            log.info("Cuenta de Auth {} borrada: la base de datos no guardó el alta", id);
        } catch (RuntimeException e) {
            log.error("No se pudo borrar la cuenta de Auth {} tras la falla de la base ({}); hay que borrarla a mano",
                    id, e.getClass().getSimpleName());
        }
    }

    /** Correo de la cuenta; el enlace se genera para el correo de Auth, que es el de {@code usuario}. */
    String correoDeCuenta(UUID idUsuario) {
        return usuarios.buscarPorId(idUsuario).map(Usuario::getCorreo)
                .orElseThrow(() -> new IllegalStateException("El entrenador apunta a una cuenta que no existe en usuario"));
    }

    /**
     * Genera un enlace nuevo (invalida el anterior) y pide a n8n el correo. Primero revisa {@code FRONTEND_URL}, para
     * no invalidar el enlace anterior si no se puede armar el nuevo.
     *
     * @throws IllegalStateException     si falta {@code FRONTEND_URL} (no se generó nada)
     * @throws AuthException             si Supabase no generó el enlace (no se generó nada)
     * @throws co.edu.futdata.datasource.external.NotificacionException si n8n no envió el correo (el enlace ya existe)
     */
    void enviarEnlace(String correo, String nombre, String plantilla) {
        if (!frontend.configurada()) {
            log.error("No se envió el enlace para crear la contraseña: falta FRONTEND_URL");
            throw new IllegalStateException("FRONTEND_URL no está configurada");
        }
        String enlace = frontend.enlaceCrearContrasena(auth.generarTokenRecuperacion(correo));
        notificador.notificar(INotificador.ENLACE_CONTRASENA, Map.of(
                "plantilla", plantilla,
                "correo", correo,
                "nombre", nombre,
                "enlace", enlace,
                "vigenciaHoras", VIGENCIA_ENLACE_HORAS));
    }

    /**
     * Bloquea ({@code activo=false}) o restaura el acceso en Supabase Auth. Si falla, no se cambió nada todavía.
     *
     * <p>Si la cuenta no existe en Auth ({@code 404}): al bloquear, devuelve {@code false} y se sigue con la base (sin
     * cuenta nadie entra, y {@code usuario.activo=false} cierra el paso en el backend); al restaurar, {@code 409} sin
     * cambiar nada. En los dos casos registra un error: con la FK {@code usuario -> auth.users ON DELETE RESTRICT}, lo
     * más probable es que {@code SUPABASE_URL} y {@code DB_URL} sean de proyectos distintos.
     *
     * @return {@code true} si cambió el acceso en Auth (y hay que revertirlo si la base falla); {@code false} si la
     *         cuenta no existe y se está bloqueando
     * @throws ConflictoException            al restaurar una cuenta que no existe en Auth
     * @throws AuthNoDisponibleException     Auth no disponible, llave rechazada, límite o configuración ({@code 503})
     * @throws co.edu.futdata.datasource.external.AuthPeticionInvalidaException error de contrato ({@code 500})
     */
    boolean cambiarAccesoAuth(UUID idUsuario, boolean activo) {
        try {
            auth.cambiarAcceso(idUsuario, activo);
            return true;
        } catch (CuentaAuthNoEncontradaException e) {
            log.error("La cuenta {} no existe en Supabase Auth al {} el acceso: ¿SUPABASE_URL y DB_URL son de proyectos "
                    + "distintos? (la FK usuario -> auth.users impide borrar una cuenta con perfil)",
                    idUsuario, activo ? "restaurar" : "bloquear");
            if (activo) {
                throw new ConflictoException(CUENTA_AUTH_INEXISTENTE, List.of("activo"));
            }
            return false;
        } catch (AuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("No se pudo {} el acceso de la cuenta {} en Supabase Auth ({})",
                    activo ? "restaurar" : "revocar", idUsuario, e.getClass().getSimpleName());
            throw new AuthNoDisponibleException("No se pudo cambiar el acceso en Supabase Auth", null);
        }
    }

    /** Compensación de {@link #cambiarAccesoAuth}: deja Auth como estaba. Si no se puede, solo lo registra. */
    void revertirAccesoAuth(UUID idUsuario, boolean activoOriginal) {
        try {
            auth.cambiarAcceso(idUsuario, activoOriginal);
        } catch (RuntimeException e) {
            log.error("No se pudo revertir el acceso de Auth de la cuenta {} ({}); revísala a mano",
                    idUsuario, e.getClass().getSimpleName());
        }
    }

    /** {@code usuario.activo} y, al revocar, borra sus sesiones verificadas (deberá pasar el 2FA otra vez). */
    void cambiarAccesoBase(UUID idUsuario, boolean activo) {
        usuarios.actualizarActivo(idUsuario, activo);
        if (!activo) {
            sesiones.eliminarDeUsuario(idUsuario);
        }
    }

    private static ConflictoException otraCuenta() {
        return new ConflictoException(OTRA_CUENTA, List.of("correo"));
    }
}
