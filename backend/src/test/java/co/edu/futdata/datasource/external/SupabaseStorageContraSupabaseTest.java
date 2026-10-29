package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.config.ClubProperties;
import co.edu.futdata.config.SupabaseProperties;
import co.edu.futdata.util.reports.ComprobantePagoDTO;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import co.edu.futdata.util.reports.ComprobantePdfGenerator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Prueba manual contra un proyecto real de Supabase. <b>Desactivada por defecto</b>: no corre en
 * {@code ./mvnw verify} ni en el CI. Sube un comprobante de ejemplo a {@code pruebas/} en el bucket, pide la URL
 * firmada, la descarga y compara los bytes; al final borra el archivo.
 *
 * <pre>
 * FUTDATA_PRUEBA_SUPABASE_REAL=true SUPABASE_URL=https://tu-ref.supabase.co SUPABASE_SERVICE_ROLE_KEY=... \
 * ./mvnw test -Dtest=SupabaseStorageContraSupabaseTest
 * </pre>
 *
 * Opcional: {@code SUPABASE_BUCKET_COMPROBANTES} (por defecto {@code comprobantes}). Ver {@code backend/README.md},
 * sección "Almacenamiento de comprobantes (Supabase Storage)".
 */
@EnabledIfEnvironmentVariable(named = "FUTDATA_PRUEBA_SUPABASE_REAL", matches = "true")
class SupabaseStorageContraSupabaseTest {

    private final String url = variable("SUPABASE_URL");
    private final String llave = variable("SUPABASE_SERVICE_ROLE_KEY");
    private final String bucket = System.getenv().getOrDefault("SUPABASE_BUCKET_COMPROBANTES", "comprobantes");
    private final String ruta = "pruebas/comprobante-prueba-" + System.currentTimeMillis() + ".pdf";
    private final SupabaseStorageClient cliente = new SupabaseStorageClient(RestClient.builder(),
            new SupabaseProperties(url, llave, bucket, Duration.ofMinutes(5), Duration.ofSeconds(15)));

    @Test
    void subeFirmaYDescargaElMismoPdf() throws IOException, InterruptedException {
        byte[] pdf = new ComprobantePdfGenerator(new ClubProperties("Asociación Deportiva Prado Fútbol Club", "811032309-1",
                "Calle 40 Sur 75b-81, San Antonio de Prado, Medellín, Antioquia", "3137317366", "pradofutbolclub@gmail.com"))
                .generar(new ComprobantePagoDTO("FD-2026-999999", Instant.now(), "Deportista de Prueba", "Sub-12",
                        "Acudiente de Prueba", "Prueba de almacenamiento (borrar)", 1_000, LocalDate.now(), MetodoPago.EFECTIVO, null));

        assertThat(cliente.subir(bucket, ruta, pdf, IAlmacenamientoArchivos.PDF)).isEqualTo(ruta);
        assertThatThrownBy(() -> cliente.subir(bucket, ruta, pdf, IAlmacenamientoArchivos.PDF))
                .as("no sobrescribe").isInstanceOf(ArchivoYaExisteException.class);

        String firmada = cliente.urlFirmada(bucket, ruta, Duration.ofSeconds(60));
        HttpResponse<byte[]> descarga = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(firmada)).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(descarga.statusCode()).isEqualTo(200);
        assertThat(descarga.body()).isEqualTo(pdf);
    }

    @Test
    void unArchivoQueNoExisteLanzaNoEncontrado() {
        assertThatThrownBy(() -> cliente.urlFirmada(bucket, "pruebas/no-existe-" + System.nanoTime() + ".pdf", Duration.ofSeconds(60)))
                .isInstanceOf(ArchivoNoEncontradoException.class);
    }

    /** Borra el archivo de prueba (la interfaz no tiene borrar a propósito: los comprobantes no se borran). */
    @AfterEach
    void borrarElArchivoDePrueba() {
        RestClient.create().method(HttpMethod.DELETE)
                .uri(url.replaceAll("/+$", "") + "/storage/v1/object/" + bucket)
                .header("Authorization", "Bearer " + llave)
                .header("apikey", llave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prefixes", List.of(ruta)))
                .retrieve()
                .toBodilessEntity();
    }

    private static String variable(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Para la prueba contra Supabase falta la variable de entorno " + nombre);
        }
        return valor;
    }
}
