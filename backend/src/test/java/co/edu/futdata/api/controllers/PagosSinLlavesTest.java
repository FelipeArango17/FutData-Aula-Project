package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.util.security.JwtValidator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Arranque sin llaves de Wompi (el caso normal en local y en CI): la aplicación arranca y los endpoints de Wompi responden
 * {@code 503 PAGOS_NO_CONFIGURADOS}, sin nombrar variables ni mostrar llaves. Usa los servicios reales (la base no hace falta:
 * la configuración se revisa antes de consultarla).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties", properties = {
        "futdata.wompi.public-key=", "futdata.wompi.private-key=", "futdata.wompi.events-secret=", "futdata.wompi.integrity-secret="})
class PagosSinLlavesTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
    private static final String PAGO = "00000000-0000-0000-0000-0000000000d1";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;

    @BeforeEach
    void preparar() {
        for (var par : List.of(List.of(ADMIN, "ADMINISTRADOR"), List.of(ACUDIENTE, "ACUDIENTE"))) {
            UUID id = (UUID) par.get(0);
            when(decoder.decode("token-" + id)).thenReturn(Jwt.withTokenValue("token-" + id).header("alg", "ES256").subject(id.toString())
                    .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) par.get(1)));
        }
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
    }

    @Test
    void iniciarWompiResponde503ConElCodigo() throws Exception {
        mvc.perform(post("/api/pagos/" + PAGO + "/iniciar-wompi").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + ACUDIENTE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PAGOS_NO_CONFIGURADOS"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("WOMPI_"))));
    }

    @Test
    void conciliarResponde503ConElCodigo() throws Exception {
        mvc.perform(post("/api/pagos/" + PAGO + "/conciliar").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + ADMIN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PAGOS_NO_CONFIGURADOS"));
    }

    @Test
    void elWebhookSinSecretoDeEventosResponde503ConElCodigo() throws Exception {
        mvc.perform(post("/api/pagos/webhook/wompi").contentType(MediaType.APPLICATION_JSON).content("{}").header("X-Event-Checksum", "abc"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PAGOS_NO_CONFIGURADOS"));
    }
}
