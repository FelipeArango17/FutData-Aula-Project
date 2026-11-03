package co.edu.futdata.util.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * IP del cliente para los límites de intentos de las rutas públicas.
 *
 * <p><b>Detrás de Render:</b> el proxy de Render <i>agrega</i> direcciones a {@code X-Forwarded-For} sin borrar las
 * que manda el cliente, así que ese encabezado se puede falsificar y <b>no se usa</b> (y
 * {@code server.forward-headers-strategy} queda sin configurar: {@code getRemoteAddr()} es la IP del proxy). Delante
 * de Render está Cloudflare, que escribe {@code CF-Connecting-IP} con la IP que vio y reemplaza la que traiga el
 * cliente; se usa esa si es una IP válida y, si no está (en local), {@code getRemoteAddr()}.
 *
 * <p>Como eso depende de que todo el tráfico pase por Cloudflare (no comprobado en el despliegue), los límites no se
 * apoyan solo en la IP: también hay uno por token y un tope global ({@code ContrasenaServiceImpl}).
 */
public final class IpCliente {

    static final String CABECERA_CLOUDFLARE = "CF-Connecting-IP";
    /** IPv4 o IPv6 (solo los caracteres; no se resuelve nada). */
    private static final Pattern IP = Pattern.compile("[0-9.]{7,15}|[0-9a-fA-F:.]{2,45}");

    private IpCliente() {
    }

    public static String de(HttpServletRequest peticion) {
        String cloudflare = peticion.getHeader(CABECERA_CLOUDFLARE);
        if (cloudflare != null && IP.matcher(cloudflare.strip()).matches()) {
            return cloudflare.strip();
        }
        return peticion.getRemoteAddr();
    }

    /** Para los logs: IPv4 con los dos últimos octetos ocultos ({@code 190.24.x.x}), IPv6 con solo los dos primeros grupos. */
    public static String enmascarada(String ip) {
        if (ip == null || ip.isBlank()) {
            return "(sin IP)";
        }
        if (ip.contains(":")) {
            String[] grupos = ip.split(":");
            return (grupos.length >= 2 ? grupos[0] + ":" + grupos[1] : grupos[0]) + ":x";
        }
        String[] octetos = ip.split("\\.");
        return octetos.length == 4 ? octetos[0] + "." + octetos[1] + ".x.x" : "x";
    }
}
