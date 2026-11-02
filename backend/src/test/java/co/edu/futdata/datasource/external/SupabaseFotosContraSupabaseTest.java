package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.futdata.config.SupabaseProperties;
import co.edu.futdata.domain.rules.ReglasFoto;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Prueba manual de las fotos contra un proyecto real de Supabase. <b>Desactivada por defecto</b>, con el mismo
 * interruptor que {@link SupabaseStorageContraSupabaseTest}: no corre en {@code ./mvnw verify} ni en el CI. Si el
 * interruptor está encendido pero falta {@code SUPABASE_URL} o {@code SUPABASE_SERVICE_ROLE_KEY}, se <b>salta</b>.
 *
 * <p>Sube dos PNG de 1x1 al bucket de fotos ({@code {uuid}/{uuid}.png}), los firma en una sola llamada por lote,
 * descarga uno, los borra y comprueba que ya no se pueden firmar. Al final borra siempre lo que haya subido, aunque
 * la prueba falle (con el borrado masivo de Storage, sin depender del cliente que se está probando).
 *
 * <pre>
 * $env:FUTDATA_PRUEBA_SUPABASE_REAL='true'; $env:SUPABASE_URL='https://tu-ref.supabase.co'
 * $env:SUPABASE_SERVICE_ROLE_KEY='...'; .\mvnw.cmd test "-Dtest=SupabaseFotosContraSupabaseTest"
 * </pre>
 *
 * Opcional: {@code FOTOS_BUCKET} (por defecto {@code fotos}, el de la migración 007).
 */
@EnabledIfEnvironmentVariable(named = "FUTDATA_PRUEBA_SUPABASE_REAL", matches = "true")
class SupabaseFotosContraSupabaseTest {

    /** PNG real de 1x1 píxel (67 bytes). */
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    private static final Duration VIGENCIA = Duration.ofSeconds(60);

    private final String bucket = System.getenv().getOrDefault("FOTOS_BUCKET", "fotos");
    /** Todo lo que la prueba subió (o intentó subir), para borrarlo al final. */
    private final List<String> subidas = new ArrayList<>();
    private String url;
    private String llave;
    private SupabaseStorageClient cliente;

    @BeforeEach
    void preparar() {
        url = System.getenv("SUPABASE_URL");
        llave = System.getenv("SUPABASE_SERVICE_ROLE_KEY");
        assumeTrue(url != null && !url.isBlank(), "Falta SUPABASE_URL: se salta la prueba de fotos contra Supabase");
        assumeTrue(llave != null && !llave.isBlank(), "Falta SUPABASE_SERVICE_ROLE_KEY: se salta la prueba de fotos contra Supabase");
        cliente = new SupabaseStorageClient(RestClient.builder(),
                new SupabaseProperties(url, llave, "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(15)));
    }

    @Test
    void subeDosFotosLasFirmaPorLoteDescargaUnaYLasBorra() throws IOException, InterruptedException {
        assertThat(ReglasFoto.tipoPorContenido(PNG_1X1)).as("el PNG de prueba pasa la validación del servicio").contains(ReglasFoto.PNG);
        String primera = UUID.randomUUID() + "/" + UUID.randomUUID() + ".png";
        String segunda = UUID.randomUUID() + "/" + UUID.randomUUID() + ".png";

        // (1) y (2) Subir las dos
        subir(primera);
        subir(segunda);

        // (2) Firmar las dos en una sola llamada
        Map<String, String> urls = cliente.urlsFirmadas(bucket, List.of(primera, segunda), VIGENCIA);
        assertThat(urls).containsOnlyKeys(primera, segunda);
        assertThat(urls.values()).allSatisfy(u -> assertThat(u).startsWith(url.replaceAll("/+$", "") + "/storage/v1/object/sign/"));

        // (3) Descargar la primera: 200 e image/png, con los mismos bytes
        HttpResponse<byte[]> descarga = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(urls.get(primera))).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(descarga.statusCode()).isEqualTo(200);
        assertThat(descarga.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("image/png"));
        assertThat(descarga.body()).isEqualTo(PNG_1X1);

        // (4) Borrar las dos; ya no se pueden firmar
        cliente.eliminar(bucket, primera);
        cliente.eliminar(bucket, segunda);
        assertThat(cliente.urlsFirmadas(bucket, List.of(primera, segunda), VIGENCIA))
                .as("firmar por lote ya no las incluye").isEmpty();
        assertThatThrownBy(() -> cliente.urlFirmada(bucket, primera, VIGENCIA))
                .as("firmar una sola ya no la encuentra").isInstanceOf(ArchivoNoEncontradoException.class);
        assertThatThrownBy(() -> cliente.eliminar(bucket, primera))
                .as("borrarla otra vez: ya no existe").isInstanceOf(ArchivoNoEncontradoException.class);
    }

    private void subir(String ruta) {
        subidas.add(ruta); // antes de subir: si la llamada falla a medias, igual se intenta borrar
        assertThat(cliente.subir(bucket, ruta, PNG_1X1, IAlmacenamientoArchivos.PNG, IAlmacenamientoArchivos.IMAGENES))
                .isEqualTo(ruta);
    }

    /**
     * (5) Borra siempre lo subido con {@code DELETE /object/{bucket}} y {@code {"prefixes": [...]}}, que responde 200
     * aunque alguno ya no exista. No usa {@code cliente.eliminar}, que es lo que se está probando.
     */
    @AfterEach
    void borrarLoSubido() {
        if (subidas.isEmpty() || cliente == null) return;
        RestClient.create().method(HttpMethod.DELETE)
                .uri(url.replaceAll("/+$", "") + "/storage/v1/object/" + bucket)
                .header("Authorization", "Bearer " + llave)
                .header("apikey", llave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prefixes", subidas))
                .retrieve()
                .toBodilessEntity();
    }
}
