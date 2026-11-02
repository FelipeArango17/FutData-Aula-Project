package co.edu.futdata.domain.rules;

import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Reglas de negocio de los pagos: referencias, montos y el mapeo de los medios de pago de Wompi. */
public final class ReglasPago {

    public static final String PREFIJO_REFERENCIA = "FD-";
    public static final String MONEDA = "COP";
    public static final long MONTO_MAXIMO = 999_999_999L;
    public static final int MAX_CONCEPTO = 250;
    /** Wompi: solo alfanuméricos, guion y guion bajo. */
    private static final Pattern REFERENCIA_VALIDA = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    /** {@code payment_method_type} de Wompi -> método del club. Cualquier otro cae en {@link MetodoPago#WOMPI_OTRO}. */
    private static final Map<String, MetodoPago> METODOS_WOMPI = Map.of(
            "CARD", MetodoPago.TARJETA,
            "NEQUI", MetodoPago.NEQUI,
            "PSE", MetodoPago.PSE,
            "BANCOLOMBIA_QR", MetodoPago.QR,
            "BANCOLOMBIA_TRANSFER", MetodoPago.BOTON_BANCOLOMBIA);

    private ReglasPago() {
    }

    public static MetodoPago metodoDesdeWompi(String paymentMethodType) {
        return METODOS_WOMPI.getOrDefault(paymentMethodType == null ? "" : paymentMethodType.strip().toUpperCase(), MetodoPago.WOMPI_OTRO);
    }

    /** {@code FD-<id del pago>}: única por cobro, con solo caracteres que Wompi admite. */
    public static String referenciaDe(UUID idPago) {
        String referencia = PREFIJO_REFERENCIA + idPago;
        if (!REFERENCIA_VALIDA.matcher(referencia).matches()) {
            throw new IllegalStateException("La referencia de Wompi no es válida");
        }
        return referencia;
    }

    public static long aCentavos(long pesos) {
        return Math.multiplyExact(pesos, 100L);
    }

    /** Primer día del mes de la fecha. */
    public static LocalDate primerDiaDelMes(LocalDate fecha) {
        return fecha.withDayOfMonth(1);
    }

    /** Vencimiento de la mensualidad de {@code periodo}: el día pedido, o el último del mes si es más corto. */
    public static LocalDate vencimientoMensualidad(LocalDate periodo, int diaVencimiento) {
        YearMonth mes = YearMonth.from(periodo);
        return mes.atDay(Math.min(Math.max(diaVencimiento, 1), mes.lengthOfMonth()));
    }

    /** {@code Mensualidad octubre 2026}, en español. */
    public static String conceptoMensualidad(LocalDate periodo) {
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
                "noviembre", "diciembre"};
        return "Mensualidad " + meses[periodo.getMonthValue() - 1] + " " + periodo.getYear();
    }
}
