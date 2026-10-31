package co.edu.futdata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.util.security.RutasSin2FA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Con el perfil dev, y solo con él, la documentación OpenAPI se publica sin token. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(RutasSin2FA.PERFIL_DOCUMENTACION)
@TestPropertySource(locations = "classpath:pruebas.properties")
class DocumentacionPerfilDevTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void laDocumentacionOpenApiIncluyeElEndpointDeSalud() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FutData API"))
                .andExpect(jsonPath("$.paths['/api/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists());
    }

    @Test
    void swaggerUiEstaEnSwaggerUiHtml() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
