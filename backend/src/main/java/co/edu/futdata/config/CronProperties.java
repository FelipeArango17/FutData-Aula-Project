package co.edu.futdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credencial de las tareas programadas que llaman al backend desde fuera (hoy, n8n -> {@code POST /api/pagos/generar-ciclos}).
 * Va en la cabecera {@code X-Cron-Token}.
 *
 * <p><b>Opcional:</b> si {@code N8N_CRON_TOKEN} no está definida, la vía por token queda <b>deshabilitada</b> (solo entra el
 * administrador con su JWT). No la exige {@link VerificadorVariablesProd}.
 *
 * @param token secreto compartido con n8n ({@code N8N_CRON_TOKEN}); recomendado: {@code openssl rand -hex 32}
 */
@ConfigurationProperties(prefix = "futdata.cron")
public record CronProperties(String token) {

    public CronProperties {
        token = token == null || token.isBlank() ? null : token.strip();
    }

    public boolean habilitado() {
        return token != null;
    }

    /** No muestra el token: estas propiedades pueden terminar en un log. */
    @Override
    public String toString() {
        return "CronProperties[token=" + (habilitado() ? "****" : "(vacío)") + "]";
    }
}
