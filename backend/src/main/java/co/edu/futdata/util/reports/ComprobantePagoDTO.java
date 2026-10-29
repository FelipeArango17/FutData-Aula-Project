package co.edu.futdata.util.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos que se imprimen en el comprobante de un pago. No depende de la base de datos: el servicio de pagos
 * lo arma con lo que ya consultó.
 *
 * <p>Solo lleva lo que puede ver el acudiente. A propósito <b>no</b> tiene quién registró el pago, la nota
 * interna, la referencia manual, el documento de identidad del deportista (es menor de edad) ni datos de
 * tarjeta o cuenta bancaria.
 *
 * @param numeroComprobante número único que asigna quien llama. Formato sugerido: {@code FD-AAAA-NNNNNN}
 *                          (año del pago y consecutivo de 6 dígitos), por ejemplo {@code FD-2026-000123}
 * @param fechaEmision      momento en que se genera el comprobante (se imprime en hora de Colombia)
 * @param nombreDeportista  nombre y apellido del deportista
 * @param categoria         categoría del deportista, por ejemplo {@code Sub-12}; opcional
 * @param nombreAcudiente   nombre y apellido del acudiente
 * @param concepto          concepto del pago, por ejemplo {@code Mensualidad octubre}
 * @param monto             pesos colombianos, entero y sin decimales (como el frontend)
 * @param fechaPago         día del pago; no puede ser posterior a hoy (hora de Colombia)
 * @param metodo            medio de pago
 * @param referenciaWompi   referencia de la transacción en Wompi; opcional y solo para pagos en línea
 */
public record ComprobantePagoDTO(
        String numeroComprobante,
        Instant fechaEmision,
        String nombreDeportista,
        String categoria,
        String nombreAcudiente,
        String concepto,
        long monto,
        LocalDate fechaPago,
        MetodoPago metodo,
        String referenciaWompi) {

    public static final int MAX_NUMERO = 40;
    public static final int MAX_NOMBRE = 120;
    public static final int MAX_CONCEPTO = 200;
    public static final int MAX_REFERENCIA = 100;

    /** Medios de pago. Los manuales los registra el club; los en línea llegan por Wompi. */
    public enum MetodoPago {
        EFECTIVO("Efectivo", false),
        TRANSFERENCIA("Transferencia", false),
        PSE("PSE", true),
        NEQUI("Nequi", true),
        TARJETA("Tarjeta", true);

        private final String etiqueta;
        private final boolean enLinea;

        MetodoPago(String etiqueta, boolean enLinea) {
            this.etiqueta = etiqueta;
            this.enLinea = enLinea;
        }

        /** Como se muestra y como lo usa el frontend: "Efectivo", "PSE"... */
        public String etiqueta() {
            return etiqueta;
        }

        /** {@code true} si el pago se hace por Wompi y puede tener referencia. */
        public boolean enLinea() {
            return enLinea;
        }

        /** El método con esa etiqueta, sin distinguir mayúsculas ("Nequi", "nequi"). */
        public static MetodoPago desdeEtiqueta(String etiqueta) {
            for (MetodoPago metodo : values()) {
                if (metodo.etiqueta.equalsIgnoreCase(etiqueta == null ? "" : etiqueta.strip())) {
                    return metodo;
                }
            }
            throw new ComprobanteInvalidoException(List.of("metodo: debe ser Efectivo, Transferencia, PSE, Nequi o Tarjeta"));
        }
    }

    /**
     * Revisa los datos antes de generar el PDF.
     *
     * @param hoy fecha de hoy en Colombia, para rechazar pagos con fecha futura
     * @throws ComprobanteInvalidoException con todos los errores encontrados
     */
    public void validar(LocalDate hoy) {
        List<String> errores = new ArrayList<>();
        texto(errores, "numeroComprobante", numeroComprobante, true, MAX_NUMERO);
        texto(errores, "nombreDeportista", nombreDeportista, true, MAX_NOMBRE);
        texto(errores, "categoria", categoria, false, MAX_NOMBRE);
        texto(errores, "nombreAcudiente", nombreAcudiente, true, MAX_NOMBRE);
        texto(errores, "concepto", concepto, true, MAX_CONCEPTO);
        texto(errores, "referenciaWompi", referenciaWompi, false, MAX_REFERENCIA);
        if (fechaEmision == null) {
            errores.add("fechaEmision: es obligatoria");
        }
        if (monto <= 0) {
            errores.add("monto: debe ser mayor que 0");
        } else if (monto > NumeroEnLetras.MAXIMO) {
            errores.add("monto: es demasiado grande");
        }
        if (fechaPago == null) {
            errores.add("fechaPago: es obligatoria");
        } else if (fechaPago.isAfter(hoy)) {
            errores.add("fechaPago: no puede ser posterior a hoy");
        }
        if (metodo == null) {
            errores.add("metodo: es obligatorio");
        } else if (!metodo.enLinea() && tieneTexto(referenciaWompi)) {
            errores.add("referenciaWompi: solo aplica a pagos en línea (PSE, Nequi o Tarjeta)");
        }
        if (!errores.isEmpty()) {
            throw new ComprobanteInvalidoException(errores);
        }
    }

    static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static void texto(List<String> errores, String campo, String valor, boolean obligatorio, int maximo) {
        if (!tieneTexto(valor)) {
            if (obligatorio) {
                errores.add(campo + ": es obligatorio");
            }
        } else if (valor.strip().length() > maximo) {
            errores.add(campo + ": debe tener máximo " + maximo + " caracteres");
        }
    }
}
