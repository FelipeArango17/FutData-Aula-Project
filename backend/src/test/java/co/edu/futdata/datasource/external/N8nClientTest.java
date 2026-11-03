package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import co.edu.futdata.config.N8nProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/** N8nClient contra un n8n simulado (MockRestServiceServer o un servidor en 127.0.0.1): sin red real. */
class N8nClientTest {

    private static final String BASE_URL = "http://n8n.prueba:5678";
    private static final String URL_WEBHOOK = BASE_URL + "/webhook/futdata/notificaciones";
    private static final String TOKEN = "token-secreto-de-prueba-9f8e7d";
    private static final String CODIGO = "123456";
    private static final String CORREO = "jugador@ejemplo.com";
    private static final String CORREO_ACUDIENTE = "acudiente@ejemplo.com";
    private static final String NOMBRE_DESTINATARIO = "Marta Gómez";
    /** Lo que nunca debe salir en un log ni en una excepción. */
    private static final String ENLACE = "https://example.test/crear-contrasena?token=PRUEBA-enlace-7c1e";
    private static final List<String> SECRETOS = List.of(CODIGO, TOKEN, CORREO, CORREO_ACUDIENTE, NOMBRE_DESTINATARIO,
            ENLACE, "token=PRUEBA");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> LOGGERS_EN_DETALLE = List.of("co.edu.futdata", "org.springframework.web", "org.springframework.http");

    private MockRestServiceServer servidor;
    private N8nClient cliente;
    private ListAppender<ILoggingEvent> registros;
    private final Map<String, Level> nivelesOriginales = new HashMap<>();

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new N8nClient(builder.build(), propiedades(BASE_URL, TOKEN, Duration.ofSeconds(5)), JSON);

