package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.config.N8nProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Prueba manual contra el n8n local ({@code n8n/}, con Docker). <b>Desactivada por defecto</b>: no corre en
 * {@code ./mvnw verify} ni en el CI. <b>Envía correos reales</b> a {@code FUTDATA_N8N_CORREO_PRUEBA}.
 *
 * <pre>
 * FUTDATA_PRUEBA_N8N_REAL=true N8N_BASE_URL=http://localhost:5678 N8N_WEBHOOK_TOKEN=... \
 * FUTDATA_N8N_CORREO_PRUEBA=tu-correo@ejemplo.com ./mvnw test -Dtest=N8nClientContraN8nLocalTest
 * </pre>
 *
 * Ver {@code backend/README.md}, sección "Notificaciones (n8n)".
 */
@EnabledIfEnvironmentVariable(named = "FUTDATA_PRUEBA_N8N_REAL", matches = "true")
class N8nClientContraN8nLocalTest {

    private String correo;
    private N8nClient cliente;

    @BeforeEach
    void preparar() {
        correo = variable("FUTDATA_N8N_CORREO_PRUEBA");
        cliente = cliente(variable("N8N_WEBHOOK_TOKEN"));
    }

    @Test
    void codigo2faResponde202YLlegaUnCorreo() {
        assertThatCode(() -> cliente.notificar(INotificador.CODIGO_2FA,
                Map.of("correo", correo, "nombre", "Prueba backend", "codigo", "123456", "vigenciaMinutos", 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void cambioEntrenamientoResponde202YLlegaUnCorreo() {
        assertThatCode(() -> cliente.notificar(INotificador.CAMBIO_ENTRENAMIENTO, Map.of(
                "tipoCambio", "CREADO",
                "entrenamiento", Map.of("categoria", "Sub-12", "fecha", "2026-11-04", "horaInicio", "16:00",
                        "lugar", "Cancha 2", "entrenador", "Prueba backend"),
                "destinatarios", List.of(Map.of("correo", correo, "nombre", "Prueba backend")))))
                .doesNotThrowAnyException();
    }

    /** No envía correos. */
    @Test
    void eventoNoSoportadoResponde400() {
        assertThatThrownBy(() -> cliente.notificar("EVENTO_INEXISTENTE", Map.of("x", 1)))
                .isInstanceOf(NotificacionRechazadaException.class);
    }

    /** No envía correos. */
    @Test
    void tokenIncorrectoResponde401() {
        assertThatThrownBy(() -> cliente("token-incorrecto").notificar(INotificador.CODIGO_2FA,
                Map.of("correo", correo, "nombre", "Prueba", "codigo", "123456", "vigenciaMinutos", 10)))
                .isInstanceOf(NotificadorMalConfiguradoException.class);
    }

    private static N8nClient cliente(String token) {
        N8nProperties propiedades = new N8nProperties(variable("N8N_BASE_URL"), token, Duration.ofSeconds(5));
        return new N8nClient(RestClient.builder(), propiedades, new ObjectMapper());
    }

    private static String variable(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Para la prueba contra n8n local falta la variable de entorno " + nombre);
        }
        return valor;
    }
}
