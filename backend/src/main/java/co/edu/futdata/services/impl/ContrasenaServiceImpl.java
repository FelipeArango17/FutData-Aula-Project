package co.edu.futdata.services.impl;

import co.edu.futdata.datasource.external.AuthException;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.datasource.external.SesionRecuperacion;
import co.edu.futdata.datasource.external.TokenRecuperacionInvalidoException;
import co.edu.futdata.domain.rules.ReglasContrasena;
import co.edu.futdata.services.facade.CrearContrasenaNoDisponibleException;
import co.edu.futdata.services.facade.DemasiadasSolicitudesException;
import co.edu.futdata.services.facade.EnlaceInvalidoException;
import co.edu.futdata.services.facade.IContrasenaService;
import co.edu.futdata.services.facade.PoliticaContrasenaException;
import co.edu.futdata.util.security.IpCliente;
import co.edu.futdata.util.security.LimiteDeFrecuencia;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Crear la contraseña con el enlace del correo (regla 9). Orden: token presente, política de la contraseña (antes de
 * verificar, porque verificar consume el enlace), límites de intentos, verificación, fijar la contraseña por id y
 * cerrar la sesión que abrió la verificación. No inicia sesión.
 *
 * <p><b>Límites en memoria</b> (se pierden al reiniciar y no se comparten entre instancias; ver
 * {@link LimiteDeFrecuencia}): {@value #POR_IP} intentos por minuto por IP, {@value #POR_TOKEN} por minuto por token
 * (por su SHA-256, no el token) y {@value #GLOBAL} por minuto en total. El de la IP es el que pidió el equipo; como la
 * IP depende de Cloudflare ({@link IpCliente}), los otros dos acotan el daño si se falsifica, y el global protege el
 * límite de verificaciones por IP de Supabase, que cuenta la IP del backend (la misma para todos los usuarios). Un
 * intento que falla por la política o por falta de token no cuenta: no llega a Supabase.
 *
 * <p>Nunca registra el token, la contraseña ni la IP completa.
 */
@Service
public class ContrasenaServiceImpl implements IContrasenaService {

    private static final Logger log = LoggerFactory.getLogger(ContrasenaServiceImpl.class);

    static final int POR_IP = 10;
    static final int POR_TOKEN = 5;
    static final int GLOBAL = 60;
    static final Duration VENTANA = Duration.ofMinutes(1);
    static final String DEMASIADOS = "Demasiados intentos. Espera un momento y vuelve a intentarlo.";
    private static final String CLAVE_GLOBAL = "global";

    private final ISupabaseAuthAdmin auth;
    private final LimiteDeFrecuencia porIp;
    private final LimiteDeFrecuencia porToken;
    private final LimiteDeFrecuencia global;

    public ContrasenaServiceImpl(ISupabaseAuthAdmin auth, Clock reloj) {
        this.auth = auth;
        this.porIp = new LimiteDeFrecuencia(POR_IP, VENTANA, reloj);
        this.porToken = new LimiteDeFrecuencia(POR_TOKEN, VENTANA, reloj);
        this.global = new LimiteDeFrecuencia(GLOBAL, VENTANA, reloj);
    }

    @Override
    public void crearContrasena(String token, String contrasena, String ipCliente) {
        if (token == null || token.isBlank()) {
            throw new EnlaceInvalidoException();
        }
        if (!ReglasContrasena.cumple(contrasena)) {
            throw new PoliticaContrasenaException();
        }
        String ip = ipCliente == null || ipCliente.isBlank() ? "(sin IP)" : ipCliente;
        limitar(ip, sha256(token.strip()));

        SesionRecuperacion sesion;
        try {
            sesion = auth.verificarTokenRecuperacion(token.strip());
        } catch (TokenRecuperacionInvalidoException e) {
            log.info("Crear contraseña: enlace inválido, vencido o usado (IP {})", IpCliente.enmascarada(ip));
            throw new EnlaceInvalidoException();
        } catch (AuthException e) {
            log.warn("Crear contraseña: Supabase Auth falló al verificar ({})", e.getMessage());
            throw new CrearContrasenaNoDisponibleException();
        }

        try {
            auth.fijarContrasena(sesion.idUsuario(), contrasena);
        } catch (AuthException e) {
            // El enlace ya se consumió: el usuario tendrá que pedir otro
            log.error("Crear contraseña: no se pudo fijar la contraseña de la cuenta {} con el enlace ya usado ({})",
                    sesion.idUsuario(), e.getMessage());
            cerrar(sesion);
            throw new CrearContrasenaNoDisponibleException();
        }
        cerrar(sesion);
        log.info("Contraseña creada para la cuenta {}", sesion.idUsuario());
    }

    /** Cierra la sesión que abrió la verificación. Si falla, solo se registra: la contraseña ya quedó creada. */
    private void cerrar(SesionRecuperacion sesion) {
        try {
            auth.cerrarSesion(sesion.tokenAcceso());
        } catch (AuthException e) {
            log.warn("No se pudo cerrar la sesión de verificación de la cuenta {} ({})", sesion.idUsuario(), e.getMessage());
        }
    }

    /** Los tres límites; si uno no deja pasar, se devuelven los cupos que ya se tomaron en esta llamada. */
    private void limitar(String ip, String hashToken) {
        Optional<Duration> espera = porIp.intentar(ip);
        if (espera.isPresent()) {
            throw demasiados("IP", ip, espera.get());
        }
        espera = porToken.intentar(hashToken);
        if (espera.isPresent()) {
            porIp.liberar(ip);
            throw demasiados("token", ip, espera.get());
        }
        espera = global.intentar(CLAVE_GLOBAL);
        if (espera.isPresent()) {
            porIp.liberar(ip);
            porToken.liberar(hashToken);
            throw demasiados("global", ip, espera.get());
        }
    }

    private static DemasiadasSolicitudesException demasiados(String limite, String ip, Duration espera) {
        log.warn("Crear contraseña: límite {} alcanzado (IP {})", limite, IpCliente.enmascarada(ip));
        return new DemasiadasSolicitudesException(DEMASIADOS, espera);
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