        // Captura todos los logs, con los de este cliente y los del cliente HTTP de Spring en el nivel más detallado.
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
        SECRETOS.forEach(secreto -> assertThat(todo).as("los logs no deben contener datos sensibles").doesNotContain(secreto));
    }

    // --- Petición ---

    @Test
    void codigo2faHaceElPostQuePideElContrato() {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(header(N8nClient.CABECERA_TOKEN, TOKEN))
                .andExpect(content().json("""
                        {"evento": "CODIGO_2FA",
                         "datos": {"correo": "jugador@ejemplo.com", "nombre": "Ana", "codigo": "123456", "vigenciaMinutos": 10}}
                        """, JsonCompareMode.STRICT))
                .andRespond(json(HttpStatus.ACCEPTED, "{\"aceptado\": true}"));

        assertThatCode(() -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa())).doesNotThrowAnyException();
        servidor.verify();
        assertThat(textoDeLosLogs()).contains("CODIGO_2FA").contains("HTTP 202").containsPattern("\\d+ ms");
    }

    /** Un n8n con el workflow anterior (síncrono) responde 200: se sigue aceptando, sin mirar el cuerpo. */
    @Test
    void codigo2faAceptaUn200SinElCampoEnviado() {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(json(HttpStatus.OK, "{}"));

        assertThatCode(() -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa())).doesNotThrowAnyException();
        servidor.verify();
        assertThat(textoDeLosLogs()).contains("CODIGO_2FA").contains("HTTP 200");
    }

    @Test
    void elCodigoSeEnviaComoTextoConservandoLosCeros() {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andExpect(content().json("{\"datos\": {\"codigo\": \"012345\"}}"))
                .andRespond(json(HttpStatus.ACCEPTED, "{\"aceptado\": true}"));

        Map<String, Object> datos = new HashMap<>(datos2fa());
        datos.put("codigo", "012345");
        cliente.notificar(INotificador.CODIGO_2FA, datos);
        servidor.verify();
    }

    @Test
    void quitaLaBarraFinalDeLaUrlBase() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer otro = MockRestServiceServer.bindTo(builder).build();
        otro.expect(requestTo(URL_WEBHOOK)).andRespond(json(HttpStatus.ACCEPTED, "{\"aceptado\": true}"));

        new N8nClient(builder.build(), propiedades(BASE_URL + "/", TOKEN, Duration.ofSeconds(5)), JSON)
                .notificar(INotificador.CODIGO_2FA, datos2fa());
        otro.verify();
    }

    // --- Respuestas ---

    @Test
    void respuesta202RetornaYRegistraSoloElNumeroDeDestinatarios() {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andExpect(content().json("{\"evento\": \"CAMBIO_ENTRENAMIENTO\"}"))
                .andRespond(json(HttpStatus.ACCEPTED, "{\"aceptado\": true, \"destinatarios\": 2}"));

        assertThatCode(() -> cliente.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosEntrenamiento()))
                .doesNotThrowAnyException();
        assertThat(textoDeLosLogs()).contains("CAMBIO_ENTRENAMIENTO").contains("HTTP 202").contains("2 destinatarios");
    }

    @Test
    void respuesta400LanzaRechazadaConElDetalleDeN8n() {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(json(HttpStatus.BAD_REQUEST,
                "{\"aceptado\": false, \"error\": \"Faltan campos o tienen un formato inválido\","
                        + " \"detalle\": \"destinatarios debe tener entre 1 y 200 elementos\"}"));

        NotificacionRechazadaException e = catchThrowableOfType(NotificacionRechazadaException.class,
                () -> cliente.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosEntrenamiento()));

        assertThat(e.getDetalle()).isEqualTo("destinatarios debe tener entre 1 y 200 elementos");
        assertThat(e.getMessage()).contains("CAMBIO_ENTRENAMIENTO").contains("400").contains(e.getDetalle());
        assertThat(e.getEvento()).isEqualTo(INotificador.CAMBIO_ENTRENAMIENTO);
        assertThat(e.getCodigoHttp()).hasValue(400);
        sinSecretos(e);
    }

    @Test
    void respuesta400SinDetalleUsaElCampoError() {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andRespond(json(HttpStatus.BAD_REQUEST, "{\"enviado\": false, \"error\": \"Evento no soportado\"}"));

        NotificacionRechazadaException e = catchThrowableOfType(NotificacionRechazadaException.class,
                () -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa()));
        assertThat(e.getDetalle()).isEqualTo("Evento no soportado");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "El correo jugador@ejemplo.com es inválido",
            "El código 123456 no es válido",
            "nombre Marta Gómez demasiado largo"})
    void descartaUnDetalleConDatosSensibles(String detalle) {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andRespond(json(HttpStatus.BAD_REQUEST, "{\"aceptado\": false, \"detalle\": \"" + detalle + "\"}"));

        NotificacionRechazadaException e = catchThrowableOfType(NotificacionRechazadaException.class,
                () -> cliente.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosEntrenamiento()));
        assertThat(e.getDetalle()).isNull();
        sinSecretos(e);
    }

    /** tipoCambio y evento tienen valores fijos y no sensibles: que aparezcan no descarta el detalle. */
    @ParameterizedTest
    @ValueSource(strings = {
            "tipoCambio debe ser CREADO, MODIFICADO o CANCELADO",
            "El evento CAMBIO_ENTRENAMIENTO no admite el tipo MODIFICADO sin anterior"})
    void conservaElDetalleSiSoloTraeValoresDeEventoOTipoCambio(String detalle) {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andRespond(json(HttpStatus.BAD_REQUEST, "{\"aceptado\": false, \"detalle\": \"" + detalle + "\"}"));

        NotificacionRechazadaException e = catchThrowableOfType(NotificacionRechazadaException.class,
                () -> cliente.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosEntrenamiento()));
        assertThat(e.getDetalle()).isEqualTo(detalle);
        assertThat(e.getMessage()).contains(detalle);
    }

    @Test
    void descartaElDetalleSiAdemasDelTipoCambioTraeOtroValorDeLosDatos() {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(json(HttpStatus.BAD_REQUEST,
                "{\"aceptado\": false, \"detalle\": \"MODIFICADO no aplica a Marta Gómez\"}"));

        NotificacionRechazadaException e = catchThrowableOfType(NotificacionRechazadaException.class,
                () -> cliente.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosEntrenamiento()));
        assertThat(e.getDetalle()).isNull();
        sinSecretos(e);
    }

    @Test
    void respuesta400SinCuerpoJsonLanzaRechazadaSinDetalle() {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("no es json"));

        NotificacionRechazadaException e = catchThrowableOfType(NotificacionRechazadaException.class,
                () -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa()));
        assertThat(e.getDetalle()).isNull();
    }

    @Test
    void respuesta401LanzaMalConfigurado() {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andRespond(json(HttpStatus.UNAUTHORIZED, "{\"enviado\": false, \"error\": \"Token inválido\"}"));

        NotificadorMalConfiguradoException e = catchThrowableOfType(NotificadorMalConfiguradoException.class,
                () -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa()));
        assertThat(e.getMessage()).contains("N8N_WEBHOOK_TOKEN").contains("401");
        assertThat(e.getCodigoHttp()).hasValue(401);
        sinSecretos(e);
    }

    @Test
    void respuesta502LanzaNoEnviada() {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andRespond(json(HttpStatus.BAD_GATEWAY, "{\"enviado\": false, \"error\": \"No se pudo enviar el correo\"}"));

        NotificacionNoEnviadaException e = catchThrowableOfType(NotificacionNoEnviadaException.class,
                () -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa()));
        assertThat(e.getMessage()).contains("CODIGO_2FA").contains("502");
        assertThat(e.getCodigoHttp()).hasValue(502);
        sinSecretos(e);
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 302, 404, 500, 503})
    void otroCodigoLanzaNoEnviada(int codigo) {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(withStatus(HttpStatus.valueOf(codigo)));

        NotificacionNoEnviadaException e = catchThrowableOfType(NotificacionNoEnviadaException.class,
                () -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa()));
        assertThat(e.getCodigoHttp()).hasValue(codigo);
    }

    @Test
    void timeoutLanzaNoEnviadaSinCodigoHttp() {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        NotificacionNoEnviadaException e = catchThrowableOfType(NotificacionNoEnviadaException.class,
                () -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa()));
        assertThat(e.getMessage()).contains("tiempo de espera");
        assertThat(e.getCodigoHttp()).isEmpty();
        sinSecretos(e);
    }

    // --- ENLACE_CONTRASENA: síncrono, con su propio tiempo de espera ---

    @Test
    void enlaceContrasenaHaceElPostYAceptaEl200() {
        servidor.expect(requestTo(URL_WEBHOOK))
                .andExpect(content().json("{\"evento\":\"ENLACE_CONTRASENA\",\"datos\":{\"plantilla\":\"BIENVENIDA_ENTRENADOR\","
                        + "\"correo\":\"" + CORREO + "\",\"nombre\":\"Ana\",\"enlace\":\"" + ENLACE + "\",\"vigenciaHoras\":1}}",
                        JsonCompareMode.STRICT))
                .andRespond(json(HttpStatus.OK, "{\"enviado\":true}"));

        cliente.notificar(INotificador.ENLACE_CONTRASENA, datosEnlace());
        servidor.verify();
    }

    @Test
    void enlaceContrasenaCon502LanzaNoEnviadaSinElEnlace() {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(json(HttpStatus.BAD_GATEWAY,
                "{\"enviado\":false,\"error\":\"No se pudo enviar el correo\"}"));

        NotificacionNoEnviadaException e = catchThrowableOfType(NotificacionNoEnviadaException.class,
                () -> cliente.notificar(INotificador.ENLACE_CONTRASENA, datosEnlace()));
        assertThat(e.getCodigoHttp()).hasValue(502);
        sinSecretos(e);
    }

    @Test
    void enlaceContrasenaCon400NoRepiteElEnlaceDelDetalle() {
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(json(HttpStatus.BAD_REQUEST,
                "{\"enviado\":false,\"detalle\":\"enlace inválido: " + ENLACE + "\"}"));

        NotificacionRechazadaException e = catchThrowableOfType(NotificacionRechazadaException.class,
                () -> cliente.notificar(INotificador.ENLACE_CONTRASENA, datosEnlace()));
        sinSecretos(e);
    }

    /**
     * Con el constructor real y un n8n que tarda 1 s: con 300 ms para los demás eventos, CODIGO_2FA se corta, pero
     * ENLACE_CONTRASENA usa su propio tiempo (3 s aquí; 15 s por defecto) y recibe el 200.
     */
    @Test
    void enlaceContrasenaUsaSuPropioTiempoDeEsperaYLosDemasNo() throws IOException {
        HttpServer lento = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        lento.createContext(N8nClient.RUTA_WEBHOOK, intercambio -> {
            try {
                Thread.sleep(1_000);
                byte[] cuerpo = "{\"enviado\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                intercambio.close();
            }
        });
        lento.start();
        try {
            String base = "http://127.0.0.1:" + lento.getAddress().getPort();
            N8nClient real = new N8nClient(RestClient.builder(),
                    new N8nProperties(base, TOKEN, Duration.ofMillis(300), Duration.ofSeconds(3)), JSON);

            assertThatThrownBy(() -> real.notificar(INotificador.CODIGO_2FA, datos2fa()))
                    .isInstanceOf(NotificacionNoEnviadaException.class);
            assertThatCode(() -> real.notificar(INotificador.ENLACE_CONTRASENA, datosEnlace())).doesNotThrowAnyException();

            // Y es configurable: con 300 ms también para el enlace, se corta
            N8nClient corto = new N8nClient(RestClient.builder(),
                    new N8nProperties(base, TOKEN, Duration.ofMillis(300), Duration.ofMillis(300)), JSON);
            assertThatThrownBy(() -> corto.notificar(INotificador.ENLACE_CONTRASENA, datosEnlace()))
                    .isInstanceOf(NotificacionNoEnviadaException.class);
        } finally {
            lento.stop(0);
        }
    }

    @Test
    void elTiempoDelEnlaceEsDe15SegundosPorDefecto() {
        assertThat(new N8nProperties(BASE_URL, TOKEN, Duration.ofSeconds(5)).timeoutEnlace()).isEqualTo(Duration.ofSeconds(15));
    }

    /** Con el constructor real: el timeout de lectura sí se aplica (servidor local que tarda más que el timeout). */
    @Test
    void elTimeoutDeLecturaConfiguradoCortaLaEspera() throws IOException {
        HttpServer lento = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        lento.createContext(N8nClient.RUTA_WEBHOOK, intercambio -> {
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
            N8nClient real = new N8nClient(RestClient.builder(), propiedades(base, TOKEN, Duration.ofMillis(300)), JSON);

            long inicio = System.nanoTime();
            NotificacionNoEnviadaException e = catchThrowableOfType(NotificacionNoEnviadaException.class,
                    () -> real.notificar(INotificador.CODIGO_2FA, datos2fa()));

            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
            assertThat(e.getCodigoHttp()).isEmpty();
            sinSecretos(e);
        } finally {
            lento.stop(0);
        }
    }

    @Test
    void errorDeRedLanzaNoEnviada() {
        // Puerto 1 en 127.0.0.1: conexión rechazada al instante.
        N8nClient real = new N8nClient(RestClient.builder(), propiedades("http://127.0.0.1:1", TOKEN, Duration.ofSeconds(1)), JSON);

        NotificacionNoEnviadaException e = catchThrowableOfType(NotificacionNoEnviadaException.class,
                () -> real.notificar(INotificador.CODIGO_2FA, datos2fa()));
        assertThat(e.getCodigoHttp()).isEmpty();
        sinSecretos(e);
    }

    // --- Configuración y argumentos ---

    @Test
    void sinUrlBaseLanzaMalConfiguradoSinLlamarAN8n() {
        N8nClient sinUrl = clienteSinRed(propiedades("", TOKEN, Duration.ofSeconds(5)));

        assertThatThrownBy(() -> sinUrl.notificar(INotificador.CODIGO_2FA, datos2fa()))
                .isInstanceOf(NotificadorMalConfiguradoException.class)
                .hasMessage("No se puede enviar la notificación CODIGO_2FA: falta configurar N8N_BASE_URL "
                        + "(ver backend/.env.example)");
    }

    @Test
    void sinTokenLanzaMalConfiguradoSinLlamarAN8n() {
        N8nClient sinToken = clienteSinRed(propiedades(BASE_URL, null, Duration.ofSeconds(5)));

        NotificadorMalConfiguradoException e = catchThrowableOfType(NotificadorMalConfiguradoException.class,
                () -> sinToken.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosEntrenamiento()));
        assertThat(e.getMessage()).contains("falta configurar N8N_WEBHOOK_TOKEN");
        assertThat(e.getCodigoHttp()).isEmpty();
    }

    @Test
    void sinUrlNiTokenNombraLasDos() {
        N8nClient sinNada = clienteSinRed(propiedades(" ", " ", Duration.ofSeconds(5)));

        assertThatThrownBy(() -> sinNada.notificar(INotificador.CODIGO_2FA, datos2fa()))
                .isInstanceOf(NotificadorMalConfiguradoException.class)
                .hasMessageContaining("N8N_BASE_URL y N8N_WEBHOOK_TOKEN");
    }

    @Test
    void eventoODatosVaciosSonUnErrorDeProgramacion() {
        assertThatThrownBy(() -> cliente.notificar(" ", datos2fa())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cliente.notificar(INotificador.CODIGO_2FA, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cliente.notificar(INotificador.CODIGO_2FA, null)).isInstanceOf(IllegalArgumentException.class);
        servidor.verify();
    }

    // --- Seguridad: todas las salidas posibles, revisadas juntas ---

    @Test
    void ningunaRespuestaDejaDatosSensiblesEnLogsNiExcepciones() {
        List<Throwable> excepciones = new ArrayList<>();
        for (HttpStatus estado : List.of(HttpStatus.OK, HttpStatus.ACCEPTED, HttpStatus.BAD_REQUEST,
                HttpStatus.UNAUTHORIZED, HttpStatus.BAD_GATEWAY, HttpStatus.INTERNAL_SERVER_ERROR)) {
            servidor.reset();
            servidor.expect(requestTo(URL_WEBHOOK)).andRespond(json(estado,
                    "{\"error\": \"Faltan campos\", \"detalle\": \"correo jugador@ejemplo.com, código 123456\"}"));
            capturar(excepciones, () -> cliente.notificar(INotificador.CODIGO_2FA, datos2fa()));
        }
        servidor.reset();
        servidor.expect(requestTo(URL_WEBHOOK)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        capturar(excepciones, () -> cliente.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosEntrenamiento()));
        capturar(excepciones, () -> clienteSinRed(propiedades("", "", Duration.ofSeconds(5)))
                .notificar(INotificador.CODIGO_2FA, datos2fa()));

        assertThat(excepciones).hasSize(6);
        excepciones.forEach(this::sinSecretos);
        // Y los logs, en @AfterEach.
    }

    // --- Apoyo ---

    private static void capturar(List<Throwable> excepciones, Runnable accion) {
        try {
            accion.run();
        } catch (NotificacionException e) {
            excepciones.add(e);
        }
    }

    /** Ni el mensaje, ni el toString, ni ninguna causa contienen datos sensibles. */
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

    /** Un cliente cuyo RestClient falla la prueba si llega a hacer una petición. */
    private static N8nClient clienteSinRed(N8nProperties propiedades) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        return new N8nClient(builder.build(), propiedades, JSON);
    }

    private static N8nProperties propiedades(String baseUrl, String token, Duration timeout) {
        return new N8nProperties(baseUrl, token, timeout);
    }

    private static ResponseCreator json(HttpStatus estado, String cuerpo) {
        return withStatus(estado).contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    }

    private static Map<String, Object> datosEnlace() {
        return Map.of("plantilla", "BIENVENIDA_ENTRENADOR", "correo", CORREO, "nombre", "Ana", "enlace", ENLACE,
                "vigenciaHoras", 1);
    }

    private static Map<String, Object> datos2fa() {
        return Map.of("correo", CORREO, "nombre", "Ana", "codigo", CODIGO, "vigenciaMinutos", 10);
    }

    private static Map<String, Object> datosEntrenamiento() {
        return Map.of(
                "tipoCambio", "MODIFICADO",
                "entrenamiento", Map.of("categoria", "Sub-12", "fecha", "2026-11-04", "horaInicio", "16:00",
                        "lugar", "Cancha 2", "entrenador", "Carlos Pérez"),
                "destinatarios", List.of(
                        Map.of("correo", CORREO_ACUDIENTE, "nombre", NOMBRE_DESTINATARIO),
                        Map.of("correo", CORREO, "nombre", "Juan Gómez")));
    }
}
