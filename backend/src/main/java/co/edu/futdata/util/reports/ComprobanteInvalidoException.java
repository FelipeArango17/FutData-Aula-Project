package co.edu.futdata.util.reports;

import java.util.List;

/**
 * Los datos del comprobante no son válidos (falta un campo, el monto no es positivo, la fecha de pago es
 * futura...). El manejo global de errores la convierte en {@code 400}.
 *
 * <p>Los mensajes nombran el campo y la regla, nunca el valor: no se copian nombres ni montos.
 */
public class ComprobanteInvalidoException extends RuntimeException {

    private final List<String> errores;

    public ComprobanteInvalidoException(List<String> errores) {
        super("Los datos del comprobante no son válidos: " + String.join("; ", errores.stream().sorted().toList()));
        this.errores = errores.stream().sorted().toList();
    }

    /** Un error por campo, con la forma {@code campo: mensaje}, ordenados. */
    public List<String> getErrores() {
        return errores;
    }
}
