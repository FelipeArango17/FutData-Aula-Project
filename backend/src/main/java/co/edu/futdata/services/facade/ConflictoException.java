package co.edu.futdata.services.facade;

import java.util.List;

/**
 * La petición es válida, pero choca con el estado actual de los datos: un documento o un correo que ya
 * tiene otro registro, o una categoría que no puede quedar sin entrenador. El manejo global de errores la
 * convierte en {@code 409} con este mensaje (en español, para mostrarlo al usuario) y los {@link #getCampos()
 * campos} implicados.
 *
 * <p>El mensaje nunca incluye datos del otro registro (nombre, documento, correo).
 */
public class ConflictoException extends RuntimeException {

    private final List<String> campos;

    public ConflictoException(String mensaje, List<String> campos) {
        super(mensaje);
        this.campos = List.copyOf(campos);
    }

    /** Campos que causan el conflicto, por ejemplo {@code ["documento"]} o {@code ["categorias"]}. */
    public List<String> getCampos() {
        return campos;
    }
}
