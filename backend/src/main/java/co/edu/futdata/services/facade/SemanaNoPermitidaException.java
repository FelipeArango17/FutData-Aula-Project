package co.edu.futdata.services.facade;

/** El rol no puede consultar esa semana (el acudiente solo ve la semana en curso). El manejo global la responde con {@code 403}. */
public class SemanaNoPermitidaException extends RuntimeException {

    public static final String MENSAJE = "Solo puedes consultar la semana en curso";

    public SemanaNoPermitidaException() {
        super(MENSAJE);
    }
}
