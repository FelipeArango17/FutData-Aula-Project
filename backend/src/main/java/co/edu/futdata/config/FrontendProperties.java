package co.edu.futdata.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dirección pública del frontend ({@code futdata.frontend.url}, variable {@code FRONTEND_URL}). Sirve para armar el
 * enlace que llega por correo para crear la contraseña: {@code {FRONTEND_URL}/crear-contrasena?token=...}.
 *
 * <p>No se valida al arrancar para que la aplicación funcione sin ella; si falta, {@link #configurada()} es
 * {@code false} y quien arma el enlace decide qué hacer (el alta queda con {@code correoEnviado=false}).
 *
 * @param url URL del frontend, sin barra final (si la trae, se quita). En local, {@code http://localhost:5173}
 */
@ConfigurationProperties(prefix = "futdata.frontend")
public record FrontendProperties(String url) {

    static final String RUTA_CREAR_CONTRASENA = "/crear-contrasena";
    static final String RUTA_PAGOS_ACUDIENTE = "/acudiente/pagos";

    public FrontendProperties {
        url = url == null || url.isBlank() ? null : url.strip().replaceAll("/+$", "");
    }

    public boolean configurada() {
        return url != null;
    }

    /**
     * Enlace de la pantalla de crear contraseña con el token ({@code hashed_token} de Supabase). <b>El resultado es
     * secreto</b>: no se registra en logs ni se devuelve en una respuesta de la API.
     *
     * @throws IllegalStateException si {@code FRONTEND_URL} no está configurada
     */
    public String enlaceCrearContrasena(String token) {
        if (!configurada()) {
            throw new IllegalStateException("FRONTEND_URL no está configurada");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token es obligatorio");
        }
        return url + RUTA_CREAR_CONTRASENA + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /**
     * Enlace a la pantalla de pagos del acudiente ({@code {FRONTEND_URL}/acudiente/pagos}) para los recordatorios de pago.
     * No lleva datos secretos.
     *
     * @throws IllegalStateException si {@code FRONTEND_URL} no está configurada
     */
    public String enlacePagos() {
        if (!configurada()) {
            throw new IllegalStateException("FRONTEND_URL no está configurada");
        }
        return url + RUTA_PAGOS_ACUDIENTE;
    }
}
