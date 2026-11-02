package co.edu.futdata.datasource.external;

import co.edu.futdata.config.WompiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link IWompiClient} sobre la API de Wompi: {@code GET {baseUrl}/transactions?reference=...} con
 * {@code Authorization: Bearer <WOMPI_PRIVATE_KEY>}. Nunca registra la llave ni el cuerpo de la respuesta.
 */
@Component
public class WompiClient implements IWompiClient {

    private static final Logger log = LoggerFactory.getLogger(WompiClient.class);

    private final RestClient http;
    private final WompiProperties propiedades;

    @Autowired
    public WompiClient(RestClient.Builder builder, WompiProperties propiedades,
                       @Value("${futdata.wompi.timeout:10s}") Duration timeout) {
        this(builder.requestFactory(fabrica(timeout)).build(), propiedades);
    }

    /** Para las pruebas. */
    WompiClient(RestClient http, WompiProperties propiedades) {
        this.http = http;
        this.propiedades = propiedades;
    }

    private static SimpleClientHttpRequestFactory fabrica(Duration timeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return fabrica;
    }

    @Override
    public List<TransaccionWompi> transaccionesDe(String referencia) {
        JsonNode cuerpo;
        try {
            cuerpo = http.get()
                    .uri(propiedades.baseUrl().replaceAll("/+$", "") + "/transactions?reference={referencia}", referencia)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + propiedades.privateKey())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // Sin el mensaje de la excepción: podría traer la URL o el encabezado
            log.warn("Wompi no respondió a la consulta de transacciones ({})", e.getClass().getSimpleName());
            throw new WompiNoDisponibleException("No se pudo consultar a Wompi", null);
        }
        List<TransaccionWompi> transacciones = new ArrayList<>();
        JsonNode datos = cuerpo == null ? null : cuerpo.path("data");
        if (datos != null && datos.isArray()) {
            for (JsonNode t : datos) {
                transacciones.add(aTransaccion(t));
            }
        }
        return transacciones;
    }

    /** {@code data.transaction} del evento o un elemento de {@code data} de la consulta. */
    public static TransaccionWompi aTransaccion(JsonNode t) {
        return new TransaccionWompi(t.path("id").asText(""), t.path("status").asText(""), t.path("amount_in_cents").asLong(-1),
                t.path("reference").asText(""), t.path("payment_method_type").asText(""));
    }
}
