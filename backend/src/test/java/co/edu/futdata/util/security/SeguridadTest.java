package co.edu.futdata.util.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.persistence.dao.ICodigoVerificacionDAO;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.IEntrenadorService;
import co.edu.futdata.services.facade.IUsuarioService;
import co.edu.futdata.services.facade.ISaludService;
import co.edu.futdata.api.dto.SaludResponseDTO;
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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * La cadena de seguridad completa por HTTP: el token pasa por el decodificador (simulado: la firma se prueba en
 * {@link JwtValidatorTest}), los roles salen de {@link IUsuarioDAO} (simulado) a través del {@link JwtValidator} real,
 * y {@code @PreAuthorize} decide. Revisa los {@code 401} y {@code 403} con el formato de errores de la API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class SeguridadTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
    private static final UUID INACTIVO = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;

    @MockitoBean
    private IUsuarioDAO usuarios;

    @MockitoBean
    private ICodigoVerificacionDAO codigosPersistencia;

    @MockitoBean
    private IEntrenadorService entrenadores;

    @MockitoBean
    private ISaludService salud;

    @MockitoBean
    private IUsuarioService usuariosServicio;

    @MockitoBean
    private IDeportistaService deportistas;

    @MockitoBean
    private ICodigoVerificacionService codigos;

    @BeforeEach
    void prepararTokens() {
        for (UUID id : List.of(ADMIN, ENTRENADOR, ACUDIENTE, INACTIVO)) {
            when(decoder.decode("token-" + id)).thenReturn(Jwt.withTokenValue("token-" + id).header("alg", "ES256")
                    .subject(id.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
        }
        when(decoder.decode("token-sub-raro")).thenReturn(Jwt.withTokenValue("token-sub-raro").header("alg", "ES256")
                .subject("no-es-uuid").build());
        when(decoder.decode("token-vencido")).thenThrow(new BadJwtException("Jwt expired at 2026-10-30T10:00:00Z"));
        when(usuarios.rolesActivos(ADMIN)).thenReturn(List.of("ADMINISTRADOR"));
        when(usuarios.rolesActivos(ENTRENADOR)).thenReturn(List.of("ENTRENADOR"));
        when(usuarios.rolesActivos(ACUDIENTE)).thenReturn(List.of("ACUDIENTE"));
        when(usuarios.rolesActivos(INACTIVO)).thenReturn(List.of());
        when(codigosPersistencia.sesionVerificada(any(), any())).thenReturn(true);
        when(entrenadores.listar()).thenReturn(List.of());
        when(entrenadores.listarCategorias()).thenReturn(List.of());
        when(salud.consultar()).thenReturn(new SaludResponseDTO("UP", "UP", "prueba"));
    }

    private ResultActions pedir(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static ResultActions error(ResultActions respuesta, int estado, String mensaje, String ruta) throws Exception {
        return respuesta
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.ruta").value(ruta))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void sinTokenEs401ConElFormatoDeErrores() throws Exception {
        error(mvc.perform(get("/api/entrenadores")), 401, RespuestasSeguridad.SIN_SESION, "/api/entrenadores")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        verify(entrenadores, never()).listar();
    }

    @Test
    void unTokenVencidoOInvalidoEs401SinDecirElMotivo() throws Exception {
        error(pedir("/api/entrenadores", "token-vencido"), 401, RespuestasSeguridad.SIN_SESION, "/api/entrenadores")
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("expired"))));
    }

    @Test
    void elAdministradorGestionaEntrenadoresYCategorias() throws Exception {
        pedir("/api/entrenadores", "token-" + ADMIN).andExpect(status().isOk());
        pedir("/api/categorias", "token-" + ADMIN).andExpect(status().isOk());
    }

    @Test
    void elEntrenadorVeLasCategoriasPeroNoGestionaEntrenadores() throws Exception {
        pedir("/api/categorias", "token-" + ENTRENADOR).andExpect(status().isOk());
        pedir("/api/deportistas", "token-" + ENTRENADOR).andExpect(status().isOk());
        error(pedir("/api/entrenadores", "token-" + ENTRENADOR), 403, RespuestasSeguridad.SIN_PERMISO, "/api/entrenadores");
        verify(entrenadores, never()).listar();
    }

    @Test
    void elAcudienteNoVeNiEntrenadoresNiCategorias() throws Exception {
        error(pedir("/api/entrenadores", "token-" + ACUDIENTE), 403, RespuestasSeguridad.SIN_PERMISO, "/api/entrenadores");
        error(pedir("/api/categorias", "token-" + ACUDIENTE), 403, RespuestasSeguridad.SIN_PERMISO, "/api/categorias");
    }

    @Test
    void unaCuentaInactivaOSinPerfilNoTieneRolesYEs403() throws Exception {
        error(pedir("/api/categorias", "token-" + INACTIVO), 403, RespuestasSeguridad.SIN_PERMISO, "/api/categorias");
        error(pedir("/api/categorias", "token-sub-raro"), 403, RespuestasSeguridad.SIN_PERMISO, "/api/categorias");
    }

    @Test
    void usuariosYDeportistasSonSoloDelAdministrador() throws Exception {
        pedir("/api/usuarios", "token-" + ADMIN).andExpect(status().isOk());
        for (String token : List.of("token-" + ENTRENADOR, "token-" + ACUDIENTE)) {
            error(pedir("/api/usuarios", token), 403, RespuestasSeguridad.SIN_PERMISO, "/api/usuarios");
            String ruta = "/api/deportistas/" + ADMIN;
            error(pedir(ruta, token), 403, RespuestasSeguridad.SIN_PERMISO, ruta);
        }
        verify(usuariosServicio, org.mockito.Mockito.times(1)).listar();
        verify(deportistas, never()).consultar(any());
    }

    @Test
    void elPerfilPropioEsAccesibleParaCualquierRolAutenticado() throws Exception {
        pedir("/api/usuarios/yo", "token-" + ENTRENADOR).andExpect(status().isOk());
    }

    @Test
    void unaRutaProtegidaExigeHaberCompletadoEl2fa() throws Exception {
        when(codigosPersistencia.sesionVerificada(eq(ADMIN), any())).thenReturn(false);

        pedir("/api/categorias", "token-" + ADMIN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"))
                .andExpect(jsonPath("$.campos[0]").value("codigo"));
    }

    @Test
    void el2faExigeElTokenPeroNoUnRol() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/2fa/codigo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + INACTIVO))
                .andExpect(status().isAccepted());
        verify(codigos).solicitar(INACTIVO);
        error(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/2fa/codigo")),
                401, RespuestasSeguridad.SIN_SESION, "/api/auth/2fa/codigo");
    }

    @Test
    void unaRutaQueNoExisteTambienExigeSesion() throws Exception {
        error(mvc.perform(get("/api/no-existe")), 401, RespuestasSeguridad.SIN_SESION, "/api/no-existe");
    }

    @Test
    void laSaludEsPublica() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        verify(decoder, never()).decode(any());
    }

    @Test
    void elPreflightDeCorsPermiteCredencialesSoloAlFrontend() throws Exception {
        mvc.perform(options("/api/entrenadores")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mvc.perform(options("/api/entrenadores")
                        .header(HttpHeaders.ORIGIN, "https://sitio-ajeno.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
