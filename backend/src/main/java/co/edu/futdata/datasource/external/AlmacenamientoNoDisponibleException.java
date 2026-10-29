package co.edu.futdata.datasource.external;

/**
 * Storage no respondió bien: {@code 5xx}, tiempo de espera agotado, error de red o respuesta inesperada.
 * Se puede intentar más tarde.
 */
public class AlmacenamientoNoDisponibleException extends AlmacenamientoException {

    public AlmacenamientoNoDisponibleException(String mensaje, Integer codigoHttp, Throwable causa) {
        super(mensaje, codigoHttp, causa);
    }
}
