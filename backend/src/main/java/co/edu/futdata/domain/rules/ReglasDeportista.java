package co.edu.futdata.domain.rules;

import co.edu.futdata.domain.entities.Deportista;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validaciones de los datos de un deportista y su categoría por año de nacimiento.
 *
 * <p>La categoría <b>se calcula</b> (no se guarda): edad por año = año actual − año de nacimiento, sin importar el
 * día ni el mes. Así un deportista cambia de categoría solo cada 1 de enero. El año actual lo da el
 * {@link java.time.Clock} del servicio, nunca la fecha del sistema.
 */
public final class ReglasDeportista {

    public static final List<String> TIPOS_DOCUMENTO = List.of("TI", "RC", "CE", "PASAPORTE");
    public static final List<String> GRUPOS = List.of("mañana", "tarde");

    /**
     * Los 4 documentos obligatorios de la inscripción. {@code estadoInscripcion} es {@link #INSCRIPCION_COMPLETA}
     * solo si cada uno tiene al menos un documento APROBADO; si no, {@link #INSCRIPCION_PENDIENTE}. Se calcula al
     * leer, sin columna.
     */
    public static final List<String> DOCUMENTOS_OBLIGATORIOS = List.of(
            "DOCUMENTO_IDENTIDAD", "CERTIFICADO_ESTUDIO", "FORMULARIO_INSCRIPCION", "AUTORIZACION_HABEAS_DATA");
    public static final String INSCRIPCION_COMPLETA = "COMPLETA";
    public static final String INSCRIPCION_PENDIENTE = "PENDIENTE";
    public static final int EDAD_MINIMA = 4;
    public static final int EDAD_MAXIMA = 16;
    /** Largo de deportista.posicion (migración 001). */
    public static final int MAX_POSICION = 50;

    /** Una de las 9 categorías del club, con su rango de edad por año de nacimiento (como en la migración 007). */
    public record CategoriaClub(String nombre, int edadMinima, int edadMaxima, boolean admiteGrupo) {
    }

    /** Las 9 categorías del club, de la menor a la mayor. Grupo (mañana/tarde) solo en Cachorros y Semillero. */
    public static final List<CategoriaClub> CATEGORIAS = List.of(
            new CategoriaClub("Párvulos", 4, 6, false),
            new CategoriaClub("Cachorros", 7, 8, true),
            new CategoriaClub("Semillero", 9, 10, true),
            new CategoriaClub("Sub-11", 11, 11, false),
            new CategoriaClub("Sub-12", 12, 12, false),
            new CategoriaClub("Sub-13", 13, 13, false),
            new CategoriaClub("Sub-14", 14, 14, false),
            new CategoriaClub("Sub-15", 15, 15, false),
            new CategoriaClub("Sub-16", 16, 16, false));

    private static final Pattern NOMBRE = Pattern.compile("\\p{L}[\\p{L} '’-]*");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");
    private static final Pattern ALFANUMERICO = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern TELEFONO = Pattern.compile("\\d{10}");

    private ReglasDeportista() {
    }

    /** Edad por año de nacimiento: {@code anioActual - año de nacimiento}. */
    public static int edadPorAnio(LocalDate fechaNacimiento, int anioActual) {
        return anioActual - fechaNacimiento.getYear();
    }

    /** La categoría que le toca por año de nacimiento; vacía si queda fuera de 4 a 16 (al leer no es un error). */
    public static Optional<CategoriaClub> categoriaDe(LocalDate fechaNacimiento, int anioActual) {
        if (fechaNacimiento == null) return Optional.empty();
        int edad = edadPorAnio(fechaNacimiento, anioActual);
        return CATEGORIAS.stream().filter(c -> edad >= c.edadMinima() && edad <= c.edadMaxima()).findFirst();
    }

    /** Nombre de la categoría que le toca por año de nacimiento; vacío si queda fuera de 4 a 16. */
    public static Optional<String> categoriaPorAnioNacimiento(LocalDate fechaNacimiento, int anioActual) {
        return categoriaDe(fechaNacimiento, anioActual).map(CategoriaClub::nombre);
    }

    /** Busca una de las 9 categorías por su nombre exacto (por ejemplo, para un filtro). */
    public static Optional<CategoriaClub> categoriaPorNombre(String nombre) {
        return CATEGORIAS.stream().filter(c -> c.nombre().equals(nombre)).findFirst();
    }

    public static Deportista limpiar(Deportista original) {
        Deportista d = new Deportista();
        d.setNombre(texto(original.getNombre()));
        d.setApellido(texto(original.getApellido()));
        String tipo = texto(original.getTipoDocumento());
        d.setTipoDocumento(tipo == null ? null : tipo.toUpperCase(Locale.ROOT));
        String documento = texto(original.getDocumento());
        d.setDocumento("PASAPORTE".equals(d.getTipoDocumento()) && documento != null
                ? documento.toUpperCase(Locale.ROOT) : documento);
        d.setFechaNacimiento(original.getFechaNacimiento());
        d.setTelefono(texto(original.getTelefono()));
        d.setDireccion(texto(original.getDireccion()));
        String grupo = texto(original.getGrupo());
        d.setGrupo(grupo == null || grupo.isEmpty() ? null : grupo.toLowerCase(Locale.ROOT));
        String posicion = texto(original.getPosicion());
        d.setPosicion(posicion == null || posicion.isEmpty() ? null : posicion);
        d.setActivo(original.isActivo());
        return d;
    }

