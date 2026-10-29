package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import co.edu.futdata.config.SupabaseProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/** SupabaseStorageClient contra un Storage simulado (MockRestServiceServer o un servidor en 127.0.0.1): sin red real. */
class SupabaseStorageClientTest {

    private static final String URL = "https://proyecto-prueba.supabase.co";
    private static final String LLAVE = "llave-service-role-super-secreta-8f7e6d5c";
    private static final String BUCKET = "comprobantes";
    private static final String RUTA = "2026/FD-2026-000123.pdf";
    private static final String URL_SUBIR = URL + "/storage/v1/object/comprobantes/2026/FD-2026-000123.pdf";
    private static final String URL_FIRMAR = URL + "/storage/v1/object/sign/comprobantes/2026/FD-2026-000123.pdf";
    private static final String TOKEN_FIRMA = "eyJhbGciOiJIUzI1NiJ9.token-de-la-url-firmada";
    private static final byte[] PDF = "%PDF-1.4 comprobante de prueba".getBytes(StandardCharsets.US_ASCII);
    /** Lo que nunca debe salir en un log ni en una excepción. */
    private static final List<String> SECRETOS = List.of(LLAVE, TOKEN_FIRMA);
    private static final List<String> LOGGERS_EN_DETALLE = List.of("co.edu.futdata", "org.springframework.web", "org.springframework.http");

    private MockRestServiceServer servidor;
    private SupabaseStorageClient cliente;
    private ListAppender<ILoggingEvent> registros;
    private final Map<String, Level> nivelesOriginales = new HashMap<>();

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new SupabaseStorageClient(builder.build(), propiedades(URL, LLAVE, Duration.ofSeconds(10)));

