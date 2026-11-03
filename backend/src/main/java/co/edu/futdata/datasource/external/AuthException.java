package co.edu.futdata.datasource.external;

import java.util.OptionalInt;

/**
 * Falla de las operaciones nuevas de {@link ISupabaseAuthAdmin} (crear la cuenta, generar y verificar el enlace de
 * crear contraseña, fijar la contraseña y cerrar la sesión). Cada caso tiene su subclase.
 *
 * <p>El mensaje está en español y <b>nunca</b> contiene la llave {@code service_role}, un token, un enlace, una
 * contraseña ni el cuerpo de la respuesta de Supabase, así que se puede registrar en logs.
 */
public abstract class AuthException extends RuntimeException {

    private final Integer codigoHttp;

    protected AuthException(String mensaje, Integer codigoHttp) {
        // Sin la causa: el mensaje de la excepción del cliente HTTP puede traer el cuerpo de la respuesta
        super(mensaje);
        this.codigoHttp = codigoHttp;
    }

    /** Código HTTP que respondió Supabase Auth; vacío si no hubo respuesta o no se llegó a llamar. */
    public OptionalInt getCodigoHttp() {
        return codigoHttp == null ? OptionalInt.empty() : OptionalInt.of(codigoHttp);
    }
}
