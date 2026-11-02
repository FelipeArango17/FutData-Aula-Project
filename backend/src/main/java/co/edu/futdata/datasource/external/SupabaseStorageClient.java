package co.edu.futdata.datasource.external;

import co.edu.futdata.config.SupabaseProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link IAlmacenamientoArchivos} sobre la API REST de Supabase Storage ({@code {url}/storage/v1}):
 *
 * <ul>
 *   <li>Subir: {@code POST /object/{bucket}/{ruta}} con el archivo como cuerpo y {@code x-upsert: false}, para
 *       que un archivo existente no se sobrescriba.</li>
 *   <li>URL firmada: {@code POST /object/sign/{bucket}/{ruta}} con {@code {"expiresIn": segundos}}; responde
 *       {@code {"signedURL": "/object/sign/...?token=..."}}, relativa a {@code {url}/storage/v1}.</li>
 *   <li>URL firmadas por lote: {@code POST /object/sign/{bucket}} con {@code {"expiresIn": s, "paths": [...]}}; una
 *       sola llamada para toda una página.</li>
 *   <li>Borrar: {@code DELETE /object/{bucket}/{ruta}}.</li>
 * </ul>
 *
 * <p>Se autentica con la llave {@code service_role} en las cabeceras {@code Authorization: Bearer} y
 * {@code apikey}. Timeout de conexión y de lectura de {@code futdata.supabase.timeout}. Sin reintentos.
 *
 * <p><b>Registros:</b> operación, bucket, ruta, tamaño, código HTTP y duración. Nunca la llave ni la URL firmada
 * (quien la tenga puede descargar el archivo).
 *
 * <p>Códigos: además del código HTTP se lee el cuerpo del error, porque algunas versiones de Storage responden
 * {@code 400} con {@code {"statusCode": "409", "error": "Duplicate"}} en lugar de {@code 409}.
 */
@Component
public class SupabaseStorageClient implements IAlmacenamientoArchivos {

