package co.edu.futdata.domain.rules;

import java.util.Map;
import java.util.Optional;

/**
 * Reglas de la foto del deportista: JPG o PNG de hasta 5 MB. El tipo se decide por los <b>bytes iniciales</b> del
 * archivo (su firma), nunca por la extensión ni por el {@code Content-Type} que declare el cliente: un PDF renombrado
 * como {@code .jpg} se rechaza.
 */
public final class ReglasFoto {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    /** 5 MB, igual que el límite del bucket {@code fotos} (migración 007). */
    public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;

    /** JPG: FF D8 FF. */
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** PNG: 89 50 4E 47 0D 0A 1A 0A. */
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ReglasFoto() {
    }

    /** {@code image/jpeg} o {@code image/png} según la firma de los bytes; vacío si no es ninguno. */
    public static Optional<String> tipoPorContenido(byte[] contenido) {
        if (empiezaCon(contenido, FIRMA_JPEG)) return Optional.of(JPEG);
        if (empiezaCon(contenido, FIRMA_PNG)) return Optional.of(PNG);
        return Optional.empty();
    }

    /** Extensión de la ruta en el bucket ({@code .jpg} o {@code .png}) según el tipo detectado. */
    public static String extension(String tipo) {
        return PNG.equals(tipo) ? ".png" : ".jpg";
    }

    /** Error en el campo {@code archivo}, o vacío si la foto sirve. */
    public static Map<String, String> validar(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            return Map.of("archivo", "está vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            return Map.of("archivo", "pesa " + contenido.length + " bytes; el máximo es 5 MB (" + TAMANO_MAXIMO + " bytes)");
        }
        if (tipoPorContenido(contenido).isEmpty()) {
            return Map.of("archivo", "debe ser una imagen JPG o PNG (se revisa el contenido del archivo, no su extensión)");
        }
        return Map.of();
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        if (contenido == null || contenido.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) {
            if (contenido[i] != firma[i]) return false;
        }
        return true;
    }
}
