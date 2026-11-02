package co.edu.futdata.util.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.IEntrenadorService;
import co.edu.futdata.services.facade.IPagoService;
import co.edu.futdata.services.facade.IPagoWompiService;
import co.edu.futdata.services.facade.FirmaWebhookInvalidaException;
import co.edu.futdata.services.facade.IUsuarioService;
import co.edu.futdata.services.facade.ISaludService;
import co.edu.futdata.api.dto.SaludResponseDTO;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
 *
 * <p>Cada token trae su {@code session_id}; la sesión verificada sale de {@link ISesionVerificadaDAO} (simulado):
 * por defecto todas lo están, y cada prueba de 2FA dice cuáles no.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class SeguridadTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
    private static final UUID INACTIVO = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    /** Dos sesiones del mismo usuario (por ejemplo, celular y computador). */
    private static final UUID SESION_A = UUID.fromString("5e55a000-0000-0000-0000-00000000000a");
    private static final UUID SESION_B = UUID.fromString("5e55b000-0000-0000-0000-00000000000b");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;

    @MockitoBean
    private IUsuarioDAO usuarios;

    @MockitoBean
    private ISesionVerificadaDAO sesiones;

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

    @MockitoBean
    private IPagoService pagos;

    @MockitoBean
    private IPagoWompiService pagosWompi;

    @BeforeEach
    void prepararTokens() {
        for (UUID id : List.of(ADMIN, ENTRENADOR, ACUDIENTE, INACTIVO)) {
            token("token-" + id, id.toString(), UUID.randomUUID().toString());
        }
        token("token-sub-raro", "no-es-uuid", UUID.randomUUID().toString());
        token("token-admin-sesion-a", ADMIN.toString(), SESION_A.toString());
        token("token-admin-sesion-b", ADMIN.toString(), SESION_B.toString());
        token("token-sin-sesion", ADMIN.toString(), null);
        token("token-sesion-rara", ADMIN.toString(), "no-es-uuid");
        when(decoder.decode("token-vencido")).thenThrow(new BadJwtException("Jwt expired at 2026-10-30T10:00:00Z"));
        when(usuarios.rolesActivos(ADMIN)).thenReturn(List.of("ADMINISTRADOR"));
        when(usuarios.rolesActivos(ENTRENADOR)).thenReturn(List.of("ENTRENADOR"));
        when(usuarios.rolesActivos(ACUDIENTE)).thenReturn(List.of("ACUDIENTE"));
        when(usuarios.rolesActivos(INACTIVO)).thenReturn(List.of());
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
        when(entrenadores.listar()).thenReturn(List.of());
        when(entrenadores.listarCategorias()).thenReturn(List.of());
        when(salud.consultar()).thenReturn(new SaludResponseDTO("UP", "UP", "prueba"));
    }

    /** Un JWT ya validado por el decodificador simulado; {@code sesion} {@code null}: sin el claim session_id. */
    private void token(String valor, String sub, String sesion) {
        Jwt.Builder jwt = Jwt.withTokenValue(valor).header("alg", "ES256").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
        if (sesion != null) {
            jwt.claim(JwtValidator.CLAIM_SESION, sesion);
        }
        when(decoder.decode(valor)).thenReturn(jwt.build());
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
    void elPerfilPropioEsAccesibleParaCualquierRolConLaSesionVerificada() throws Exception {
        pedir("/api/usuarios/yo", "token-" + ENTRENADOR).andExpect(status().isOk());
    }

    @Test
    void elPerfilPropioSin2faEs403() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ENTRENADOR), any())).thenReturn(false);

        pedir("/api/usuarios/yo", "token-" + ENTRENADOR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        verify(usuariosServicio, never()).consultar(any());
    }

    @Test
    void unaRutaProtegidaExigeHaberCompletadoEl2fa() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ADMIN), any())).thenReturn(false);

        pedir("/api/categorias", "token-" + ADMIN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"))
                .andExpect(jsonPath("$.campos[0]").value("codigo"));
    }

    @Test
    void verificarLaSesionANoHabilitaLaSesionBDelMismoUsuario() throws Exception {
        when(sesiones.estaVerificada(eq(SESION_A), eq(ADMIN), any())).thenReturn(true);
        when(sesiones.estaVerificada(eq(SESION_B), eq(ADMIN), any())).thenReturn(false);

        pedir("/api/categorias", "token-admin-sesion-a").andExpect(status().isOk());
        pedir("/api/categorias", "token-admin-sesion-b")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
    }

    @Test
    void unTokenSinSessionIdEs401AunqueElUsuarioTengaRol() throws Exception {
        error(pedir("/api/categorias", "token-sin-sesion"), 401, RespuestasSeguridad.SIN_SESION, "/api/categorias");
        error(pedir("/api/categorias", "token-sesion-rara"), 401, RespuestasSeguridad.SIN_SESION, "/api/categorias");
        error(mvc.perform(post("/api/auth/2fa/codigo").header(HttpHeaders.AUTHORIZATION, "Bearer token-sin-sesion")),
                401, RespuestasSeguridad.SIN_SESION, "/api/auth/2fa/codigo");
        verify(sesiones, never()).estaVerificada(any(), any(), any());
        verify(codigos, never()).solicitar(any());
    }

    @Test
    void el2faExigeElTokenYUnRolPeroNoLaSesionVerificada() throws Exception {
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(false);

        mvc.perform(post("/api/auth/2fa/codigo").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + ACUDIENTE))
                .andExpect(status().isAccepted());
        verify(codigos).solicitar(ACUDIENTE);
        error(mvc.perform(post("/api/auth/2fa/codigo")), 401, RespuestasSeguridad.SIN_SESION, "/api/auth/2fa/codigo");
    }

    @Test
    void unTokenSinRolesEs403TambienEnEl2faYEnElPerfil() throws Exception {
        for (String ruta : List.of("/api/usuarios/yo", "/api/categorias")) {
            error(pedir(ruta, "token-" + INACTIVO), 403, RespuestasSeguridad.SIN_PERMISO, ruta);
        }
        error(mvc.perform(post("/api/auth/2fa/codigo").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + INACTIVO)),
                403, RespuestasSeguridad.SIN_PERMISO, "/api/auth/2fa/codigo");
        verify(codigos, never()).solicitar(any());
        verify(usuariosServicio, never()).consultar(any());
    }

    @Test
    void elLogoutExigeLaSesionVerificadaYCierraSoloLaDelToken() throws Exception {
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer token-admin-sesion-a"))
                .andExpect(status().isNoContent());
        verify(codigos).cerrarSesion(ADMIN, SESION_A);

        when(sesiones.estaVerificada(eq(SESION_B), eq(ADMIN), any())).thenReturn(false);
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer token-admin-sesion-b"))
                .andExpect(status().isForbidden());
        verify(codigos, never()).cerrarSesion(ADMIN, SESION_B);
    }

    @Test
    void elWebhookDeWompiEsPublicoSinTokenYSin2faPeroExigeLaFirma() throws Exception {
        // Sin JWT: la seguridad lo deja pasar y es el servicio quien verifica la firma del evento
        mvc.perform(post(RutasSin2FA.WEBHOOK_WOMPI).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("X-Event-Checksum", "abc"))
                .andExpect(status().isOk());
        verify(pagosWompi).procesarEvento("{}", "abc");
        verify(decoder, never()).decode(any());

        org.mockito.Mockito.doThrow(new FirmaWebhookInvalidaException()).when(pagosWompi).procesarEvento(any(), any());
        mvc.perform(post(RutasSin2FA.WEBHOOK_WOMPI).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("La firma del evento no es válida."));
    }

    // --- Pagos: roles por endpoint ------------------------------------------------------------------------

    private static final String ID_PAGO = "00000000-0000-0000-0000-0000000000d1";

    /** (método, ruta, roles que pasan). Los demás roles reciben 403 y el servicio no se toca. */
    private record RutaPago(String metodo, String ruta, Set<UUID> permitidos) {
    }

    private ResultActions llamar(String metodo, String ruta, UUID quien) throws Exception {
        var peticion = switch (metodo) {
            case "GET" -> get(ruta);
            default -> post(ruta).contentType(MediaType.APPLICATION_JSON).content("{}");
        };
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer token-" + quien));
    }

    @Test
    void cadaEndpointDePagosExigeSuRol() throws Exception {
        UUID tesorero = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
        token("token-" + tesorero, tesorero.toString(), UUID.randomUUID().toString());
        when(usuarios.rolesActivos(tesorero)).thenReturn(List.of("TESORERO"));
        Set<UUID> soloAdmin = Set.of(ADMIN);
        Set<UUID> adminYTesorero = Set.of(ADMIN, tesorero);
        Set<UUID> soloAcudiente = Set.of(ACUDIENTE);
        List<RutaPago> rutas = List.of(
                new RutaPago("POST", "/api/pagos", soloAdmin),
                new RutaPago("GET", "/api/pagos", adminYTesorero),
                new RutaPago("GET", "/api/pagos/mis-pagos", soloAcudiente),
                new RutaPago("POST", "/api/pagos/" + ID_PAGO + "/iniciar-wompi", soloAcudiente),
                new RutaPago("POST", "/api/pagos/" + ID_PAGO + "/reintentar", soloAcudiente),
                new RutaPago("POST", "/api/pagos/" + ID_PAGO + "/conciliar", adminYTesorero),
                new RutaPago("POST", "/api/pagos/" + ID_PAGO + "/aprobar", adminYTesorero),
                new RutaPago("POST", "/api/pagos/" + ID_PAGO + "/rechazar", adminYTesorero),
                new RutaPago("GET", "/api/pagos/" + ID_PAGO + "/comprobante", Set.of(ADMIN, tesorero, ACUDIENTE)),
                new RutaPago("POST", "/api/pagos/generar-mensualidades", soloAdmin));
        for (RutaPago r : rutas) {
            for (UUID quien : List.of(ADMIN, tesorero, ENTRENADOR, ACUDIENTE)) {
                int estado = llamar(r.metodo(), r.ruta(), quien).andReturn().getResponse().getStatus();
                if (r.permitidos().contains(quien)) {
                    org.assertj.core.api.Assertions.assertThat(estado).as("%s %s como %s", r.metodo(), r.ruta(), quien).isNotIn(401, 403);
                } else {
                    org.assertj.core.api.Assertions.assertThat(estado).as("%s %s como %s", r.metodo(), r.ruta(), quien).isEqualTo(403);
                }
            }
            // sin token: 401
            int sinToken = (r.metodo().equals("GET") ? mvc.perform(get(r.ruta())) : mvc.perform(post(r.ruta())))
                    .andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(sinToken).as("%s %s sin token", r.metodo(), r.ruta()).isEqualTo(401);
        }
    }

    @Test
    void losEndpointsDePagosExigenLaSesionVerificada() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ACUDIENTE), any())).thenReturn(false);
        pedir("/api/pagos/mis-pagos", "token-" + ACUDIENTE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        verify(pagos, never()).misPagos(any());
    }

    @Test
    void unAcudienteNoPuedeLlegarAlCobroDeOtroPorLaRutaDeLosPrivilegiados() throws Exception {
        error(llamar("POST", "/api/pagos/" + ID_PAGO + "/conciliar", ACUDIENTE), 403, RespuestasSeguridad.SIN_PERMISO,
                "/api/pagos/" + ID_PAGO + "/conciliar");
        verify(pagos, never()).conciliar(any(), any());
    }

    @Test
    void laDocumentacionNoEstaFueraDelPerfilDev() throws Exception {
        for (String ruta : List.of("/v3/api-docs", "/swagger-ui.html")) {
            error(mvc.perform(get(ruta)), 401, RespuestasSeguridad.SIN_SESION, ruta);
        }
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
