package co.edu.futdata.services.facade;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Los datos que recibió la fachada no cumplen las reglas del negocio. El manejo global de errores la
 * convierte en {@code 400}, con el mensaje {@code campo: mensaje} (separados por {@code "; "} y ordenados,
 * como los errores de Bean Validation) y la lista de {@link #getCampos() campos} con error.
 *
 * <p>Los mensajes nombran el campo y la regla, nunca el valor recibido.
 */
public class DatosInvalidosException extends RuntimeException {

    private final List<String> campos;

    /** @param errores un mensaje por campo, por ejemplo {@code "documento" -> "debe tener entre 6 y 12 dígitos"} */
    public DatosInvalidosException(Map<String, String> errores) {
        super(formatear(errores));
        this.campos = List.copyOf(new TreeMap<>(errores).keySet());
    }

    public static DatosInvalidosException de(String campo, String mensaje) {
        return new DatosInvalidosException(Map.of(campo, mensaje));
    }

    /** Campos con error, ordenados. */
    public List<String> getCampos() {
        return campos;
    }

    private static String formatear(Map<String, String> errores) {
        if (errores == null || errores.isEmpty()) {
            throw new IllegalArgumentException("Debe haber al menos un error");
        }
        return new TreeMap<>(errores).entrySet().stream()
                .map(error -> error.getKey() + ": " + error.getValue())
                .collect(Collectors.joining("; "));
    }
}
