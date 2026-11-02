package co.edu.futdata.datasource.external;

/** Wompi no respondió o respondió un error. El mensaje no lleva llaves ni datos del pago. */
public class WompiNoDisponibleException extends RuntimeException {

    public WompiNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
