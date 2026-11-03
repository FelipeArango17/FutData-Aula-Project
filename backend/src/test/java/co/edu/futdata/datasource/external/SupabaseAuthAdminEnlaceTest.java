package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.futdata.config.SupabaseProperties;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.client.RestClient;

/**
 * Operaciones del enlace para crear la contraseña de SupabaseAuthAdmin contra un Auth simulado (sin red real). Los
 * valores secretos son inventados; se comprueba que no salen en logs ni en excepciones.
 */
class SupabaseAuthAdminEnlaceTest {

    private static final String URL = "https://proyecto-prueba.supabase.co";
    private static final String LLAVE = "llave-service-role-inventada-1a2b3c";
    private static final String TOKEN_HASH = "hash-inventado-0f1e2d3c4b5a";
    private static final String TOKEN_ACCESO = "jwt-inventado-de-la-sesion-9z8y7x";
    private static final String CONTRASENA = "Inventada2026";
    private static final List<String> SECRETOS = List.of(LLAVE, TOKEN_HASH, TOKEN_ACCESO, CONTRASENA);
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private MockRestServiceServer servidor;
    private SupabaseAuthAdmin auth;
    private ListAppender<ILoggingEvent> registros;
    private Level nivelOriginal;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        auth = new SupabaseAuthAdmin(builder.build(),
                new SupabaseProperties(URL, LLAVE, "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(10)));
        registros = new ListAppender<>();
        registros.start();
        Logger logger = (Logger) LoggerFactory.getLogger("co.edu.futdata");
        nivelOriginal = logger.getLevel();
        logger.setLevel(Level.TRACE);
        raiz().addAppender(registros);
    }

    @AfterEach
    void limpiar() {
        raiz().detachAppender(registros);
        ((Logger) LoggerFactory.getLogger("co.edu.futdata")).setLevel(nivelOriginal);
        assertThat(registros.list).allSatisfy(evento ->
                SECRETOS.forEach(s -> assertThat(evento.getFormattedMessage()).doesNotContain(s)));
    }

    private static Logger raiz() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static void sinSecretos(Throwable t) {
        assertThat(t).isNotNull();
        SECRETOS.forEach(s -> assertThat(String.valueOf(t.getMessage())).doesNotContain(s));
        assertThat(t.getCause()).isNull();
    }

    // ---- crearUsuario ----

    @Test
    void crearUsuarioConElCorreoConfirmadoYSinContrasena() {
        servidor.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", LLAVE))
                .andExpect(header("Authorization", "Bearer " + LLAVE))
                .andExpect(content().json("{\"email\":\"luis@pradofc.co\",\"email_confirm\":true}",
                        JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\":\"" + ID + "\",\"email\":\"luis@pradofc.co\",\"aud\":\"authenticated\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(auth.crearUsuario("luis@pradofc.co")).isEqualTo(ID);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":422,\"error_code\":\"email_exists\",\"msg\":\"A user with this email address has already been registered\"}",
            "{\"code\":422,\"msg\":\"A user with this email address has already been registered\"}"})
    void crearUsuarioConCorreoRepetidoLanzaCorreoYaRegistrado(String cuerpo) {
        servidor.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON).body(cuerpo));

        Throwable t = catchThrowable(() -> auth.crearUsuario("luis@pradofc.co"));
        assertThat(t).isInstanceOf(CorreoYaRegistradoException.class);
        assertThat(((AuthException) t).getCodigoHttp()).hasValue(422);
    }

