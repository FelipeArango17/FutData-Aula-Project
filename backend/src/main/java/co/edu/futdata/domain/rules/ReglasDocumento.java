package co.edu.futdata.domain.rules;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reglas de los documentos de la inscripción (regla 7 y 8 de {@code docs/PRESENTACION-TECNICA.md}): 7 tipos, PDF, JPG
 * o PNG de hasta 5 MB y comentario de rechazo de 1 a 300 caracteres.
 *
 * <p>El tipo de contenido se decide solo por los <b>bytes iniciales</b> del archivo (su firma), nunca por la extensión,
 * el nombre ni el {@code Content-Type} que mande el cliente (se ignora): un PDF renombrado {@code .jpg} se guarda como
 * PDF, un PNG declarado como {@code application/pdf} se guarda como PNG, y un ejecutable renombrado {@code .pdf} se
 * rechaza. Todos los métodos {@code validar...} devuelven un mapa campo → mensaje (vacío si todo está
 * bien), listo para un {@code DatosInvalidosException}.
 */
public final class ReglasDocumento {

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    /** 5 MB, igual que el límite del bucket {@code documentos} (migración 007) y que {@code documento_tamano_ck}. */
    public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;
    /** Largo máximo del comentario de rechazo (el mismo del frontend; la base no lo limita). */
    public static final int COMENTARIO_MAXIMO = 300;
    /** Largo de {@code documento.nombre_original} (migración 004). */
    public static final int NOMBRE_MAXIMO = 255;

    /** Los 7 tipos (migración 007) con su etiqueta legible, la misma que muestra el frontend. */
    private static final Map<String, String> ETIQUETAS = Map.of(
            "DOCUMENTO_IDENTIDAD", "Documento de identidad",
            "CERTIFICADO_ESTUDIO", "Certificado de estudio",
            "FORMULARIO_INSCRIPCION", "Formulario de inscripción",
            "AUTORIZACION_HABEAS_DATA", "Habeas Data",
            "PAZ_Y_SALVO", "Paz y salvo",
            "CARTA_RENUNCIA", "Carta de renuncia",
            "TRASPASO_NACIONAL", "Traspaso nacional");

    /** Los 7 tipos: los 4 obligatorios primero y luego los 3 "si aplica". */
    public static final List<String> TIPOS = List.of(
            "DOCUMENTO_IDENTIDAD", "CERTIFICADO_ESTUDIO", "FORMULARIO_INSCRIPCION", "AUTORIZACION_HABEAS_DATA",
            "PAZ_Y_SALVO", "CARTA_RENUNCIA", "TRASPASO_NACIONAL");