    static final String RUTA_API = "/storage/v1";
    static final Duration VIGENCIA_MINIMA = Duration.ofSeconds(10);
    static final Duration VIGENCIA_MAXIMA = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageClient.class);
    /** Nombres de bucket de Supabase: minúsculas, dígitos, punto, guion y guion bajo. */
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9._-]{0,62}");
    /** Segmentos con letras, dígitos, punto, guion o guion bajo, separados por una sola "/". */
    private static final Pattern RUTA = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*");
    private static final int MAXIMO_RUTA = 300;
    private static final Map<String, Integer> CODIGOS_DEL_CUERPO = Map.of(
            "keyalreadyexists", 409, "resourcealreadyexists", 409, "duplicate", 409, "already_exists", 409,
            "nosuchkey", 404, "nosuchbucket", 404, "not_found", 404,
            "entitytoolarge", 413, "payload too large", 413,
            "invalidjwt", 401);
    private static final Set<Integer> CODIGOS_CONOCIDOS = Set.of(400, 401, 403, 404, 409, 413);

    private final RestClient http;
    private final SupabaseProperties propiedades;

    @Autowired
    public SupabaseStorageClient(RestClient.Builder builder, SupabaseProperties propiedades) {
        this(builder.requestFactory(fabricaConTimeout(propiedades.timeout())).build(), propiedades);
    }

    /** Para las pruebas: recibe un {@code RestClient} ya armado (por ejemplo, con MockRestServiceServer). */
    SupabaseStorageClient(RestClient http, SupabaseProperties propiedades) {
        this.http = http;
        this.propiedades = propiedades;
    }

    private static SimpleClientHttpRequestFactory fabricaConTimeout(Duration timeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return fabrica;
    }

    @Override
    public String subir(String bucket, String ruta, byte[] contenido, String contentType) {
        return subir(bucket, ruta, contenido, contentType, List.of(PDF));
    }

    @Override
    public String subir(String bucket, String ruta, byte[] contenido, String contentType, List<String> tiposPermitidos) {
        validarUbicacion(bucket, ruta);
        String tipo = contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
        if (tiposPermitidos == null || tiposPermitidos.isEmpty() || !tiposPermitidos.contains(tipo)) {
            throw new ArchivoInvalidoException("Solo se pueden subir archivos "
                    + String.join(" o ", tiposPermitidos == null ? List.of() : tiposPermitidos), null, null);
        }
        if (contenido == null || contenido.length == 0) {
            throw new ArchivoInvalidoException("El archivo está vacío", null, null);
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new ArchivoInvalidoException("El archivo pesa " + contenido.length + " bytes; el máximo es "
                    + TAMANO_MAXIMO + " (5 MB)", null, null);
        }
        String base = urlApi("subir");
        String destino = bucket + "/" + ruta;

        long inicio = System.nanoTime();
        Respuesta respuesta = llamar("subir", destino, inicio, () -> http.post()
                .uri(base + "/object/" + destino)
                .headers(this::autenticar)
                .contentType(MediaType.parseMediaType(tipo))
                .header("x-upsert", "false")
                .body(contenido)
                .exchange((peticion, res) -> new Respuesta(res.getStatusCode().value(), leerCuerpo(res)), true));
        long ms = milisegundos(inicio);

        int codigo = codigoEfectivo(respuesta);
        if (codigo == 200) {
            log.info("Archivo subido a Storage: {} ({} KB), HTTP 200, {} ms", destino, Math.ceilDiv(contenido.length, 1024), ms);
            return ruta;
        }
        log.warn("Storage no guardó {}: HTTP {} (efectivo {}), {} ms", destino, respuesta.codigo(), codigo, ms);
        throw switch (codigo) {
            case 409 -> new ArchivoYaExisteException("Ya existe un archivo en " + destino + "; no se sobrescribe", codigo, null);
            case 404 -> new AlmacenamientoNoConfiguradoException("El bucket " + bucket + " no existe en Supabase Storage", codigo, null);
            case 401, 403 -> llaveRechazada(codigo);
            case 413 -> new AlmacenamientoRechazadoException("Supabase Storage rechazó " + destino
                    + " por tamaño (413); revisa el límite del bucket", codigo, null);
            default -> otroCodigo(codigo, "guardar " + destino);
        };
    }

    @Override
    public String urlFirmada(String bucket, String ruta, Duration vigencia) {
        validarUbicacion(bucket, ruta);
        if (vigencia == null || vigencia.compareTo(VIGENCIA_MINIMA) < 0 || vigencia.compareTo(VIGENCIA_MAXIMA) > 0) {
            throw new IllegalArgumentException("La vigencia de la URL firmada debe estar entre 10 s y 1 h");
        }
        String base = urlApi("firmar");
        String destino = bucket + "/" + ruta;

        long inicio = System.nanoTime();
        Respuesta respuesta = llamar("firmar", destino, inicio, () -> http.post()
                .uri(base + "/object/sign/" + destino)
                .headers(this::autenticar)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", vigencia.toSeconds()))
                .exchange((peticion, res) -> new Respuesta(res.getStatusCode().value(), leerCuerpo(res)), true));
        long ms = milisegundos(inicio);

        int codigo = codigoEfectivo(respuesta);
        if (codigo == 200 && respuesta.cuerpo().get("signedURL") instanceof String relativa && relativa.startsWith("/")) {
            // La URL firmada no se registra: quien la tenga puede descargar el archivo.
            log.info("URL firmada creada para {} (vigencia {} s), HTTP 200, {} ms", destino, vigencia.toSeconds(), ms);
            String completa = relativa.startsWith(RUTA_API + "/") ? base.substring(0, base.length() - RUTA_API.length()) + relativa : base + relativa;
            return completa + (completa.contains("?") ? "&" : "?") + "download=" + ruta.substring(ruta.lastIndexOf('/') + 1);
        }
        log.warn("Storage no firmó {}: HTTP {} (efectivo {}), {} ms", destino, respuesta.codigo(), codigo, ms);
        throw switch (codigo) {
            case 200 -> new AlmacenamientoNoDisponibleException("Supabase Storage respondió sin signedURL para " + destino, 200, null);
            case 404 -> new ArchivoNoEncontradoException("No existe el archivo " + destino + " en Supabase Storage", codigo, null);
            case 401, 403 -> llaveRechazada(codigo);
            default -> otroCodigo(codigo, "firmar " + destino);
        };
    }

    @Override
    public void eliminar(String bucket, String ruta) {
        validarUbicacion(bucket, ruta);
        String base = urlApi("borrar");
        String destino = bucket + "/" + ruta;

        long inicio = System.nanoTime();
        Respuesta respuesta = llamar("borrar", destino, inicio, () -> http.delete()
                .uri(base + "/object/" + destino)
                .headers(this::autenticar)
                .exchange((peticion, res) -> new Respuesta(res.getStatusCode().value(), leerCuerpo(res)), true));
        long ms = milisegundos(inicio);

        int codigo = codigoEfectivo(respuesta);
        if (codigo == 200 || codigo == 204) {
            log.info("Archivo borrado de Storage: {}, HTTP {}, {} ms", destino, respuesta.codigo(), ms);
            return;
        }
        log.warn("Storage no borró {}: HTTP {} (efectivo {}), {} ms", destino, respuesta.codigo(), codigo, ms);
        throw switch (codigo) {
            case 404 -> new ArchivoNoEncontradoException("No existe el archivo " + destino + " en Supabase Storage", codigo, null);
            case 401, 403 -> llaveRechazada(codigo);
            default -> otroCodigo(codigo, "borrar " + destino);
        };
    }

    /**
     * {@code POST /object/sign/{bucket}} con {@code {"expiresIn": s, "paths": [...]}}; responde una lista con
     * {@code {"path", "signedURL", "error"}} por ruta. Las rutas con {@code error} (por ejemplo, sin archivo) no van
     * en el resultado.
     */
    @Override
    public Map<String, String> urlsFirmadas(String bucket, List<String> rutas, Duration vigencia) {
        if (rutas == null || rutas.isEmpty()) return Map.of();
        rutas.forEach(ruta -> validarUbicacion(bucket, ruta));
        validarVigencia(vigencia);
        String base = urlApi("firmar");

        long inicio = System.nanoTime();
        RespuestaLista respuesta = llamar("firmar", bucket + " (" + rutas.size() + " rutas)", inicio, () -> http.post()
                .uri(base + "/object/sign/" + bucket)
                .headers(this::autenticar)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", vigencia.toSeconds(), "paths", rutas))
                .exchange((peticion, res) -> new RespuestaLista(res.getStatusCode().value(), leerLista(res)), true));
        long ms = milisegundos(inicio);

        if (respuesta.codigo() == 200) {
            Map<String, String> urls = new LinkedHashMap<>();
            for (Map<String, Object> item : respuesta.items()) {
                if (item.get("error") == null && item.get("path") instanceof String ruta && rutas.contains(ruta)
                        && item.get("signedURL") instanceof String relativa && relativa.startsWith("/")) {
                    urls.put(ruta, absoluta(base, relativa));
                }
            }
            // Las URL firmadas no se registran: quien las tenga puede descargar los archivos.
            log.info("URL firmadas por lote en {}: {} de {} rutas (vigencia {} s), HTTP 200, {} ms", bucket,
                    urls.size(), rutas.size(), vigencia.toSeconds(), ms);
            return urls;
        }
        log.warn("Storage no firmó por lote en {}: HTTP {}, {} ms", bucket, respuesta.codigo(), ms);
        throw switch (respuesta.codigo()) {
            case 404 -> new AlmacenamientoNoConfiguradoException("El bucket " + bucket + " no existe en Supabase Storage", 404, null);
            case 401, 403 -> llaveRechazada(respuesta.codigo());
            default -> otroCodigo(respuesta.codigo(), "firmar por lote en " + bucket);
        };
    }

    private static void validarVigencia(Duration vigencia) {
        if (vigencia == null || vigencia.compareTo(VIGENCIA_MINIMA) < 0 || vigencia.compareTo(VIGENCIA_MAXIMA) > 0) {
            throw new IllegalArgumentException("La vigencia de la URL firmada debe estar entre 10 s y 1 h");
        }
    }

    /** {@code signedURL} viene relativa a {@code {url}/storage/v1} (o, en algunas versiones, con {@code /storage/v1}). */
    private static String absoluta(String base, String relativa) {
        return relativa.startsWith(RUTA_API + "/") ? base.substring(0, base.length() - RUTA_API.length()) + relativa
                : base + relativa;
    }

    private void autenticar(HttpHeaders cabeceras) {
        cabeceras.setBearerAuth(propiedades.serviceRoleKey().strip());
        cabeceras.set("apikey", propiedades.serviceRoleKey().strip());
    }

    /** Hace la llamada y convierte las fallas de red en {@link AlmacenamientoNoDisponibleException}. */
    private <T> T llamar(String operacion, String destino, long inicio, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            log.warn("Storage no respondió al {} {} ({} ms)", operacion, destino, milisegundos(inicio));
            throw new AlmacenamientoNoDisponibleException("No se pudo contactar a Supabase Storage para " + operacion + " "
                    + destino + " (tiempo de espera agotado o error de red)", null, e);
        } catch (RestClientException e) {
            log.error("Error del cliente HTTP al {} {}: {} ({} ms)", operacion, destino, e.getClass().getSimpleName(), milisegundos(inicio));
            // Sin la causa: su mensaje podría traer cabeceras o parte de la respuesta.
            throw new AlmacenamientoNoDisponibleException("No se pudo " + operacion + " " + destino
                    + " por un error del cliente HTTP", null, null);
        }
    }

    /** Revisa la configuración antes de cada llamada: en el perfil local la app arranca aunque falte. */
    private String urlApi(String operacion) {
        boolean sinUrl = !StringUtils.hasText(propiedades.url());
        boolean sinLlave = !StringUtils.hasText(propiedades.serviceRoleKey());
        if (sinUrl || sinLlave) {
            String faltan = sinUrl && sinLlave ? "SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY" : sinUrl ? "SUPABASE_URL" : "SUPABASE_SERVICE_ROLE_KEY";
            log.error("Storage no usado ({}): falta configurar {}", operacion, faltan);
            throw new AlmacenamientoNoConfiguradoException("No se puede usar Supabase Storage: falta configurar " + faltan
                    + " (ver backend/.env.example)", null, null);
        }
        String base = propiedades.url().strip();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + RUTA_API;
    }

    static void validarUbicacion(String bucket, String ruta) {
        if (bucket == null || !BUCKET.matcher(bucket).matches()) {
            throw new ArchivoInvalidoException("Nombre de bucket inválido", null, null);
        }
        if (ruta == null || ruta.isBlank()) {
            throw new ArchivoInvalidoException("La ruta del archivo es obligatoria", null, null);
        }
        if (ruta.startsWith("/")) {
            throw new ArchivoInvalidoException("La ruta no puede empezar con /", null, null);
        }
        if (ruta.contains("..")) {
            throw new ArchivoInvalidoException("La ruta no puede contener ..", null, null);
        }
        if (ruta.length() > MAXIMO_RUTA || !RUTA.matcher(ruta).matches()) {
            throw new ArchivoInvalidoException("La ruta solo puede tener letras sin tilde, dígitos, punto, guion, guion bajo "
                    + "y una / entre carpetas", null, null);
        }
    }

    /** El código HTTP, o el que trae el cuerpo del error si Storage respondió 400 con otro código dentro. */
    static int codigoEfectivo(Respuesta respuesta) {
        if (respuesta.codigo() != 400) {
            return respuesta.codigo();
        }
        Map<String, Object> cuerpo = respuesta.cuerpo();
        if (cuerpo.get("statusCode") != null) {
            try {
                int interno = Integer.parseInt(String.valueOf(cuerpo.get("statusCode")).strip());
                if (CODIGOS_CONOCIDOS.contains(interno)) {
                    return interno;
                }
            } catch (NumberFormatException e) {
                // No es un número: se miran "code" y "error".
            }
        }
        for (String campo : new String[] {"code", "error"}) {
            if (cuerpo.get(campo) instanceof String valor) {
                Integer codigo = CODIGOS_DEL_CUERPO.get(valor.strip().toLowerCase(Locale.ROOT));
                if (codigo != null) {
                    return codigo;
                }
            }
        }
        return 400;
    }

    private static AlmacenamientoNoConfiguradoException llaveRechazada(int codigo) {
        return new AlmacenamientoNoConfiguradoException("Supabase Storage rechazó la llave (" + codigo
                + "): revisa SUPABASE_SERVICE_ROLE_KEY", codigo, null);
    }

    private static AlmacenamientoException otroCodigo(int codigo, String accion) {
        if (codigo >= 500 || codigo == 429) {
            return new AlmacenamientoNoDisponibleException("Supabase Storage no está disponible para " + accion
                    + " (" + codigo + ")", codigo, null);
        }
        if (codigo >= 400) {
            return new AlmacenamientoRechazadoException("Supabase Storage rechazó " + accion + " (" + codigo + ")", codigo, null);
        }
        return new AlmacenamientoNoDisponibleException("Supabase Storage respondió un código inesperado al " + accion
                + " (" + codigo + ")", codigo, null);
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

    /** El cuerpo de la respuesta como lista de objetos; vacía si no es un arreglo JSON. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> leerLista(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res) {
        try {
            List<Object> cuerpo = res.bodyTo(List.class);
            if (cuerpo == null) return List.of();
            return cuerpo.stream().filter(Map.class::isInstance).map(o -> (Map<String, Object>) o).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static long milisegundos(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }

    record Respuesta(int codigo, Map<String, Object> cuerpo) {
    }

    record RespuestaLista(int codigo, List<Map<String, Object>> items) {
    }
}
