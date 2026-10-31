package co.edu.futdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.config.N8nProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Arranque del contexto completo sin base de datos disponible. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class FutDataApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private N8nProperties n8n;

    @Autowired
    private MockMvc mvc;

    @Test
    void elContextoArrancaSinBaseDeDatos() {
        assertThat(contexto.getBean("jdbcTemplate")).isNotNull();
        // Sin JPA: el PPI usa DAO con JDBC.
        assertThat(contexto.containsBean("entityManagerFactory")).isFalse();
    }

    @Test
    void lasPropiedadesDeN8nSeCarganConTimeoutDeCincoSegundosYSinMostrarElToken() {
        assertThat(n8n.baseUrl()).isEqualTo("http://localhost:5678");
        assertThat(n8n.webhookToken()).isEqualTo("token-de-prueba");
        assertThat(n8n.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(n8n.toString()).doesNotContain("token-de-prueba");
    }

    /** Sin el perfil dev la documentación no existe y la ruta exige sesión (ver DocumentacionPerfilDevTest). */
    @Test
    void sinElPerfilDevNoSePublicaLaDocumentacion() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }
}
