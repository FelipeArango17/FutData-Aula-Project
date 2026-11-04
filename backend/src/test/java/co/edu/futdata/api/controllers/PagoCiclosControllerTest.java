package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;

/** POST /api/pagos/generar-ciclos con la cadena de seguridad completa: JWT de administrador o X-Cron-Token. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties", properties = "futdata.cron.token=token-cron-de-prueba-0123456789")
class PagoCiclosControllerTest {

    static final String TOKEN = "token-cron-de-prueba-0123456789";
    static final String RUTA = "/api/pagos/generar-ciclos";
    static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
    static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");

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

    @BeforeEach
    void preparar() {
        for (var par : List.of(List.of(ADMIN, "ADMINISTRADOR"), List.of(TESORERO, "TESORERO"), List.of(ACUDIENTE, "ACUDIENTE"))) {
            UUID id = (UUID) par.get(0);
            when(decoder.decode("token-" + id)).thenReturn(Jwt.withTokenValue("token-" + id).header("alg", "ES256").subject(id.toString())
                    .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) par.get(1)));
        }
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
        when(ciclos.generarCobrosDeCiclo(any(), any())).thenReturn(new ResultadoCiclosDTO(5, 2, 4, 1, 0));
    }

    private ResultActions como(UUID usuario) throws Exception {
        return mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    @Test
    void elAdministradorConJwtGenera() throws Exception {
        como(ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.creadas").value(5))
                .andExpect(jsonPath("$.yaExistentes").value(2))
                .andExpect(jsonPath("$.avisosEnviados").value(4))
                .andExpect(jsonPath("$.avisosFallidos").value(1))
                .andExpect(jsonPath("$.deportistasConError").value(0));
        verify(ciclos).generarCobrosDeCiclo(any(LocalDate.class), eq(ADMIN));
    }

    @Test
    void elTokenDeCronValidoGeneraSinJwtYSinUsuario() throws Exception {
        mvc.perform(post(RUTA).header("X-Cron-Token", TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.creadas").value(5));
        verify(ciclos).generarCobrosDeCiclo(any(LocalDate.class), isNull());
    }

    @Test
    void unTokenIncorrectoEsRechazado() throws Exception {
        mvc.perform(post(RUTA).header("X-Cron-Token", TOKEN + "x")).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).header("X-Cron-Token", "")).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).header("X-Cron-Token", TOKEN.toUpperCase())).andExpect(status().isUnauthorized());
        verifyNoInteractions(ciclos);
    }

    @Test
    void sinJwtNiTokenEsRechazado() throws Exception {
        mvc.perform(post(RUTA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(ciclos);
    }

    @Test
    void otrosRolesNoPuedenAunqueTenganJwtValido() throws Exception {
        como(TESORERO).andExpect(status().isForbidden());
        como(ACUDIENTE).andExpect(status().isForbidden());
        verifyNoInteractions(ciclos);
    }

    @Test
    void conJwtDeOtroRolManda_elJwtYElTokenSeIgnora() throws Exception {
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + TESORERO).header("X-Cron-Token", TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ciclos);
    }

    @Test
    void elTokenDeCronNoAbreNingunOtroEndpoint() throws Exception {
        mvc.perform(get("/api/pagos").header("X-Cron-Token", TOKEN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pagos/generar-mensualidades").header("X-Cron-Token", TOKEN).contentType("application/json").content("{\"periodo\":\"2026-12\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").header("X-Cron-Token", TOKEN)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header("X-Cron-Token", TOKEN)).andExpect(status().is4xxClientError()); // GET: no es la ruta del token
        verify(pagos, never()).generarMensualidades(any(), any());
        verifyNoInteractions(ciclos);
    }
}
