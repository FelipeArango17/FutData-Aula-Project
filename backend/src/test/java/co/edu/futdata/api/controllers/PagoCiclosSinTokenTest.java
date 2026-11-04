package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.ResultadoCiclosDTO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ICobrosCicloService;
import co.edu.futdata.services.facade.IPagoService;
import co.edu.futdata.services.facade.IPagoWompiService;
import co.edu.futdata.util.security.JwtValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Sin N8N_CRON_TOKEN la vía por token queda deshabilitada (y el administrador con JWT sigue entrando). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties", properties = "futdata.cron.token=")
class PagoCiclosSinTokenTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;
    @MockitoBean
    private IPagoService pagos;
    @MockitoBean
    private IPagoWompiService webhook;
    @MockitoBean
    private ICobrosCicloService ciclos;

    @Test
    void sinLaVariableNingunTokenAbreLaRuta() throws Exception {
        for (String valor : List.of("", " ", "cualquiera", "null", "undefined")) {
            mvc.perform(post(PagoCiclosControllerTest.RUTA).header("X-Cron-Token", valor)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(ciclos);
    }

    @Test
    void elAdministradorConJwtSigueEntrando() throws Exception {
        when(decoder.decode("t")).thenReturn(Jwt.withTokenValue("t").header("alg", "ES256").subject(ADMIN.toString())
                .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
        when(usuarios.rolesActivos(ADMIN)).thenReturn(List.of("ADMINISTRADOR"));
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
        when(ciclos.generarCobrosDeCiclo(any(), any())).thenReturn(new ResultadoCiclosDTO(0, 0, 0, 0, 0));

        mvc.perform(post(PagoCiclosControllerTest.RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer t")).andExpect(status().isOk());
        verify(ciclos).generarCobrosDeCiclo(any(LocalDate.class), eq(ADMIN));
    }
}
