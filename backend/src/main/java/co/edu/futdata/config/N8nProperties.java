package co.edu.futdata.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
 * @param timeoutEnlace el mismo tiempo, solo para {@code ENLACE_CONTRASENA} ({@code N8N_TIMEOUT_ENLACE}, 15 s por
 *                     defecto). Ese evento es síncrono: n8n responde después de entregar el correo al SMTP (~3 s, a
 *                     veces más). Con 5 s, un tiempo agotado con el correo ya enviado haría pensar que no salió, y
 *                     el reenvío generaría otro enlace que invalida el que sí llegó
 */
@Validated
@ConfigurationProperties(prefix = "futdata.n8n")
public record N8nProperties(
        String baseUrl,
        String webhookToken,
        @NotNull @DefaultValue("5s") Duration timeout,
        @NotNull @DefaultValue("15s") Duration timeoutEnlace) {

    public static final Duration TIMEOUT_ENLACE_POR_DEFECTO = Duration.ofSeconds(15);

    /** El que usa Spring para leer {@code futdata.n8n.*} (hay otro constructor, para las pruebas). */
    @ConstructorBinding
    public N8nProperties {
    }

    /** Con el tiempo de espera del enlace por defecto (15 s). */
    public N8nProperties(String baseUrl, String webhookToken, Duration timeout) {
        this(baseUrl, webhookToken, timeout, TIMEOUT_ENLACE_POR_DEFECTO);
    }

    /** No muestra el token: estas propiedades pueden terminar en un log. */
    @Override
    public String toString() {
        boolean conToken = webhookToken != null && !webhookToken.isBlank();
        return "N8nProperties[baseUrl=" + baseUrl + ", webhookToken=" + (conToken ? "****" : "(vacío)")
                + ", timeout=" + timeout + ", timeoutEnlace=" + timeoutEnlace + "]";
    }
}
