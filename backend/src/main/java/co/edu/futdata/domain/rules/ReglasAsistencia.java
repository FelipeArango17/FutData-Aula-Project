package co.edu.futdata.domain.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de la asistencia a un entrenamiento (bloque 4, versión mínima): tres estados, observaciones de hasta 300
 * caracteres y nunca una sesión futura. Puras: no leen el reloj ni la base de datos; quien las llama pasa {@code hoy} (el
 * {@code Clock} de Bogotá) y los datos ya leídos. Los mensajes son los de {@code DatosInvalidosException} (qué debe
 * cumplir el campo) o frases completas (409 y 403).
 */
public final class ReglasAsistencia {

    public static final String PRESENTE = "PRESENTE";
    public static final String AUSENTE = "AUSENTE";
    public static final String JUSTIFICADO = "JUSTIFICADO";
    /** Los tres estados de {@code asistencia_estado_ck}. */
    public static final List<String> ESTADOS = List.of(PRESENTE, AUSENTE, JUSTIFICADO);

    /** Largo máximo de las observaciones de un deportista en una sesión. */
    public static final int MAX_OBSERVACIONES = 300;

    public static final String FECHA_FUTURA = "no puede ser una fecha futura (hoy sí es válido)";
    public static final String SESION_A_ELEGIR = "indica exactamente uno: idFranja o idExcepcion";
    public static final String FECHA_NO_ES_DEL_EXTRA = "no es la fecha de ese entrenamiento extra";
    public static final String SIN_REGISTROS = "debe traer al menos un registro";
    public static final String ESTADO_INVALIDO = "el estado debe ser PRESENTE, AUSENTE o JUSTIFICADO";
    public static final String OBSERVACIONES_LARGAS = "las observaciones pueden tener como máximo " + MAX_OBSERVACIONES
            + " caracteres";
    public static final String DEPORTISTA_REPETIDO = "un deportista no puede aparecer dos veces";
    public static final String DEPORTISTA_FUERA_DE_LISTA = "hay un deportista que no está en la lista de esta sesión";
    public static final String SESION_CANCELADA = "Esa sesión está cancelada ese día: no se puede pasar lista";
    public static final String SOLAPE = "Esta sesión se cruza con otro entrenamiento ya registrado (de la misma categoría "
            + "o del mismo entrenador): no se puede pasar lista";
    public static final String SIN_PERMISO = "Solo puedes pasar lista de tus categorías";

    private ReglasAsistencia() {
    }

    /** {@code true} si {@code fecha} es posterior a {@code hoy}. Hoy no es futura. */
    public static boolean esFechaFutura(LocalDate fecha, LocalDate hoy) {
        return fecha.isAfter(hoy);
    }

    /** El estado en mayúsculas y sin espacios en los extremos; vacío si no es uno de {@link #ESTADOS}. */
    public static Optional<String> estado(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String limpio = texto.strip().toUpperCase(java.util.Locale.ROOT);
        return ESTADOS.contains(limpio) ? Optional.of(limpio) : Optional.empty();
    }

    /**
     * Las observaciones sin espacios de más (igual que el motivo del horario): se quitan los de los extremos, las
     * secuencias de espacios o saltos de línea quedan en uno solo y, si no queda nada, {@code null}.
     */
    public static String normalizarObservaciones(String observaciones) {
        return ReglasExcepcion.normalizarMotivo(observaciones);
    }

    /** {@code true} si las observaciones ya normalizadas pasan de {@link #MAX_OBSERVACIONES}. */
    public static boolean observacionesDemasiadoLargas(String normalizadas) {
        return normalizadas != null && normalizadas.length() > MAX_OBSERVACIONES;
    }
}