    /** Validación al crear: exige de 4 a 16 años por año de nacimiento. */
    public static Map<String, String> validar(Deportista d, LocalDate hoy) {
        return validar(d, hoy, true);
    }

    /**
     * Errores por campo al crear o actualizar (vacío si todo está bien).
     *
     * @param hoy         la fecha del reloj del servicio; su año es el que define la categoría
     * @param exigirRango {@code true} al crear o si cambió la fecha de nacimiento: rechaza a quien quede fuera de 4 a
     *                    16 años. {@code false} si la fecha no cambió: así se puede editar o desactivar a quien ya se
     *                    pasó de 16. El grupo se valida siempre contra la categoría de la fecha que queda guardada.
     */
    public static Map<String, String> validar(Deportista d, LocalDate hoy, boolean exigirRango) {
        Map<String, String> errores = new LinkedHashMap<>();
        obligatorio(errores, "nombre", d.getNombre(), "es obligatorio");
        obligatorio(errores, "apellido", d.getApellido(), "es obligatorio");
        if (!TIPOS_DOCUMENTO.contains(d.getTipoDocumento())) {
            errores.put("tipoDocumento", "debe ser TI, RC, CE o PASAPORTE");
        }
        String documento = d.getDocumento();
        if (documento == null || documento.isBlank()) {
            errores.put("documento", "es obligatorio");
        } else if (documento.length() < 6 || documento.length() > 40
                || (!"PASAPORTE".equals(d.getTipoDocumento()) && !SOLO_DIGITOS.matcher(documento).matches())
                || ("PASAPORTE".equals(d.getTipoDocumento()) && !ALFANUMERICO.matcher(documento).matches())) {
            errores.put("documento", "tiene un formato inválido");
        }
        Optional<CategoriaClub> categoria = Optional.empty();
        boolean fechaValida = false;
        if (d.getFechaNacimiento() == null) {
            errores.put("fechaNacimiento", "es obligatoria");
        } else if (d.getFechaNacimiento().isAfter(hoy)) {
            errores.put("fechaNacimiento", "no puede ser futura");
        } else {
            categoria = categoriaDe(d.getFechaNacimiento(), hoy.getYear());
            fechaValida = categoria.isPresent() || !exigirRango;
            if (categoria.isEmpty() && exigirRango) {
                errores.put("fechaNacimiento", "por año de nacimiento tiene "
                        + edadPorAnio(d.getFechaNacimiento(), hoy.getYear()) + " años en " + hoy.getYear()
                        + "; el club solo inscribe de " + EDAD_MINIMA + " a " + EDAD_MAXIMA + " años");
            }
        }
        if (d.getTelefono() == null || !TELEFONO.matcher(d.getTelefono()).matches()) {
            errores.put("telefono", "debe tener exactamente 10 dígitos");
        }
        if (d.getDireccion() == null || d.getDireccion().length() < 5 || d.getDireccion().length() > 250) {
            errores.put("direccion", "debe tener entre 5 y 250 caracteres");
        }
        if (d.getPosicion() != null && d.getPosicion().length() > MAX_POSICION) {
            errores.put("posicion", "debe tener máximo " + MAX_POSICION + " caracteres");
        }
        validarGrupo(errores, d.getGrupo(), categoria, fechaValida);
        return errores;
    }

    /**
     * El grupo es opcional; si viene, debe ser mañana o tarde y la categoría debe admitirlo.
     *
     * @param fechaValida {@code false} si la fecha ya tiene su propio error: no se repite en grupo
     */
    private static void validarGrupo(Map<String, String> errores, String grupo, Optional<CategoriaClub> categoria,
                                     boolean fechaValida) {
        if (grupo == null) return;
        if (!GRUPOS.contains(grupo)) {
            errores.put("grupo", "debe ser mañana o tarde");
        } else if (categoria.isPresent() && !categoria.get().admiteGrupo()) {
            errores.put("grupo", "solo se asigna en Cachorros y Semillero; por año de nacimiento está en "
                    + categoria.get().nombre());
        } else if (categoria.isEmpty() && fechaValida) {
            // Fuera de 4 a 16 con la fecha guardada (edición sin cambiar la fecha): no hay categoría que lo admita
            errores.put("grupo", "solo se asigna en Cachorros y Semillero; por año de nacimiento no tiene categoría");
        }
    }

    private static void obligatorio(Map<String, String> errores, String campo, String valor, String mensaje) {
        if (valor == null || valor.isBlank()) errores.put(campo, mensaje);
    }

    private static String texto(String valor) {
        return valor == null ? null : valor.trim();
    }
}
