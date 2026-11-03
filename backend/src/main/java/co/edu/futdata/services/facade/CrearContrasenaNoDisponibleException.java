package co.edu.futdata.services.facade;

/**
 * Supabase Auth falló mientras se creaba la contraseña (no respondió, {@code 5xx} o configuración). {@code 503}. Si
 * la falla fue después de verificar, el enlace ya se consumió: por eso el mensaje sugiere pedir que lo reenvíen.
 */
public class CrearContrasenaNoDisponibleException extends RuntimeException {

    public static final String MENSAJE = "Inténtalo de nuevo; si persiste, pide que te reenvíen el enlace.";

    public CrearContrasenaNoDisponibleException() {
        super(MENSAJE);
    }
}
