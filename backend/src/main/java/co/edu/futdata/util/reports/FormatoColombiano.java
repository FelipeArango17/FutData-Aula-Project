package co.edu.futdata.util.reports;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos colombianos para los PDF: pesos ({@code $ 90.000}), fechas ({@code 28 de octubre de 2026}, hora de
 * Colombia), NIT ({@code NIT 811.032.309-1}) y celular ({@code 313 731 7366}).
 *
 * <p>No depende de los datos regionales de la JVM: el resultado es igual en Windows, Linux y Docker.
 */
public final class FormatoColombiano {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm", Locale.ROOT);

    private FormatoColombiano() {
    }

    /** {@code 90000} es {@code "$ 90.000"}. Sin decimales, como el frontend. */
    public static String pesos(long monto) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setGroupingSeparator('.');
        simbolos.setMinusSign('-');
        return "$ " + new DecimalFormat("#,##0", simbolos).format(monto);
    }

    /** {@code 2026-10-28} es {@code "28 de octubre de 2026"}. */
    public static String fecha(LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    /** Fecha y hora en Colombia: {@code "28 de octubre de 2026, 4:35 p. m."}. */
    public static String fechaHora(Instant instante) {
        ZonedDateTime local = instante.atZone(ZONA);
        String meridiano = local.getHour() < 12 ? "a. m." : "p. m.";
        return fecha(local.toLocalDate()) + ", " + HORA.format(local) + " " + meridiano;
    }

    /**
     * {@code "811032309-1"} es {@code "NIT 811.032.309-1"}. Acepta el NIT con o sin puntos y espacios; si no
     * tiene la forma {@code dígitos[-dígito]}, lo deja como viene.
     */
    public static String nit(String nit) {
        String limpio = nit.strip().replaceAll("[.\\s]", "");
        if (!limpio.matches("\\d+(-\\d)?")) {
            return "NIT " + nit.strip();
        }
        String[] partes = limpio.split("-");
        String cuerpo = agruparDeATres(partes[0]);
        return "NIT " + cuerpo + (partes.length > 1 ? "-" + partes[1] : "");
    }

    /** Un celular de 10 dígitos ({@code 3137317366}) es {@code "313 731 7366"}; otro número queda como viene. */
    public static String telefono(String telefono) {
        String digitos = telefono.replaceAll("[\\s-]", "");
        if (!digitos.matches("\\d{10}")) {
            return telefono.strip();
        }
        return digitos.substring(0, 3) + " " + digitos.substring(3, 6) + " " + digitos.substring(6);
    }

    private static String agruparDeATres(String digitos) {
        StringBuilder texto = new StringBuilder();
        int primero = digitos.length() % 3 == 0 ? 3 : digitos.length() % 3;
        texto.append(digitos, 0, primero);
        for (int i = primero; i < digitos.length(); i += 3) {
            texto.append('.').append(digitos, i, i + 3);
        }
        return texto.toString();
    }
}
