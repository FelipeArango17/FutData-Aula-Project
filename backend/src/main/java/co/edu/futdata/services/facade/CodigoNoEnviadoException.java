package co.edu.futdata.services.facade;

public class CodigoNoEnviadoException extends RuntimeException {
    public CodigoNoEnviadoException() {
        super("No pudimos enviarte el código. Intenta de nuevo en un momento.");
    }
}