        registros = new ListAppender<>();
        registros.start();
        raiz().addAppender(registros);
        LOGGERS_EN_DETALLE.forEach(nombre -> {
            Logger logger = (Logger) LoggerFactory.getLogger(nombre);
            nivelesOriginales.put(nombre, logger.getLevel());
            logger.setLevel(Level.TRACE);
        });
    }

    @AfterEach
    void revisarQueLosLogsNoTenganSecretos() {
        raiz().detachAppender(registros);
        nivelesOriginales.forEach((nombre, nivel) -> ((Logger) LoggerFactory.getLogger(nombre)).setLevel(nivel));
        String todo = textoDeLosLogs();
        SECRETOS.forEach(secreto -> assertThat(todo).as("los logs no deben contener secretos").doesNotContain(secreto));
    }

    // --- Subir ---

    @Test
    void subirHaceElPostQuePideStorage() {
        servidor.expect(requestTo(URL_SUBIR))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LLAVE))
                .andExpect(header("apikey", LLAVE))
                .andExpect(header("Content-Type", "application/pdf"))
                .andExpect(header("x-upsert", "false"))
                .andExpect(content().bytes(PDF))
                .andRespond(json(HttpStatus.OK, "{\"Key\": \"comprobantes/2026/FD-2026-000123.pdf\", \"Id\": \"b1c2\"}"));

        assertThat(cliente.subir(BUCKET, RUTA, PDF, "application/pdf")).isEqualTo(RUTA);
        servidor.verify();
        assertThat(textoDeLosLogs()).contains("comprobantes/2026/FD-2026-000123.pdf", "HTTP 200");
    }

    @Test
    void quitaLaBarraFinalDeLaUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer otro = MockRestServiceServer.bindTo(builder).build();
        otro.expect(requestTo(URL_SUBIR)).andRespond(json(HttpStatus.OK, "{}"));

        new SupabaseStorageClient(builder.build(), propiedades(URL + "/", LLAVE, Duration.ofSeconds(10)))
                .subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF);
        otro.verify();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            409 | {"statusCode": "409", "error": "Duplicate", "message": "The resource already exists"}
            400 | {"statusCode": "409", "error": "Duplicate", "message": "The resource already exists"}
            400 | {"code": "KeyAlreadyExists", "message": "The resource already exists"}
            """)
    void unArchivoExistenteLanzaYaExiste(int estado, String cuerpo) {
        servidor.expect(requestTo(URL_SUBIR)).andRespond(json(HttpStatus.valueOf(estado), cuerpo));

        ArchivoYaExisteException e = catchThrowableOfType(ArchivoYaExisteException.class,
                () -> cliente.subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF));
        assertThat(e.getMessage()).isEqualTo("Ya existe un archivo en comprobantes/2026/FD-2026-000123.pdf; no se sobrescribe");
        assertThat(e.getCodigoHttp()).hasValue(409);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            400 | {"statusCode": "400", "error": "InvalidRequest", "message": "Bad request"} | AlmacenamientoRechazadoException     | 400
            400 | no es json                                                                | AlmacenamientoRechazadoException     | 400
            401 | {"statusCode": "401", "error": "InvalidJWT", "message": "invalid signature"} | AlmacenamientoNoConfiguradoException | 401
            400 | {"statusCode": "403", "error": "Unauthorized", "message": "new row violates"} | AlmacenamientoNoConfiguradoException | 403
            403 | {"code": "AccessDenied", "message": "Access denied"}                       | AlmacenamientoNoConfiguradoException | 403
            404 | {"code": "NoSuchBucket", "message": "Bucket not found"}                    | AlmacenamientoNoConfiguradoException | 404
            413 | {"code": "EntityTooLarge", "message": "Payload too large"}                 | AlmacenamientoRechazadoException     | 413
            400 | {"statusCode": "413", "error": "Payload too large"}                         | AlmacenamientoRechazadoException     | 413
            422 | {}                                                                          | AlmacenamientoRechazadoException     | 422
            500 | {"code": "InternalError"}                                                   | AlmacenamientoNoDisponibleException  | 500
            502 | {}                                                                          | AlmacenamientoNoDisponibleException  | 502
            503 | {"code": "SlowDown"}                                                        | AlmacenamientoNoDisponibleException  | 503
            429 | {}                                                                          | AlmacenamientoNoDisponibleException  | 429
            201 | {}                                                                          | AlmacenamientoNoDisponibleException  | 201
            """)
    void cadaRespuestaDeErrorAlSubirLanzaSuExcepcion(int estado, String cuerpo, String excepcion, int codigoEsperado) {
        servidor.expect(requestTo(URL_SUBIR)).andRespond(json(HttpStatus.valueOf(estado), cuerpo));

        Throwable e = catchThrowable(() -> cliente.subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF));

        assertThat(e.getClass().getSimpleName()).isEqualTo(excepcion);
        assertThat(((AlmacenamientoException) e).getCodigoHttp()).hasValue(codigoEsperado);
        sinSecretos(e);
    }

    @Test
    void unaLlaveRechazadaDiceQueVariableRevisar() {
        servidor.expect(requestTo(URL_SUBIR)).andRespond(json(HttpStatus.UNAUTHORIZED, "{}"));

        assertThatThrownBy(() -> cliente.subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class)
                .hasMessage("Supabase Storage rechazó la llave (401): revisa SUPABASE_SERVICE_ROLE_KEY");
    }

    @Test
    void timeoutAlSubirLanzaNoDisponibleSinCodigo() {
        servidor.expect(requestTo(URL_SUBIR)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        AlmacenamientoNoDisponibleException e = catchThrowableOfType(AlmacenamientoNoDisponibleException.class,
                () -> cliente.subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF));
        assertThat(e.getMessage()).contains("tiempo de espera");
        assertThat(e.getCodigoHttp()).isEmpty();
        sinSecretos(e);
    }

    /** Con el constructor real: el timeout de lectura sí se aplica (servidor local que tarda más que el timeout). */
    @Test
    void elTimeoutConfiguradoCortaLaEspera() throws IOException {
        HttpServer lento = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        lento.createContext("/storage/v1/", intercambio -> {
            try {
                Thread.sleep(3_000);
                intercambio.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                intercambio.close();
            }
        });
        lento.start();
        try {
            String base = "http://127.0.0.1:" + lento.getAddress().getPort();
            SupabaseStorageClient real = new SupabaseStorageClient(RestClient.builder(), propiedades(base, LLAVE, Duration.ofMillis(300)));

            long inicio = System.nanoTime();
            AlmacenamientoNoDisponibleException e = catchThrowableOfType(AlmacenamientoNoDisponibleException.class,
                    () -> real.subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF));

            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
            assertThat(e.getCodigoHttp()).isEmpty();
            sinSecretos(e);
        } finally {
            lento.stop(0);
        }
    }

    @Test
    void errorDeRedLanzaNoDisponible() {
        SupabaseStorageClient real = new SupabaseStorageClient(RestClient.builder(),
                propiedades("http://127.0.0.1:1", LLAVE, Duration.ofSeconds(1)));

        AlmacenamientoNoDisponibleException e = catchThrowableOfType(AlmacenamientoNoDisponibleException.class,
                () -> real.urlFirmada(BUCKET, RUTA, Duration.ofMinutes(5)));
        assertThat(e.getCodigoHttp()).isEmpty();
        sinSecretos(e);
    }

    // --- Validaciones del cliente (no llaman a Storage) ---

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/octet-stream", "image/png", ""})
    void soloAceptaPdf(String tipo) {
        assertThatThrownBy(() -> cliente.subir(BUCKET, RUTA, PDF, tipo))
                .isInstanceOf(ArchivoInvalidoException.class).hasMessage("Solo se pueden subir archivos application/pdf");
        assertThatThrownBy(() -> cliente.subir(BUCKET, RUTA, PDF, null)).isInstanceOf(ArchivoInvalidoException.class);
        servidor.verify();
    }

    @Test
    void elLimiteEsDeCincoMegas() {
        servidor.expect(requestTo(URL_SUBIR)).andRespond(json(HttpStatus.OK, "{}"));
        assertThat(cliente.subir(BUCKET, RUTA, new byte[IAlmacenamientoArchivos.TAMANO_MAXIMO], "application/pdf")).isEqualTo(RUTA);
        servidor.verify();

        assertThatThrownBy(() -> cliente.subir(BUCKET, RUTA, new byte[IAlmacenamientoArchivos.TAMANO_MAXIMO + 1], "application/pdf"))
                .isInstanceOf(ArchivoInvalidoException.class)
                .hasMessage("El archivo pesa 5242881 bytes; el máximo es 5242880 (5 MB)");
        assertThatThrownBy(() -> cliente.subir(BUCKET, RUTA, new byte[0], "application/pdf"))
                .isInstanceOf(ArchivoInvalidoException.class).hasMessage("El archivo está vacío");
        assertThatThrownBy(() -> cliente.subir(BUCKET, RUTA, null, "application/pdf")).isInstanceOf(ArchivoInvalidoException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            /2026/FD-2026-000123.pdf     | La ruta no puede empezar con /
            2026/../secretos.pdf         | La ruta no puede contener ..
            ../FD-2026-000123.pdf        | La ruta no puede contener ..
            2026/..                      | La ruta no puede contener ..
            2026//FD-2026-000123.pdf     | La ruta solo puede tener
            2026/FD-2026-000123.pdf/     | La ruta solo puede tener
            2026\\FD-2026-000123.pdf     | La ruta solo puede tener
            2026/Marta Ríos.pdf          | La ruta solo puede tener
            2026/%2e%2e/x.pdf            | La ruta solo puede tener
            2026/{id}.pdf                | La ruta solo puede tener
            2026/x.pdf?token=1           | La ruta solo puede tener
            .oculto/x.pdf                | La ruta solo puede tener
            """)
    void rechazaRutasPeligrosas(String ruta, String mensaje) {
        assertThatThrownBy(() -> cliente.subir(BUCKET, ruta, PDF, "application/pdf"))
                .isInstanceOf(ArchivoInvalidoException.class).hasMessageStartingWith(mensaje);
        assertThatThrownBy(() -> cliente.urlFirmada(BUCKET, ruta, Duration.ofMinutes(5)))
                .isInstanceOf(ArchivoInvalidoException.class).hasMessageStartingWith(mensaje);
        servidor.verify();
    }

    @Test
    void rechazaRutaVaciaYBucketInvalido() {
        assertThatThrownBy(() -> cliente.subir(BUCKET, " ", PDF, "application/pdf"))
                .isInstanceOf(ArchivoInvalidoException.class).hasMessage("La ruta del archivo es obligatoria");
        for (String bucket : new String[] {null, "", "Comprobantes", "compro/bantes", "../x"}) {
            assertThatThrownBy(() -> cliente.subir(bucket, RUTA, PDF, "application/pdf"))
                    .isInstanceOf(ArchivoInvalidoException.class).hasMessage("Nombre de bucket inválido");
        }
        servidor.verify();
    }

    // --- URL firmada ---

    @Test
    void urlFirmadaHaceElPostQuePideStorageYDevuelveLaUrlCompleta() {
        servidor.expect(requestTo(URL_FIRMAR))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LLAVE))
                .andExpect(header("apikey", LLAVE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"expiresIn\": 300}", JsonCompareMode.STRICT))
                .andRespond(json(HttpStatus.OK,
                        "{\"signedURL\": \"/object/sign/comprobantes/2026/FD-2026-000123.pdf?token=" + TOKEN_FIRMA + "\"}"));

        String url = cliente.urlFirmada(BUCKET, RUTA, Duration.ofMinutes(5));

        assertThat(url).isEqualTo(URL + "/storage/v1/object/sign/comprobantes/2026/FD-2026-000123.pdf?token=" + TOKEN_FIRMA
                + "&download=FD-2026-000123.pdf");
        assertThat(textoDeLosLogs()).contains("URL firmada creada", "vigencia 300 s");
        servidor.verify();
    }

    @Test
    void aceptaUnaSignedUrlQueYaIncluyeStorageV1() {
        servidor.expect(requestTo(URL_FIRMAR)).andRespond(json(HttpStatus.OK,
                "{\"signedURL\": \"/storage/v1/object/sign/comprobantes/2026/FD-2026-000123.pdf?token=" + TOKEN_FIRMA + "\"}"));

        assertThat(cliente.urlFirmada(BUCKET, RUTA, Duration.ofMinutes(5)))
                .startsWith(URL + "/storage/v1/object/sign/comprobantes/").doesNotContain("/storage/v1/storage/v1");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            404 | {"code": "NoSuchKey", "message": "Object not found"}                    | ArchivoNoEncontradoException         | 404
            400 | {"statusCode": "404", "error": "not_found", "message": "Object not found"} | ArchivoNoEncontradoException       | 404
            400 | {"statusCode": "400", "error": "InvalidRequest"}                         | AlmacenamientoRechazadoException     | 400
            401 | {}                                                                       | AlmacenamientoNoConfiguradoException | 401
            403 | {}                                                                       | AlmacenamientoNoConfiguradoException | 403
            500 | {}                                                                       | AlmacenamientoNoDisponibleException  | 500
            503 | {}                                                                       | AlmacenamientoNoDisponibleException  | 503
            200 | {"otro": "campo"}                                                        | AlmacenamientoNoDisponibleException  | 200
            200 | no es json                                                               | AlmacenamientoNoDisponibleException  | 200
            """)
    void cadaRespuestaDeErrorAlFirmarLanzaSuExcepcion(int estado, String cuerpo, String excepcion, int codigoEsperado) {
        servidor.expect(requestTo(URL_FIRMAR)).andRespond(json(HttpStatus.valueOf(estado), cuerpo));

        Throwable e = catchThrowable(() -> cliente.urlFirmada(BUCKET, RUTA, Duration.ofMinutes(5)));

        assertThat(e.getClass().getSimpleName()).isEqualTo(excepcion);
        assertThat(((AlmacenamientoException) e).getCodigoHttp()).hasValue(codigoEsperado);
        sinSecretos(e);
    }

    @Test
    void timeoutAlFirmarLanzaNoDisponible() {
        servidor.expect(requestTo(URL_FIRMAR)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(catchThrowableOfType(AlmacenamientoNoDisponibleException.class,
                () -> cliente.urlFirmada(BUCKET, RUTA, Duration.ofMinutes(5))).getCodigoHttp()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 9, 3601, -60})
    void laVigenciaDebeSerCorta(long segundos) {
        assertThatThrownBy(() -> cliente.urlFirmada(BUCKET, RUTA, Duration.ofSeconds(segundos)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La vigencia de la URL firmada debe estar entre 10 s y 1 h");
        assertThatThrownBy(() -> cliente.urlFirmada(BUCKET, RUTA, null)).isInstanceOf(IllegalArgumentException.class);
        servidor.verify();
    }

    // --- Configuración ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULO", textBlock = """
            ''   | llave | SUPABASE_URL
            url  | ' '   | SUPABASE_SERVICE_ROLE_KEY
            NULO | NULO  | SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY
            """)
    void sinConfiguracionLanzaNoConfiguradoSinLlamarAStorage(String url, String llave, String faltan) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer sinPeticiones = MockRestServiceServer.bindTo(builder).build();
        SupabaseStorageClient sinConfigurar = new SupabaseStorageClient(builder.build(),
                propiedades(url == null ? null : url.equals("url") ? URL : url, llave == null ? null : llave.equals("llave") ? LLAVE : llave,
                        Duration.ofSeconds(10)));
        String mensaje = "No se puede usar Supabase Storage: falta configurar " + faltan + " (ver backend/.env.example)";

        assertThatThrownBy(() -> sinConfigurar.subir(BUCKET, RUTA, PDF, "application/pdf"))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class).hasMessage(mensaje);
        assertThatThrownBy(() -> sinConfigurar.urlFirmada(BUCKET, RUTA, Duration.ofMinutes(5)))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class).hasMessage(mensaje);
        sinPeticiones.verify();
    }

    @Test
    void lasPropiedadesNoMuestranLaLlave() {
        SupabaseProperties conLlave = propiedades(URL, LLAVE, Duration.ofSeconds(10));
        assertThat(conLlave.toString()).doesNotContain(LLAVE).contains("serviceRoleKey=****", URL);
        assertThat(propiedades(URL, "", Duration.ofSeconds(10)).toString()).contains("serviceRoleKey=(vacía)");
    }

    // --- Seguridad: todas las salidas posibles, revisadas juntas ---

    @Test
    void ningunaRespuestaDejaLaLlaveNiLaUrlFirmadaEnLogsNiExcepciones() {
        for (HttpStatus estado : List.of(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.PAYLOAD_TOO_LARGE, HttpStatus.INTERNAL_SERVER_ERROR)) {
            // Un Storage que, para probar, devuelve la llave y el token en el cuerpo del error
            String cuerpo = "{\"signedURL\": \"/object/sign/x?token=" + TOKEN_FIRMA + "\", \"message\": \"" + LLAVE + "\"}";
            servidor.reset();
            servidor.expect(requestTo(URL_SUBIR)).andRespond(json(estado, cuerpo));
            servidor.expect(requestTo(URL_FIRMAR)).andRespond(json(estado, cuerpo));
            capturar(() -> cliente.subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF));
            capturar(() -> cliente.urlFirmada(BUCKET, RUTA, Duration.ofMinutes(5)));
        }
        servidor.reset();
        servidor.expect(requestTo(URL_SUBIR)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        capturar(() -> cliente.subir(BUCKET, RUTA, PDF, IAlmacenamientoArchivos.PDF));
        // Y los logs, en @AfterEach.
    }

    // --- Apoyo ---

    private void capturar(Runnable accion) {
        try {
            accion.run();
        } catch (AlmacenamientoException e) {
            sinSecretos(e);
        }
    }

    /** Ni el mensaje, ni el toString, ni ninguna causa contienen secretos. */
    private void sinSecretos(Throwable e) {
        for (Throwable actual = e; actual != null; actual = actual.getCause()) {
            String texto = actual + " " + actual.getMessage();
            SECRETOS.forEach(secreto -> assertThat(texto).doesNotContain(secreto));
        }
    }

    private String textoDeLosLogs() {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : registros.list) {
            texto.append(evento.getFormattedMessage()).append('\n');
            for (IThrowableProxy error = evento.getThrowableProxy(); error != null; error = error.getCause()) {
                texto.append(error.getClassName()).append(": ").append(error.getMessage()).append('\n');
            }
        }
        return texto.toString();
    }

    private static Logger raiz() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static SupabaseProperties propiedades(String url, String llave, Duration timeout) {
        return new SupabaseProperties(url, llave, BUCKET, Duration.ofMinutes(5), timeout);
    }

    private static ResponseCreator json(HttpStatus estado, String cuerpo) {
        return withStatus(estado).contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    }
}
