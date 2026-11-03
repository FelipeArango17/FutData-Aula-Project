package co.edu.futdata.datasource.external;

/**
 * Supabase Auth no respondió bien: falta configuración, {@code 5xx}, {@code 429}, tiempo de espera agotado, error
 * de red, llave rechazada o respuesta inesperada. Para el usuario es un 503: se puede intentar más tarde.
 */
public class AuthNoDisponibleException extends AuthException {

    public AuthNoDisponibleException(String mensaje, Integer codigoHttp) {
        super(mensaje, codigoHttp);
    }
}
