package co.edu.futdata.domain.rules;

import co.edu.futdata.domain.rules.ReglasDeportista.CategoriaClub;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validaciones de una franja del horario fijo del club (tabla {@code franja_horario}, migración 007).
 *
 * <p>Lo que depende de otros registros (que la categoría y el entrenador existan, que el entrenador esté activo y
 * que no se cruce con otra franja suya) lo revisa el servicio o la base de datos, no esta clase. El servicio junta
 * esos errores con los de {@link #validar} para responder un solo {@code 400}.
 */
public final class ReglasFranja {

    /** Las dos sedes del club, como en {@code franja_sede_ck}. */
    public static final List<String> SEDES = List.of("Horizontes", "UVA El Paraíso");
    public static final List<String> GRUPOS = ReglasDeportista.GRUPOS;
    public static final int DIA_MINIMO = 1;
    public static final int DIA_MAXIMO = 6;
    /** Formato de las horas en la API: {@code HH:mm} de 24 horas, con dos dígitos. */
    public static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final Pattern HORA = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    /** Las categorías que admiten grupo (mañana/tarde), en el orden oficial: "Cachorros y Semillero". */
    static final String CATEGORIAS_CON_GRUPO = enumerar(ReglasDeportista.CATEGORIAS.stream()
            .filter(CategoriaClub::admiteGrupo).map(CategoriaClub::nombre).toList());

    /**
     * Los datos de una franja tal como llegan a la API, con las horas todavía como texto.
     *
     * @param idEntrenador {@code null} = "por turnos" (sin entrenador fijo)
     */
    public record Entrada(UUID idCategoria, Integer dia, String horaInicio, String horaFin, String sede,
                          UUID idEntrenador, String grupo) {
    }

    private ReglasFranja() {
    }

    /** Quita espacios; el grupo pasa a minúsculas y, si queda vacío, a {@code null}. */
    public static Entrada limpiar(Entrada e) {
        String grupo = texto(e.grupo());
        return new Entrada(e.idCategoria(), e.dia(), texto(e.horaInicio()), texto(e.horaFin()), texto(e.sede()),
                e.idEntrenador(), grupo == null || grupo.isEmpty() ? null : grupo.toLowerCase(Locale.ROOT));
    }

    /**
     * Errores por campo (vacío si todo está bien). Los mensajes nombran la regla, nunca el valor recibido.
     *
     * @param categoria nombre de la categoría de {@code idCategoria}, o {@code null} si no se encontró (ese error
     *                  lo agrega el servicio); sirve para saber si la franja admite grupo
     */
    public static Map<String, String> validar(Entrada e, String categoria) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (e.idCategoria() == null) {
            errores.put("idCategoria", "es obligatoria");
        }
        if (e.dia() == null) {
            errores.put("dia", "es obligatorio (1 = lunes ... 6 = sábado)");
        } else if (e.dia() < DIA_MINIMO || e.dia() > DIA_MAXIMO) {
            errores.put("dia", "debe estar entre 1 (lunes) y 6 (sábado)");
        }
        Optional<LocalTime> inicio = validarHora(errores, "horaInicio", e.horaInicio());
        Optional<LocalTime> fin = validarHora(errores, "horaFin", e.horaFin());
        if (inicio.isPresent() && fin.isPresent() && !fin.get().isAfter(inicio.get())) {
            errores.put("horaFin", "debe ser posterior a horaInicio");
        }
        if (e.sede() == null || e.sede().isEmpty()) {
            errores.put("sede", "es obligatoria (" + enumerarO(SEDES) + ")");
        } else if (!SEDES.contains(e.sede())) {
            errores.put("sede", "debe ser " + enumerarO(SEDES));
        }
        validarGrupo(errores, e.grupo(), categoria);
        return errores;
    }

    /** La hora en {@code HH:mm}; vacía si falta o no tiene ese formato exacto. */
    public static Optional<LocalTime> hora(String texto) {
        if (texto == null || !HORA.matcher(texto).matches()) return Optional.empty();
        return Optional.of(LocalTime.parse(texto, FORMATO_HORA));
    }

    /** La hora como la devuelve la API: {@code HH:mm}. */
    public static String formatear(LocalTime hora) {
        return hora == null ? null : hora.format(FORMATO_HORA);
    }

    /** {@code true} si es una de las dos {@link #SEDES} (nombre exacto), por ejemplo para un filtro. */
    public static boolean esSede(String sede) {
        return sede != null && SEDES.contains(sede); // List.of(...).contains(null) lanza NullPointerException
    }

    private static Optional<LocalTime> validarHora(Map<String, String> errores, String campo, String valor) {
        if (valor == null || valor.isEmpty()) {
            errores.put(campo, "es obligatoria (formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00)");
            return Optional.empty();
        }
        Optional<LocalTime> hora = hora(valor);
        if (hora.isEmpty()) {
            errores.put(campo, "debe tener el formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00");
        }
        return hora;
    }

    /** Opcional; si viene, mañana o tarde y solo en las categorías que lo admiten (Cachorros y Semillero). */
    private static void validarGrupo(Map<String, String> errores, String grupo, String categoria) {
        if (grupo == null) return;
        if (!GRUPOS.contains(grupo)) {
            errores.put("grupo", "debe ser mañana o tarde");
            return;
        }
        if (categoria == null) return; // la categoría ya tiene su propio error
        boolean admite = ReglasDeportista.categoriaPorNombre(categoria).map(CategoriaClub::admiteGrupo).orElse(false);
        if (!admite) {
            errores.put("grupo", "solo se asigna en " + CATEGORIAS_CON_GRUPO + "; la franja es de " + categoria);
        }
    }

    /** {@code A}, {@code A y B}, {@code A, B y C}. */
    private static String enumerar(List<String> nombres) {
        if (nombres.size() == 1) return nombres.get(0);
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.getLast();
    }

    private static String enumerarO(List<String> nombres) {
        if (nombres.size() == 1) return nombres.get(0);
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " o " + nombres.getLast();
    }

    private static String texto(String valor) {
        return valor == null ? null : valor.trim();
    }
}
