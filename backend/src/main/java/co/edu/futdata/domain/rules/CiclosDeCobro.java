package co.edu.futdata.domain.rules;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Ciclos de cobro "como un gimnasio": el ancla es la fecha de registro del deportista y cada mes se cobra el mismo día,
 * acotado al último día del mes. Funciones puras (sin reloj ni base de datos): reciben "hoy" por parámetro.
 *
 * <p>La fecha n-ésima se calcula siempre desde el ancla, nunca desde la anterior, para que no haya corrimiento:
 * registro el 31 de octubre -> 31 oct, 30 nov, 31 dic, 31 ene, 28 feb (29 en bisiesto).
 */
public final class CiclosDeCobro {

    private CiclosDeCobro() {
    }

    /**
     * Un ciclo de cobro.
     *
     * @param numero      0 es el del día del registro
     * @param fechaCobro  día en que toca cobrar
     * @param periodo     primer día del mes de {@code fechaCobro} (el periodo de la mensualidad)
     */
    public record Ciclo(int numero, LocalDate fechaCobro, LocalDate periodo) {
    }

    /** Fecha de cobro del ciclo {@code n} (0 = la fecha de registro). */
    public static LocalDate fechaDeCobro(LocalDate ancla, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("El ciclo no puede ser negativo");
        }
        if (n == 0) {
            return ancla;
        }
        YearMonth mes = YearMonth.from(ancla).plusMonths(n);
        return mes.atDay(Math.min(ancla.getDayOfMonth(), mes.lengthOfMonth()));
    }

    /**
     * Ciclo vigente: el de la fecha de cobro más reciente que sea menor o igual a {@code hoy + diasAnticipacion}. Nunca devuelve
     * ciclos anteriores a ese (no hay cobros retroactivos). Vacío si el ancla es posterior a ese límite (registro a futuro).
     */
    public static Optional<Ciclo> cicloVigente(LocalDate ancla, LocalDate hoy, int diasAnticipacion) {
        LocalDate limite = hoy.plusDays(diasAnticipacion);
        if (ancla.isAfter(limite)) {
            return Optional.empty();
        }
        int n = (int) ChronoUnit.MONTHS.between(YearMonth.from(ancla).atDay(1), YearMonth.from(limite).atDay(1));
        if (fechaDeCobro(ancla, n).isAfter(limite)) {
            n--;
        }
        LocalDate fecha = fechaDeCobro(ancla, n);
        return Optional.of(new Ciclo(n, fecha, fecha.withDayOfMonth(1)));
    }

    /** Vence en la fecha de cobro; si el generador corrió tarde y ya pasó, vence hoy. */
    public static LocalDate vencimiento(LocalDate fechaCobro, LocalDate hoy) {
        return fechaCobro.isBefore(hoy) ? hoy : fechaCobro;
    }

    /** Ingreso nuevo: registrado hace {@code ventanaDias} días o menos (inclusive). */
    public static boolean esIngresoReciente(LocalDate fechaRegistro, LocalDate hoy, int ventanaDias) {
        return !fechaRegistro.isBefore(hoy.minusDays(ventanaDias));
    }
}
