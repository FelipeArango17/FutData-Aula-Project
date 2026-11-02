package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import co.edu.futdata.config.SupabaseProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/**
 * Lo que SupabaseStorageClient agrega para las fotos: tipos permitidos por llamada, borrar y URL firmadas por lote.
 * Contra un Storage simulado (MockRestServiceServer); sin red real.
 */
class SupabaseStorageClientFotosTest {

    private static final String URL = "https://proyecto-prueba.supabase.co";
    private static final String LLAVE = "llave-service-role-super-secreta-8f7e6d5c";
    private static final String RUTA = "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b/0b7e.jpg";
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};

    private MockRestServiceServer servidor;
    private SupabaseStorageClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new SupabaseStorageClient(builder.build(),
                new SupabaseProperties(URL, LLAVE, "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(10)));
    }

    // --- Tipos por llamada ---

    @Test
    void subeUnaImagenConSuTipoCuandoLaLlamadaLaPermite() {
        servidor.expect(requestTo(URL + "/storage/v1/object/fotos/" + RUTA))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "image/jpeg"))
                .andExpect(header("x-upsert", "false"))
                .andRespond(json(HttpStatus.OK, "{}"));

        assertThat(cliente.subir("fotos", RUTA, JPG, "image/jpeg", IAlmacenamientoArchivos.IMAGENES)).isEqualTo(RUTA);
        servidor.verify();
    }

    @Test
    void lasFotosNoAceptanPdfYLosComprobantesNoAceptanImagenes() {
        assertThatThrownBy(() -> cliente.subir("fotos", RUTA, JPG, "application/pdf", IAlmacenamientoArchivos.IMAGENES))
                .isInstanceOf(ArchivoInvalidoException.class)
                .hasMessage("Solo se pueden subir archivos image/jpeg o image/png");
        assertThatThrownBy(() -> cliente.subir("comprobantes", "2026/x.pdf", JPG, "image/jpeg"))
                .isInstanceOf(ArchivoInvalidoException.class)
                .hasMessage("Solo se pueden subir archivos application/pdf");
        servidor.verify(); // no se llamó a Storage
    }

    @Test
    void unaImagenDeMasDe5MbNoSeEnvia() {
        assertThatThrownBy(() -> cliente.subir("fotos", RUTA, new byte[IAlmacenamientoArchivos.TAMANO_MAXIMO + 1],
                "image/png", IAlmacenamientoArchivos.IMAGENES))
                .isInstanceOf(ArchivoInvalidoException.class);
        servidor.verify();
    }

    // --- Borrar ---

    @Test
    void borraConDelete() {
        servidor.expect(requestTo(URL + "/storage/v1/object/fotos/" + RUTA))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer " + LLAVE))
                .andRespond(json(HttpStatus.OK, "{\"message\": \"Successfully deleted\"}"));

        cliente.eliminar("fotos", RUTA);
        servidor.verify();
    }

    @Test
    void borrarUnArchivoQueNoExisteLanzaNoEncontrado() {
        servidor.expect(requestTo(URL + "/storage/v1/object/fotos/" + RUTA))
                .andRespond(json(HttpStatus.BAD_REQUEST, "{\"statusCode\": \"404\", \"error\": \"not_found\"}"));

        assertThatThrownBy(() -> cliente.eliminar("fotos", RUTA)).isInstanceOf(ArchivoNoEncontradoException.class);
    }

    @Test
    void borrarConStorageCaidoLanzaNoDisponible() {
        servidor.expect(requestTo(URL + "/storage/v1/object/fotos/" + RUTA)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> cliente.eliminar("fotos", RUTA)).isInstanceOf(AlmacenamientoNoDisponibleException.class);
    }

    @Test
    void borrarRechazaRutasPeligrosasSinLlamarAStorage() {
        assertThatThrownBy(() -> cliente.eliminar("fotos", "../otro/archivo.jpg")).isInstanceOf(ArchivoInvalidoException.class);
        servidor.verify();
    }

    // --- URL firmadas por lote ---

    @Test
    void firmaVariasRutasEnUnaSolaLlamada() {
        servidor.expect(requestTo(URL + "/storage/v1/object/sign/fotos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"expiresIn\": 300, \"paths\": [\"a/1.jpg\", \"b/2.png\", \"c/3.jpg\"]}",
                        JsonCompareMode.STRICT))
                .andRespond(json(HttpStatus.OK, """
                        [{"path": "a/1.jpg", "signedURL": "/object/sign/fotos/a/1.jpg?token=t1", "error": null},
                         {"path": "b/2.png", "signedURL": "/storage/v1/object/sign/fotos/b/2.png?token=t2", "error": null},
                         {"path": "c/3.jpg", "signedURL": null, "error": "Either the object does not exist or you do not have access to it"}]
                        """));

        Map<String, String> urls = cliente.urlsFirmadas("fotos", List.of("a/1.jpg", "b/2.png", "c/3.jpg"), Duration.ofMinutes(5));

        assertThat(urls).containsExactly(
                Map.entry("a/1.jpg", URL + "/storage/v1/object/sign/fotos/a/1.jpg?token=t1"),
                Map.entry("b/2.png", URL + "/storage/v1/object/sign/fotos/b/2.png?token=t2"));
        servidor.verify();
    }

    @Test
    void sinRutasNoLlamaAStorage() {
        assertThat(cliente.urlsFirmadas("fotos", List.of(), Duration.ofMinutes(5))).isEmpty();
        servidor.verify();
    }

    @Test
    void firmarPorLoteConStorageCaidoLanzaNoDisponible() {
        servidor.expect(requestTo(URL + "/storage/v1/object/sign/fotos")).andRespond(withException(new IOException("sin red")));

        assertThatThrownBy(() -> cliente.urlsFirmadas("fotos", List.of("a/1.jpg"), Duration.ofMinutes(5)))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class);
    }

    @Test
    void firmarPorLoteConLaLlaveRechazadaLanzaNoConfigurado() {
        servidor.expect(requestTo(URL + "/storage/v1/object/sign/fotos")).andRespond(json(HttpStatus.FORBIDDEN, "{}"));

        assertThatThrownBy(() -> cliente.urlsFirmadas("fotos", List.of("a/1.jpg"), Duration.ofMinutes(5)))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class)
                .hasMessageNotContaining(LLAVE);
    }

    private static ResponseCreator json(HttpStatus estado, String cuerpo) {
        return withStatus(estado).contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    }
}
