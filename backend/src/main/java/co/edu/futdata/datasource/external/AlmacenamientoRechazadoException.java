package co.edu.futdata.datasource.external;

/**
 * Storage rechazó la petición ({@code 400}, {@code 413} u otro {@code 4xx}). Reintentar igual no sirve.
 */
public class AlmacenamientoRechazadoException extends AlmacenamientoException {

    public AlmacenamientoRechazadoException(String mensaje, Integer codigoHttp, Throwable causa) {
        super(mensaje, codigoHttp, causa);
    }
}
