package co.edu.futdata.datasource.external;

import co.edu.futdata.config.N8nProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link INotificador} sobre el webhook de n8n: {@code POST {baseUrl}/webhook/futdata/notificaciones}
 * con la cabecera {@code X-FutData-Token} y el cuerpo {@code {"evento", "datos"}}.
 *
 * <p>Timeout de conexión y de lectura de {@code futdata.n8n.timeout} (5 s), salvo {@link #ENLACE_CONTRASENA}, que es
 * síncrono y usa {@code futdata.n8n.timeout-enlace} (15 s). Sin reintentos.
 *
 * <p><b>Registros:</b> solo el evento, el código HTTP, la duración y, si n8n lo devuelve, el número de
 * destinatarios. Nunca el cuerpo, el código 2FA, los correos, los destinatarios ni el token.
 *
 * <p>El cuerpo se serializa aquí a bytes con Jackson en lugar de pasarle el mapa a {@code RestClient}: con
 * {@code logging.level.org.springframework.web=DEBUG}, Spring registra el objeto que convierte ("Writing
 * [...]"), y ahí saldrían el código y los correos. Con bytes solo registra la referencia del arreglo.
 */
@Component
public class N8nClient implements INotificador {

    static final String RUTA_WEBHOOK = "/webhook/futdata/notificaciones";
    static final String CABECERA_TOKEN = "X-FutData-Token";
    static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final Logger log = LoggerFactory.getLogger(N8nClient.class);
    /** Correos y números de 6 dígitos o más: si el detalle de un 400 los trae, no se usa. */
    private static final Pattern DATO_SENSIBLE = Pattern.compile("@|\\d{6}");
    private static final int MAXIMO_DETALLE = 200;
    /** Campos de {@code datos} con valores fijos y no sensibles: que aparezcan en el detalle no lo descarta. */
    private static final Set<String> CAMPOS_NO_SENSIBLES = Set.of("evento", "tipoCambio");

    private final RestClient http;
    /** Para {@link INotificador#ENLACE_CONTRASENA}, con {@code futdata.n8n.timeout-enlace} (15 s). */
    private final RestClient httpEnlace;
    private final N8nProperties propiedades;
    private final ObjectMapper json;

    @Autowired
    public N8nClient(RestClient.Builder builder, N8nProperties propiedades, ObjectMapper json) {
        this(builder.clone().requestFactory(fabricaConTimeout(propiedades.timeout())).build(),
                builder.clone().requestFactory(fabricaConTimeout(propiedades.timeoutEnlace())).build(),
                propiedades, json);
    }

    /** Para las pruebas: recibe un {@code RestClient} ya armado (por ejemplo, con MockRestServiceServer). */
    N8nClient(RestClient http, N8nProperties propiedades, ObjectMapper json) {
        this(http, http, propiedades, json);
    }

    private N8nClient(RestClient http, RestClient httpEnlace, N8nProperties propiedades, ObjectMapper json) {
        this.http = http;
        this.httpEnlace = httpEnlace;
        this.propiedades = propiedades;
        this.json = json;
    }

    private static SimpleClientHttpRequestFactory fabricaConTimeout(Duration timeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return fabrica;
    }

    @Override
    public void notificar(String evento, Map<String, Object> datos) {
        String url = urlWebhook(evento);
        if (!StringUtils.hasText(evento) || datos == null || datos.isEmpty()) {
            throw new IllegalArgumentException("notificar() necesita un evento y datos no vacíos");
        }

        byte[] cuerpo;
        try {
            cuerpo = json.writeValueAsBytes(Map.of("evento", evento, "datos", datos));
        } catch (JsonProcessingException e) {
            // Sin la causa: su mensaje podría traer parte de los datos.
            log.error("Notificación {} no enviada: los datos no se pueden convertir a JSON", evento);
            throw new IllegalArgumentException("Los datos de la notificación " + evento + " no se pueden convertir a JSON");
        }

        long inicio = System.nanoTime();
        Respuesta respuesta;
        try {
            RestClient cliente = ENLACE_CONTRASENA.equals(evento) ? httpEnlace : http;
            respuesta = cliente.post()
                    .uri(url)
                    .contentType(JSON_UTF8)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(CABECERA_TOKEN, propiedades.webhookToken())
                    .body(cuerpo)
                    .exchange((peticion, res) -> new Respuesta(res.getStatusCode().value(), leerCuerpo(res)), true);
        } catch (ResourceAccessException e) {
            long ms = milisegundos(inicio);
            log.warn("Notificación {} no enviada: sin respuesta de n8n ({} ms)", evento, ms);
            // TODO auditoría (AuditoriaLogger): la registra el servicio que atrapa esta excepción (tiene el usuarioId):
            // evento, usuarioId, "sin respuesta" y fecha. Aquí n8n pudo no registrar nada: es el único registro.
            throw new NotificacionNoEnviadaException("No se pudo contactar a n8n para enviar la notificación " + evento
                    + " (tiempo de espera agotado o error de red)", evento, null, e);
        } catch (RestClientException e) {
            long ms = milisegundos(inicio);
            log.error("Notificación {} no enviada: error del cliente HTTP {} ({} ms)", evento, e.getClass().getSimpleName(), ms);
            // TODO auditoría (AuditoriaLogger): la registra el servicio que atrapa esta excepción (tiene el usuarioId).
            throw new NotificacionNoEnviadaException("No se pudo enviar la notificación " + evento
                    + " por un error del cliente HTTP", evento, null, null);
        }

        long ms = milisegundos(inicio);
        int codigo = respuesta.codigo();
        switch (codigo) {
            case 200, 202 -> {
                Object destinatarios = respuesta.cuerpo().get("destinatarios");
                if (destinatarios instanceof Number numero) {
                    log.info("Notificación {} aceptada por n8n: HTTP {}, {} ms, {} destinatarios", evento, codigo, ms, numero.intValue());
                } else {
                    log.info("Notificación {} aceptada por n8n: HTTP {}, {} ms", evento, codigo, ms);
                }
            }
            case 400 -> {
                log.error("Notificación {} rechazada por n8n: HTTP 400, {} ms (contrato roto)", evento, ms);
                throw new NotificacionRechazadaException(evento, detalleSeguro(respuesta.cuerpo(), datos));
            }
            case 401 -> {
                log.error("Notificación {} rechazada por n8n: HTTP 401, {} ms (revisa N8N_WEBHOOK_TOKEN)", evento, ms);
                throw new NotificadorMalConfiguradoException("n8n rechazó el token de la notificación " + evento
                        + " (401): N8N_WEBHOOK_TOKEN no coincide con el de n8n", evento, 401);
            }
            default -> {
                log.warn("Notificación {} no enviada: HTTP {}, {} ms", evento, codigo, ms);
                // TODO auditoría (AuditoriaLogger): la registra el servicio que atrapa esta excepción (tiene el usuarioId):
                // evento, usuarioId, código HTTP y fecha. Obligatorio ante 502.
                String motivo = codigo == 502 ? "n8n no pudo enviar el correo" : "n8n respondió un código inesperado";
                throw new NotificacionNoEnviadaException("No se pudo enviar la notificación " + evento
                        + " (" + codigo + "): " + motivo, evento, codigo, null);
            }
        }
    }

    /** Revisa la configuración antes de cada envío: en el perfil local la app arranca aunque falte. */
    private String urlWebhook(String evento) {
        boolean sinUrl = !StringUtils.hasText(propiedades.baseUrl());
        boolean sinToken = !StringUtils.hasText(propiedades.webhookToken());
        if (sinUrl || sinToken) {
            String faltan = sinUrl && sinToken ? "N8N_BASE_URL y N8N_WEBHOOK_TOKEN" : sinUrl ? "N8N_BASE_URL" : "N8N_WEBHOOK_TOKEN";
            log.error("Notificación {} no enviada: falta configurar {}", evento, faltan);
            throw new NotificadorMalConfiguradoException("No se puede enviar la notificación " + evento
                    + ": falta configurar " + faltan + " (ver backend/.env.example)", evento, null);
        }
        String base = propiedades.baseUrl().strip();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + RUTA_WEBHOOK;
    }

    /** El cuerpo de la respuesta como mapa; vacío si no es un objeto JSON. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> leerCuerpo(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res) {
        try {
            Map<String, Object> cuerpo = res.bodyTo(Map.class);
            return cuerpo != null ? cuerpo : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /**
     * {@code detalle} (o {@code error}) del 400, recortado, solo si no parece tener datos sensibles: ni
     * correos, ni números de 6 dígitos, ni ningún texto que el servicio haya puesto en {@code datos}, salvo
     * los de {@link #CAMPOS_NO_SENSIBLES} (por ejemplo, "tipoCambio debe ser CREADO, MODIFICADO o CANCELADO").
     */
    static String detalleSeguro(Map<String, Object> cuerpo, Map<String, Object> datos) {
        Object valor = cuerpo.get("detalle") instanceof String d && !d.isBlank() ? d : cuerpo.get("error");
        if (!(valor instanceof String texto) || texto.isBlank()) {
            return null;
        }
        String limpio = texto.replaceAll("\\p{Cntrl}", " ").strip();
        if (DATO_SENSIBLE.matcher(limpio).find()) {
            return null;
        }
        String minusculas = limpio.toLowerCase(Locale.ROOT);
        Set<String> textos = new HashSet<>();
        recolectarTextos(datos, textos);
        if (textos.stream().anyMatch(minusculas::contains)) {
            return null;
        }
        return limpio.length() > MAXIMO_DETALLE ? limpio.substring(0, MAXIMO_DETALLE) + "…" : limpio;
    }

    private static void recolectarTextos(Object valor, Set<String> textos) {
        switch (valor) {
            case String s when s.strip().length() >= 3 -> textos.add(s.strip().toLowerCase(Locale.ROOT));
            case Map<?, ?> mapa -> mapa.forEach((campo, v) -> {
                if (!CAMPOS_NO_SENSIBLES.contains(campo)) {
                    recolectarTextos(v, textos);
                }
            });
            case Collection<?> lista -> lista.forEach(v -> recolectarTextos(v, textos));
            case null, default -> { }
        }
    }

    private static long milisegundos(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }

    private record Respuesta(int codigo, Map<String, Object> cuerpo) {
    }
}
