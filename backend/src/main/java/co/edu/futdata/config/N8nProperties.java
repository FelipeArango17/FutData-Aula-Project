package co.edu.futdata.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Conexión con el webhook de notificaciones de n8n ({@code futdata.n8n.*}). La usa
 * {@link co.edu.futdata.datasource.external.N8nClient}; ver
 * {@code docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md}.
 *
 * <p>{@code baseUrl} y {@code webhookToken} no se validan aquí para que la aplicación arranque en el perfil
 * local sin n8n: si faltan, {@code N8nClient.notificar()} lanza {@code NotificadorMalConfiguradoException}.
 * En el perfil {@code prod} las exige {@link VerificadorVariablesProd} antes de arrancar.
 *
 * @param baseUrl      URL de n8n sin barra final ({@code N8N_BASE_URL}); el webhook es
 *                     {@code {baseUrl}/webhook/futdata/notificaciones}; puede estar vacía en el perfil local
 * @param webhookToken secreto compartido que va en la cabecera {@code X-FutData-Token}
 *                     ({@code N8N_WEBHOOK_TOKEN}); puede estar vacío en el perfil local
 * @param timeout      tiempo máximo de conexión y de lectura; el contrato pide 5 s
 */
@Validated
@ConfigurationProperties(prefix = "futdata.n8n")
public record N8nProperties(
        String baseUrl,
        String webhookToken,
        @NotNull @DefaultValue("5s") Duration timeout) {

    /** No muestra el token: estas propiedades pueden terminar en un log. */
    @Override
    public String toString() {
        boolean conToken = webhookToken != null && !webhookToken.isBlank();
        return "N8nProperties[baseUrl=" + baseUrl + ", webhookToken=" + (conToken ? "****" : "(vacío)")
                + ", timeout=" + timeout + "]";
    }
}
