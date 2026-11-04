package co.edu.futdata.domain.rules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Semana del club (lunes a sábado) del horario de entrenamientos (bloque 4B). Reglas puras, sin reloj: quien las
 * llama decide qué es "hoy" (el {@code Clock} de Bogotá).
 */
public final class ReglasSemana {

    /** Días de la semana de entrenamiento: 1 = lunes ... 6 = sábado (el club no entrena los domingos). */
    public static final int DIAS = 6;

    private ReglasSemana() {
    }

    /**
     * El lunes de la semana de {@code fecha}. El domingo no tiene entrenamientos: ese día la semana que cuenta es la
     * que empieza mañana, así que devuelve el lunes siguiente (igual que {@code semanaEnCurso} del frontend).
     */
    public static LocalDate lunesDeLaSemana(LocalDate fecha) {
        if (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return fecha.plusDays(1);
        }
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** El sábado de la semana que empieza en {@code lunes}. */
    public static LocalDate sabado(LocalDate lunes) {
        return lunes.plusDays(DIAS - 1L);
    }

    /** La fecha en que cae una franja de ese {@code dia} (1..6) en la semana que empieza en {@code lunes}. */
    public static LocalDate fechaDeLaFranja(LocalDate lunes, int dia) {
        return lunes.plusDays(dia - 1L);
    }

    /** Fecha en formato {@code AAAA-MM-DD} estricto; vacío si el texto no es una fecha real. */
    public static Optional<LocalDate> fecha(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(texto));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