    @Test
    void crearUsuarioCon422SinCorreoRepetidoNoEsCorreoYaRegistrado() {
        servidor.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_code\":\"validation_failed\"}"));

        assertThatThrownBy(() -> auth.crearUsuario("luis@pradofc.co")).isInstanceOf(AuthNoDisponibleException.class);
    }

    // ---- generarTokenRecuperacion ----

    @Test
    void generarTokenDevuelveSoloElHashedTokenNuncaElActionLink() {
        servidor.expect(requestTo(URL + "/auth/v1/admin/generate_link"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + LLAVE))
                .andExpect(content().json("{\"type\":\"recovery\",\"email\":\"luis@pradofc.co\"}",
                        JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\":\"" + ID + "\",\"action_link\":\"https://otro/verify?token=xyz\","
                        + "\"hashed_token\":\"" + TOKEN_HASH + "\",\"verification_type\":\"recovery\"}", MediaType.APPLICATION_JSON));

        assertThat(auth.generarTokenRecuperacion("luis@pradofc.co")).isEqualTo(TOKEN_HASH);
    }

    @Test
    void generarTokenAceptaElHashedTokenDentroDeProperties() {
        servidor.expect(requestTo(URL + "/auth/v1/admin/generate_link"))
                .andRespond(withSuccess("{\"properties\":{\"hashed_token\":\"" + TOKEN_HASH + "\"},\"user\":{\"id\":\"" + ID + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(auth.generarTokenRecuperacion("luis@pradofc.co")).isEqualTo(TOKEN_HASH);
    }

    @Test
    void generarTokenSinHashedTokenEsNoDisponible() {
        servidor.expect(requestTo(URL + "/auth/v1/admin/generate_link"))
                .andRespond(withSuccess("{\"action_link\":\"https://otro\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> auth.generarTokenRecuperacion("luis@pradofc.co"))
                .isInstanceOf(AuthNoDisponibleException.class);
    }

    // ---- verificarTokenRecuperacion ----

    @Test
    void verificarDevuelveElUsuarioYElTokenDeLaSesion() {
        servidor.expect(requestTo(URL + "/auth/v1/verify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", LLAVE))
                .andExpect(content().json("{\"type\":\"recovery\",\"token_hash\":\"" + TOKEN_HASH + "\"}",
                        JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"access_token\":\"" + TOKEN_ACCESO + "\",\"refresh_token\":\"r\",\"token_type\":\"bearer\","
                        + "\"user\":{\"id\":\"" + ID + "\",\"email\":\"luis@pradofc.co\"}}", MediaType.APPLICATION_JSON));

        SesionRecuperacion sesion = auth.verificarTokenRecuperacion(TOKEN_HASH);

        assertThat(sesion.idUsuario()).isEqualTo(ID);
        assertThat(sesion.tokenAcceso()).isEqualTo(TOKEN_ACCESO);
        assertThat(sesion.toString()).doesNotContain(TOKEN_ACCESO);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 410, 422})
    void verificarConTokenRechazadoLanzaTokenInvalido(int codigo) {
        servidor.expect(requestTo(URL + "/auth/v1/verify"))
                .andRespond(withStatus(HttpStatus.valueOf(codigo)).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_code\":\"otp_expired\",\"msg\":\"Email link is invalid or has expired\"}"));

        Throwable t = catchThrowable(() -> auth.verificarTokenRecuperacion(TOKEN_HASH));
        assertThat(t).isInstanceOf(TokenRecuperacionInvalidoException.class);
        sinSecretos(t);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 502, 503})
    void verificarConLlaveRechazadaLimiteOFallaDeSupabaseEsNoDisponible(int codigo) {
        servidor.expect(requestTo(URL + "/auth/v1/verify")).andRespond(withStatus(HttpStatus.valueOf(codigo)));

        Throwable t = catchThrowable(() -> auth.verificarTokenRecuperacion(TOKEN_HASH));
        assertThat(t).isInstanceOf(AuthNoDisponibleException.class);
        assertThat(((AuthException) t).getCodigoHttp()).hasValue(codigo);
        sinSecretos(t);
    }

    @Test
    void verificarSinRespuestaEsNoDisponible() {
        servidor.expect(requestTo(URL + "/auth/v1/verify")).andRespond(withException(new SocketTimeoutException("Read timed out")));

        Throwable t = catchThrowable(() -> auth.verificarTokenRecuperacion(TOKEN_HASH));
        assertThat(t).isInstanceOf(AuthNoDisponibleException.class);
        assertThat(((AuthException) t).getCodigoHttp()).isEmpty();
        sinSecretos(t);
    }

    @Test
    void verificarSinTokenNoLlamaASupabase() {
        assertThatThrownBy(() -> auth.verificarTokenRecuperacion(" ")).isInstanceOf(TokenRecuperacionInvalidoException.class);
        servidor.verify();
    }

    @Test
    void verificarSinSesionEnLaRespuestaEsNoDisponible() {
        servidor.expect(requestTo(URL + "/auth/v1/verify"))
                .andRespond(withSuccess("{\"user\":{\"id\":\"" + ID + "\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> auth.verificarTokenRecuperacion(TOKEN_HASH)).isInstanceOf(AuthNoDisponibleException.class);
    }

    // ---- fijarContrasena ----

    @Test
    void fijarContrasenaPorIdConLaApiAdmin() {
        servidor.expect(requestTo(URL + "/auth/v1/admin/users/" + ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer " + LLAVE))
                .andExpect(content().json("{\"password\":\"" + CONTRASENA + "\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\":\"" + ID + "\"}", MediaType.APPLICATION_JSON));

        auth.fijarContrasena(ID, CONTRASENA);
        servidor.verify();
    }

    @Test
    void fijarContrasenaRechazadaEsNoDisponibleSinRepetirla() {
        servidor.expect(requestTo(URL + "/auth/v1/admin/users/" + ID))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_code\":\"weak_password\"}"));

        Throwable t = catchThrowable(() -> auth.fijarContrasena(ID, CONTRASENA));
        assertThat(t).isInstanceOf(AuthNoDisponibleException.class);
        sinSecretos(t);
    }

    // ---- cerrarSesion ----

    @Test
    void cerrarSesionSoloLaDeEseToken() {
        servidor.expect(requestTo(URL + "/auth/v1/logout?scope=local"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", LLAVE))
                .andExpect(header("Authorization", "Bearer " + TOKEN_ACCESO))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        auth.cerrarSesion(TOKEN_ACCESO);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void cerrarUnaSesionQueYaNoExisteNoFalla(int codigo) {
        servidor.expect(requestTo(URL + "/auth/v1/logout?scope=local")).andRespond(withStatus(HttpStatus.valueOf(codigo)));

        assertThatCode(() -> auth.cerrarSesion(TOKEN_ACCESO)).doesNotThrowAnyException();
    }

    @Test
    void cerrarSesionConSupabaseCaidoEsNoDisponible() {
        servidor.expect(requestTo(URL + "/auth/v1/logout?scope=local")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> auth.cerrarSesion(TOKEN_ACCESO)).isInstanceOf(AuthNoDisponibleException.class);
    }

    // ---- Configuración ----

    @Test
    void sinConfiguracionNoLlamaYEsNoDisponible() {
        SupabaseAuthAdmin sinLlave = new SupabaseAuthAdmin(RestClient.builder().build(),
                new SupabaseProperties(URL, "", "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(10)));

        assertThatThrownBy(() -> sinLlave.crearUsuario("a@b.co")).isInstanceOf(AuthNoDisponibleException.class);
        assertThatThrownBy(() -> sinLlave.generarTokenRecuperacion("a@b.co")).isInstanceOf(AuthNoDisponibleException.class);
        assertThatThrownBy(() -> sinLlave.verificarTokenRecuperacion(TOKEN_HASH)).isInstanceOf(AuthNoDisponibleException.class);
        assertThatThrownBy(() -> sinLlave.fijarContrasena(ID, CONTRASENA)).isInstanceOf(AuthNoDisponibleException.class);
        assertThatThrownBy(() -> sinLlave.cerrarSesion(TOKEN_ACCESO)).isInstanceOf(AuthNoDisponibleException.class);
    }
}
