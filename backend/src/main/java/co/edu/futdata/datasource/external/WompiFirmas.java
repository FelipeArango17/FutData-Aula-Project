package co.edu.futdata.datasource.external;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Las dos firmas de Wompi (SHA-256 en hexadecimal). Funciones puras, sin red ni estado:
 * <ul>
 *   <li><b>Integridad del checkout</b> (se calcula en el servidor): {@code <Referencia><MontoEnCentavos><Moneda><SecretoIntegridad>};
 *       con expiración, {@code <Referencia><Monto><Moneda><FechaExpiracion><SecretoIntegridad>}.</li>
 *   <li><b>Checksum del evento</b> (webhook): los valores de las propiedades de {@code signature.properties} (rutas dentro de
 *       {@code data}, en ese orden) + {@code timestamp} + secreto de eventos.</li>
 * </ul>
 */
public final class WompiFirmas {

    private WompiFirmas() {
    }

    public static String firmaIntegridad(String referencia, long montoEnCentavos, String moneda, String secreto) {
        return sha256Hex(referencia + montoEnCentavos + moneda + secreto);
    }

    public static String firmaIntegridad(String referencia, long montoEnCentavos, String moneda, String expiracion, String secreto) {
        return sha256Hex(referencia + montoEnCentavos + moneda + expiracion + secreto);
    }

    /**
     * El checksum que le corresponde al evento, o {@code null} si el evento no trae la lista de propiedades, o si alguna
     * propiedad no existe en {@code data}: un evento así no se puede validar. Siempre lee la lista del evento.
     */
    public static String checksumDelEvento(JsonNode evento, String secretoEventos) {
        JsonNode propiedades = evento.path("signature").path("properties");
        JsonNode marca = marcaDeTiempo(evento);
        if (!propiedades.isArray() || propiedades.isEmpty() || marca == null) {
            return null;
        }
        StringBuilder texto = new StringBuilder();
        for (JsonNode propiedad : propiedades) {
            JsonNode valor = evento.path("data");
            if (!propiedad.isTextual()) {
                return null;
            }
            for (String parte : propiedad.asText().split("\\.")) {
                valor = valor.path(parte);
            }
            if (valor.isMissingNode() || valor.isNull() || valor.isContainerNode()) {
                return null;
            }
            texto.append(valor.asText());
        }
        return sha256Hex(texto.append(marca.asText()).append(secretoEventos).toString());
    }

    /**
     * {@code true} solo si el checksum calculado es igual al encabezado {@code X-Event-Checksum} <b>y</b> a
     * {@code signature.checksum}. Comparación en tiempo constante.
     */
    public static boolean eventoValido(JsonNode evento, String encabezado, String secretoEventos) {
        if (encabezado == null || encabezado.isBlank() || secretoEventos == null || secretoEventos.isBlank()) {
            return false;
        }
        String esperado = checksumDelEvento(evento, secretoEventos);
        String enCuerpo = evento.path("signature").path("checksum").asText("");
        return esperado != null && iguales(esperado, encabezado.strip()) & iguales(esperado, enCuerpo);
    }

    /** El campo {@code timestamp} del evento (donde lo documenta Wompi); si no, {@code signature.timestamp}. */
    private static JsonNode marcaDeTiempo(JsonNode evento) {
        for (JsonNode candidata : new JsonNode[] {evento.path("timestamp"), evento.path("signature").path("timestamp")}) {
            if (candidata.isNumber() || candidata.isTextual()) {
                return candidata;
            }
        }
        return null;
    }

    private static boolean iguales(String esperado, String recibido) {
        return MessageDigest.isEqual(esperado.toLowerCase().getBytes(StandardCharsets.UTF_8),
                recibido.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
