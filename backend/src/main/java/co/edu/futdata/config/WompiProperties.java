package co.edu.futdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Pagos en línea con Wompi ({@code futdata.wompi.*}). Todas vienen de variables de entorno y <b>nunca</b> se suben al
 * repositorio ni se escriben en logs, respuestas o errores. Sin llaves la aplicación arranca igual: los endpoints de
 * Wompi responden {@code 503 PAGOS_NO_CONFIGURADOS}.
 *
 * @param publicKey       {@code WOMPI_PUBLIC_KEY}: {@code pub_test_...} (sandbox) o {@code pub_prod_...}; va al navegador
 * @param privateKey      {@code WOMPI_PRIVATE_KEY}: {@code prv_test_...} o {@code prv_prod_...}; solo para consultar transacciones
 * @param eventsSecret    {@code WOMPI_EVENTS_SECRET}: {@code test_events_...} o {@code prod_events_...}; valida el webhook
 * @param integritySecret {@code WOMPI_INTEGRITY_SECRET}: {@code test_integrity_...} o {@code prod_integrity_...}; firma el checkout
 * @param baseUrl         {@code WOMPI_BASE_URL}: {@code https://sandbox.wompi.co/v1} (por defecto) o {@code https://production.wompi.co/v1}
 * @param redirectUrl     {@code WOMPI_REDIRECT_URL}: a dónde vuelve el usuario tras pagar; solo informa, el pago se confirma por webhook
 */
@ConfigurationProperties(prefix = "futdata.wompi")
public record WompiProperties(
        String publicKey,
        String privateKey,
        String eventsSecret,
        String integritySecret,
        @DefaultValue("https://sandbox.wompi.co/v1") String baseUrl,
        String redirectUrl) {

    public static final String SANDBOX = "https://sandbox.wompi.co/v1";
    public static final String PRODUCCION = "https://production.wompi.co/v1";

    /** Para el checkout y la conciliación: las cuatro llaves, y cada una del mismo ambiente que {@code baseUrl}. */
    public boolean configurado() {
        return StringUtils.hasText(publicKey) && StringUtils.hasText(privateKey)
                && StringUtils.hasText(eventsSecret) && StringUtils.hasText(integritySecret)
                && ambienteCoherente();
    }

    /** Para el webhook basta el secreto de eventos (y que sea del ambiente correcto). */
    public boolean webhookConfigurado() {
        return StringUtils.hasText(eventsSecret) && coincide(eventsSecret, "events");
    }

    /** Sandbox solo con llaves {@code _test_}; producción solo con {@code _prod_}: nunca se mezclan. */
    public boolean ambienteCoherente() {
        return coincide(publicKey, "pub") && coincide(privateKey, "prv")
                && coincide(eventsSecret, "events") && coincide(integritySecret, "integrity");
    }

    private boolean coincide(String llave, String tipo) {
        if (!StringUtils.hasText(llave)) {
            return false;
        }
        String ambiente = ambiente();
        if (ambiente == null) {
            return false;
        }
        return llave.startsWith(tipo + "_" + ambiente + "_") || llave.startsWith(ambiente + "_" + tipo + "_");
    }

    /** {@code test} si la URL es el sandbox, {@code prod} si es producción, {@code null} si es otra cosa. */
    private String ambiente() {
        String url = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        if (url.equals(SANDBOX)) {
            return "test";
        }
        return url.equals(PRODUCCION) ? "prod" : null;
    }

    /** No muestra ninguna llave: estas propiedades pueden terminar en un log. */
    @Override
    public String toString() {
        return "WompiProperties[baseUrl=" + baseUrl + ", llaves=" + (configurado() ? "configuradas" : "incompletas") + "]";
    }
}
