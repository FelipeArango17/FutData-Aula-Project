package co.edu.futdata.services.facade;

/**
 * La fachada no encontró el recurso pedido (por ejemplo, un entrenamiento con ese id). El manejo
 * global de errores la convierte en {@code 404} con este mensaje, así que debe estar en español y
 * servir para mostrarlo al usuario.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
