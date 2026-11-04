package co.edu.futdata.domain.rules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Reglas de las excepciones por fecha del horario de la semana (bloque 4B-2: cancelaciones y extras). Puras: no leen
 * el reloj ni la base de datos; quien las llama pasa {@code hoy} (el {@code Clock} de Bogotá) y los datos ya leídos.
 */
public final class ReglasExcepcion {

    /** Largo máximo del motivo (columna {@code excepcion_horario.motivo}). */
    public static final int MAX_MOTIVO = 200;

    // --- Mensajes en español: los campos de un 400 dicen qué debe cumplir, los 409 y 403 son frases completas ---

    public static final String FECHA_PASADA = "no puede ser una fecha pasada (hoy sí es válido)";
    public static final String DIA_NO_COINCIDE = "el día de la semana de la fecha no coincide con el de la franja";
    public static final String FECHA_FUERA_DE_SEMANA = "debe ser de lunes a sábado (el club no entrena los domingos)";
    public static final String MOTIVO_LARGO = "puede tener como máximo " + MAX_MOTIVO + " caracteres";
    /** Mismo texto que usa el horario del club ({@code /api/horario-club}) cuando dos franjas se cruzan. */
    public static final String CRUCE = "Ese entrenador ya tiene otra franja que se cruza con este horario";
    public static final String YA_CANCELADA = "Esa franja ya está cancelada en esa fecha";
    public static final String SIN_PERMISO = "Solo puedes modificar el horario de tus categorías";

    private ReglasExcepcion() {
    }

    /** {@code true} si {@code fecha} es anterior a {@code hoy}. Hoy no es pasada. */
    public static boolean esFechaPasada(LocalDate fecha, LocalDate hoy) {
        return fecha.isBefore(hoy);
    }

    /** {@code true} si {@code fecha} cae en ese día de la franja (1 = lunes ... 6 = sábado). */
    public static boolean coincideConElDia(LocalDate fecha, int diaDeLaFranja) {
        return fecha.getDayOfWeek().getValue() == diaDeLaFranja;
    }

    /** {@code true} de lunes a sábado: el club no entrena los domingos. */
    public static boolean esDiaDeEntrenamiento(LocalDate fecha) {
        return fecha.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * El motivo sin espacios de más: se quitan los de los extremos, las secuencias de espacios o saltos de línea
     * quedan en uno solo y, si no queda nada, {@code null} (el motivo es opcional).
     */
    public static String normalizarMotivo(String motivo) {
        if (motivo == null) {
            return null;
        }
        // \p{Z} incluye el espacio de no separación ( ), que \s no cubre
        String limpio = motivo.replaceAll("[\\s\\p{Z}]+", " ").strip();
        return limpio.isEmpty() ? null : limpio;
    }

    /** {@code true} si el motivo ya normalizado pasa de {@link #MAX_MOTIVO} caracteres. */
    public static boolean motivoDemasiadoLargo(String motivoNormalizado) {
        return motivoNormalizado != null && motivoNormalizado.length() > MAX_MOTIVO;
    }

    /**
     * {@code true} si los intervalos {@code [inicio, fin)} se solapan, con el mismo criterio de
     * {@code tsrange(...) &&} de la base de datos: tocarse en un extremo (uno termina a la hora en que empieza el otro)
     * no es cruzarse.
     */
    public static boolean seCruzan(LocalTime inicioA, LocalTime finA, LocalTime inicioB, LocalTime finB) {
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }
}