    /** PDF: {@code %PDF-}. */
    private static final byte[] FIRMA_PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};
    /** JPG: FF D8 FF. */
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** PNG: 89 50 4E 47 0D 0A 1A 0A. */
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final String NOMBRE_POR_DEFECTO = "documento";

    private ReglasDocumento() {
    }

    // --- Tipo ---------------------------------------------------------------------------------------------------

    /** Error en el campo {@code tipo}, o vacío si es uno de los 7 (se compara tal cual, en mayúsculas). */
    public static Map<String, String> validarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return Map.of("tipo", "es obligatorio");
        }
        if (!ETIQUETAS.containsKey(tipo)) {
            return Map.of("tipo", "debe ser uno de " + String.join(", ", TIPOS));
        }
        return Map.of();
    }

    /** Etiqueta legible del tipo, por ejemplo {@code Documento de identidad}. */
    public static String etiqueta(String tipo) {
        String etiqueta = tipo == null ? null : ETIQUETAS.get(tipo);
        if (etiqueta == null) {
            throw new IllegalArgumentException("Tipo de documento desconocido");
        }
        return etiqueta;
    }

    // --- Archivo ------------------------------------------------------------------------------------------------

    /** {@code application/pdf}, {@code image/jpeg} o {@code image/png} según la firma de los bytes; vacío si no es ninguno. */
    public static Optional<String> tipoPorContenido(byte[] contenido) {
        if (empiezaCon(contenido, FIRMA_PDF)) return Optional.of(PDF);
        if (empiezaCon(contenido, FIRMA_JPEG)) return Optional.of(JPEG);
        if (empiezaCon(contenido, FIRMA_PNG)) return Optional.of(PNG);
        return Optional.empty();
    }

    /** Extensión de la ruta en el bucket ({@code .pdf}, {@code .jpg} o {@code .png}) según el tipo de contenido detectado. */
    public static String extension(String tipoContenido) {
        return switch (tipoContenido == null ? "" : tipoContenido) {
            case PDF -> ".pdf";
            case PNG -> ".png";
            case JPEG -> ".jpg";
            default -> throw new IllegalArgumentException("Tipo de contenido no permitido");
        };
    }

    /**
     * Ruta del archivo en el bucket: {@code {idDeportista}/{uuid}.{ext}}. Nunca lleva el nombre del cliente.
     *
     * @param tipoContenido el detectado por {@link #tipoPorContenido(byte[])}
     */
    public static String ruta(UUID idDeportista, UUID idArchivo, String tipoContenido) {
        return idDeportista + "/" + idArchivo + extension(tipoContenido);
    }

    /**
     * Error en el campo {@code archivo}, o vacío si sirve: no vacío, de hasta 5 MB y con la firma de un PDF, JPG o PNG.
     * El {@code Content-Type} que mande el cliente se ignora: decide solo la firma (ver {@link #tipoPorContenido}).
     */
    public static Map<String, String> validarArchivo(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            return Map.of("archivo", "está vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            return Map.of("archivo", "pesa " + contenido.length + " bytes; el máximo es 5 MB (" + TAMANO_MAXIMO + " bytes)");
        }
        if (tipoPorContenido(contenido).isEmpty()) {
            return Map.of("archivo", "debe ser un PDF, JPG o PNG (se revisa el contenido del archivo, no su extensión)");
        }
        return Map.of();
    }

    /**
     * El nombre original para guardar en {@code nombre_original}: sin carpetas ({@code /} ni {@code \}), sin caracteres
     * de control, sin espacios en los extremos y de hasta 255 caracteres. Vacío o nulo queda como {@code documento}. Es
     * solo informativo: la ruta en Storage nunca lo usa.
     */
    public static String limpiarNombre(String nombre) {
        if (nombre == null) return NOMBRE_POR_DEFECTO;
        String base = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        String limpio = base.replaceAll("\\p{Cntrl}", "").strip();
        if (limpio.length() > NOMBRE_MAXIMO) {
            limpio = limpio.substring(0, NOMBRE_MAXIMO).strip();
        }
        return limpio.isEmpty() ? NOMBRE_POR_DEFECTO : limpio;
    }

    // --- Fecha de vencimiento -----------------------------------------------------------------------------------

    /**
     * Error en el campo {@code fechaVencimiento}, o vacío si no viene (es opcional) o es una fecha real
     * {@code AAAA-MM-DD} que no es anterior a {@code hoy} (hoy sí vale).
     *
     * @param hoy la fecha de hoy según el reloj de la aplicación (hora de Colombia)
     */
    public static Map<String, String> validarFechaVencimiento(String texto, LocalDate hoy) {
        if (texto == null || texto.isBlank()) {
            return Map.of();
        }
        Optional<LocalDate> fecha = parsearFecha(texto);
        if (fecha.isEmpty()) {
            return Map.of("fechaVencimiento", "debe ser una fecha válida con el formato AAAA-MM-DD");
        }
        if (fecha.get().isBefore(hoy)) {
            return Map.of("fechaVencimiento", "no puede ser una fecha pasada");
        }
        return Map.of();
    }

    /** La fecha {@code AAAA-MM-DD} real, o vacío si falta, está en blanco o no existe en el calendario ({@code 2026-02-30}). */
    public static Optional<LocalDate> parsearFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(texto.strip()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    // --- Comentario de rechazo ----------------------------------------------------------------------------------

    /** El comentario sin espacios en los extremos; nulo queda como cadena vacía. */
    public static String limpiarComentario(String comentario) {
        return comentario == null ? "" : comentario.strip();
    }

    /** Error en el campo {@code comentario}: obligatorio (ya recortado) y de 1 a 300 caracteres. */
    public static Map<String, String> validarComentario(String comentario) {
        String limpio = limpiarComentario(comentario);
        Map<String, String> errores = new LinkedHashMap<>();
        if (limpio.isEmpty()) {
            errores.put("comentario", "es obligatorio al rechazar un documento");
        } else if (limpio.length() > COMENTARIO_MAXIMO) {
            errores.put("comentario", "no puede pasar de " + COMENTARIO_MAXIMO + " caracteres");
        }
        return errores;
    }

    // --- Quién lo subió -----------------------------------------------------------------------------------------

    /** {@code Administrador} o {@code Acudiente} según el rol de quien lo subió; {@code null} para cualquier otro. */
    public static String cargadoPor(String rol) {
        return switch (rol == null ? "" : rol) {
            case "ADMINISTRADOR" -> "Administrador";
            case "ACUDIENTE" -> "Acudiente";
            default -> null;
        };
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        if (contenido == null || contenido.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) {
            if (contenido[i] != firma[i]) return false;
        }
        return true;
    }
}
