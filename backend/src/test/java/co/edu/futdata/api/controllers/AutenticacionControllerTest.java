package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.services.facade.CodigoVerificacionException;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import co.edu.futdata.util.security.JwtValidator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/auth por HTTP: el usuario sale del JWT (sub) y la sesión de su session_id, nunca de un correo en el cuerpo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class AutenticacionControllerTest {

    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID SESION = UUID.fromString("5e55a000-3b4d-4e5f-8a7b-1c2d3e4f5a6b");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ICodigoVerificacionService servicio;

    @MockitoBean
    private ISesionVerificadaDAO sesiones;

    /** El JWT del primer paso: con rol (sale de la base de datos) y con session_id. */
    private static JwtRequestPostProcessor token(String sub) {
        return jwt().jwt(j -> j.subject(sub).claim(JwtValidator.CLAIM_SESION, SESION.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ACUDIENTE"));
    }

    @Test
    void solicitarDevuelve202SinExponerElCodigo() throws Exception {
        when(servicio.solicitar(ID)).thenReturn(new Codigo2FAResponseDTO("a***@pradofc.co", 600, 60_000));

        mvc.perform(post("/api/auth/2fa/codigo").with(token(ID.toString())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correoEnmascarado").value("a***@pradofc.co"))
                .andExpect(jsonPath("$.vigenciaSegundos").value(600))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    void elCorreoDelCuerpoSeIgnoraYSeUsaElDelToken() throws Exception {
        when(servicio.solicitar(any())).thenReturn(new Codigo2FAResponseDTO("a***@pradofc.co", 600, 60_000));

        mvc.perform(post("/api/auth/2fa/codigo").with(token(ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"otra.persona@pradofc.co\"}"))
                .andExpect(status().isAccepted());

        verify(servicio).solicitar(ID);
    }

    @Test
    void reenvioLimitadoDevuelve409ConCampoCodigo() throws Exception {
        when(servicio.reenviar(ID)).thenThrow(new CodigoVerificacionException(
                "DEMASIADO_PRONTO", "Debes esperar antes de solicitar otro código.", 30_000L));

        mvc.perform(post("/api/auth/2fa/reenviar").with(token(ID.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos").isArray())
                .andExpect(jsonPath("$.campos[0]").value("codigo"));
    }

    @Test
    void verificarRecibeSoloElCodigoYMarcaLaSesionDelToken() throws Exception {
        mvc.perform(post("/api/auth/2fa/verificar").with(token(ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"012345\"}"))
                .andExpect(status().isOk());

        verify(servicio).verificar(eq(ID), eq(SESION), eq("012345"));
    }

    @Test
    void logoutCierraLaSesionDelTokenYDevuelve204() throws Exception {
        when(sesiones.estaVerificada(eq(SESION), eq(ID), any())).thenReturn(true);

        mvc.perform(post("/api/auth/logout").with(token(ID.toString())))
                .andExpect(status().isNoContent());

        verify(servicio).cerrarSesion(ID, SESION);
    }

    @Test
    void sinElTokenDelPrimerPasoEs401() throws Exception {
        mvc.perform(post("/api/auth/2fa/codigo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"ana@pradofc.co\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(servicio);
    }

    @Test
    void unTokenSinSessionIdEs401() throws Exception {
        mvc.perform(post("/api/auth/2fa/codigo").with(jwt().jwt(j -> j.subject(ID.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ACUDIENTE"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(servicio);
    }

    @Test
    void unSubQueNoEsUuidEs404() throws Exception {
        mvc.perform(post("/api/auth/2fa/codigo").with(token("no-es-uuid")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(servicio);
    }
}
