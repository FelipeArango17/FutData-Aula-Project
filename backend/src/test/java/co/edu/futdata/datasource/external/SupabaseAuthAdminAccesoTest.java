package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
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
 * {@link SupabaseAuthAdmin#cambiarAcceso} contra un Auth simulado: la petición exacta (PUT, ruta y {@code ban_duration})
 * y la traducción de cada respuesta. Antes usaba PATCH, que Supabase Auth no tiene en esa ruta.
 */
class SupabaseAuthAdminAccesoTest {

    private static final String URL = "https://proyecto-prueba.supabase.co";
    private static final String LLAVE = "llave-service-role-inventada-4d5e6f";
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String RUTA = URL + "/auth/v1/admin/users/" + ID;
    /** Cuerpo de error con algo que no debe terminar en un log ni en una excepción. */
    private static final String CUERPO_SENSIBLE = "{\"msg\":\"detalle-interno-que-no-se-registra\"}";

    private MockRestServiceServer servidor;
    private SupabaseAuthAdmin auth;
    private ListAppender<ILoggingEvent> registros;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        auth = new SupabaseAuthAdmin(builder.build(),
                new SupabaseProperties(URL, LLAVE, "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(10)));
        registros = new ListAppender<>();
        registros.start();
        raiz().addAppender(registros);
        ((Logger) LoggerFactory.getLogger("co.edu.futdata")).setLevel(Level.TRACE);
    }

    @AfterEach
    void sinSecretosEnLogs() {
        raiz().detachAppender(registros);
        assertThat(registros.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain(LLAVE).doesNotContain("detalle-interno-que-no-se-registra"));
    }

    private static Logger raiz() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static void sinSecretos(Throwable t) {
        assertThat(t.getMessage()).doesNotContain(LLAVE).doesNotContain("detalle-interno-que-no-se-registra");
        assertThat(t.getCause()).isNull();
    }

    @Test
    void bloquearEsPutConBanDurationDe876000h() {
        servidor.expect(requestTo(RUTA))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("apikey", LLAVE))
                .andExpect(header("Authorization", "Bearer " + LLAVE))
                .andExpect(content().json("{\"ban_duration\":\"876000h\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\":\"" + ID + "\"}", MediaType.APPLICATION_JSON));

        auth.cambiarAcceso(ID, false);
        servidor.verify();
    }

    @Test
    void restaurarEsPutConBanDurationNone() {
        servidor.expect(requestTo(RUTA))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"ban_duration\":\"none\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\":\"" + ID + "\"}", MediaType.APPLICATION_JSON));

        auth.cambiarAcceso(ID, true);
        servidor.verify();
    }

    @Test
    void el404EsCuentaNoEncontrada() {
        servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error_code\":\"user_not_found\",\"msg\":\"User not found\"}"));

        Throwable t = catchThrowable(() -> auth.cambiarAcceso(ID, false));
        assertThat(t).isInstanceOf(CuentaAuthNoEncontradaException.class);
        assertThat(((AuthException) t).getCodigoHttp()).hasValue(404);
        sinSecretos(t);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 502, 503})
    void llaveLimiteO5xxSonNoDisponibleConElCodigoEnElLog(int codigo) {
        servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.valueOf(codigo))
                .contentType(MediaType.APPLICATION_JSON).body(CUERPO_SENSIBLE));

        Throwable t = catchThrowable(() -> auth.cambiarAcceso(ID, false));
        assertThat(t).isInstanceOf(AuthNoDisponibleException.class);
        assertThat(((AuthException) t).getCodigoHttp()).hasValue(codigo);
        sinSecretos(t);
        assertThat(registros.list).anyMatch(e -> e.getFormattedMessage().contains("respondió " + codigo + " al bloquear el acceso"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 422})
    void un400O422EsErrorDeContratoConLogClaro(int codigo) {
        servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.valueOf(codigo))
                .contentType(MediaType.APPLICATION_JSON).body(CUERPO_SENSIBLE));

        Throwable t = catchThrowable(() -> auth.cambiarAcceso(ID, true));
        assertThat(t).isInstanceOf(AuthPeticionInvalidaException.class);
        assertThat(((AuthException) t).getCodigoHttp()).hasValue(codigo);
        sinSecretos(t);
        assertThat(registros.list).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("error de contrato") && e.getFormattedMessage().contains(String.valueOf(codigo)));
    }

    @Test
    void sinRespuestaEsNoDisponibleSinCodigo() {
        servidor.expect(requestTo(RUTA)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        Throwable t = catchThrowable(() -> auth.cambiarAcceso(ID, false));
        assertThat(t).isInstanceOf(AuthNoDisponibleException.class);
        assertThat(((AuthException) t).getCodigoHttp()).isEmpty();
        sinSecretos(t);
    }

    @Test
    void sinConfiguracionSigueSiendoIllegalStateComoAntes() {
        SupabaseAuthAdmin sinLlave = new SupabaseAuthAdmin(RestClient.builder().build(),
                new SupabaseProperties(URL, " ", "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(10)));

        assertThatThrownBy(() -> sinLlave.cambiarAcceso(ID, false)).isInstanceOf(IllegalStateException.class);
    }
}
