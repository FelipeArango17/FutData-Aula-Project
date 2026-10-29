package co.edu.futdata.datasource.external;

import java.util.OptionalInt;

/**
 * Falla de {@link IAlmacenamientoArchivos}. Sirve para atrapar todas las fallas juntas; cada caso tiene su
 * subclase.
 *
 * <p>El mensaje está en español y <b>nunca</b> contiene la llave {@code service_role} ni una URL firmada, así que
 * se puede registrar en logs. Puede contener el bucket y la ruta, que no tienen datos personales.
 */
public abstract class AlmacenamientoException extends RuntimeException {

    private final Integer codigoHttp;

    protected AlmacenamientoException(String mensaje, Integer codigoHttp, Throwable causa) {
        super(mensaje, causa);
        this.codigoHttp = codigoHttp;
    }

    /** Código HTTP que respondió Storage; vacío si no hubo respuesta o no se llegó a llamar. */
    public OptionalInt getCodigoHttp() {
        return codigoHttp == null ? OptionalInt.empty() : OptionalInt.of(codigoHttp);
    }
}
