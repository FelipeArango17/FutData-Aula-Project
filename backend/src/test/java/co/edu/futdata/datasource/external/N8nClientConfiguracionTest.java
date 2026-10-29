package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Perfil local sin N8N_BASE_URL ni N8N_WEBHOOK_TOKEN: la aplicación arranca y notificar() lo explica. */
@SpringBootTest
@TestPropertySource(locations = "classpath:pruebas.properties",
        properties = {"futdata.n8n.base-url=", "futdata.n8n.webhook-token="})
class N8nClientConfiguracionTest {

    @Autowired
    private INotificador notificador;

    @Test
    void elContextoArrancaYNotificarLanzaMalConfigurado() {
        assertThat(notificador).isInstanceOf(N8nClient.class);

        assertThatThrownBy(() -> notificador.notificar(INotificador.CODIGO_2FA, Map.of("correo", "x")))
                .isInstanceOf(NotificadorMalConfiguradoException.class)
                .hasMessageContaining("falta configurar N8N_BASE_URL y N8N_WEBHOOK_TOKEN");
    }
}
