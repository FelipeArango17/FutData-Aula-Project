package co.edu.futdata.datasource.external;

/**
 * Supabase Auth rechazó la petición por su forma ({@code 400} o {@code 422}): un error de programación o de contrato
 * (por ejemplo, un {@code ban_duration} con formato inválido), no algo que se arregle reintentando. Para el usuario es
 * un {@code 500}; el log lo dice claro.
 */
public class AuthPeticionInvalidaException extends AuthException {

    public AuthPeticionInvalidaException(String operacion, Integer codigoHttp) {
        super("Supabase Auth rechazó la petición para " + operacion + " (" + codigoHttp + "): revisa el contrato", codigoHttp);
    }
}
