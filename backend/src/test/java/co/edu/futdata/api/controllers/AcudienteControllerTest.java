package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.MiJugadorFichaResponseDTO;
import co.edu.futdata.api.dto.MiJugadorResponseDTO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.security.JwtValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * /api/acudientes/me por HTTP con la cadena de seguridad completa (JWT, sesión 2FA y roles) y el servicio simulado:
 * roles, forma del JSON, errores y que el acudiente salga siempre del token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class AcudienteControllerTest {

    private static final UUID ACUDIENTE_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ACUDIENTE_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID HIJO_A = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID HIJO_B = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID INEXISTENTE = UUID.fromString("00000000-0000-0000-0000-0000000000d3");
    private static final UUID CATEGORIA = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000009");
    private static final String MENSAJE_404 = "No existe un deportista con ese id.";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;
    @MockitoBean
    private IDeportistaService servicio;

    @BeforeEach
    void preparar() {
        for (var par : List.of(List.of(ACUDIENTE_A, "ACUDIENTE"), List.of(ACUDIENTE_B, "ACUDIENTE"),
                List.of(ADMIN, "ADMINISTRADOR"), List.of(TESORERO, "TESORERO"), List.of(ENTRENADOR, "ENTRENADOR"))) {
            UUID id = (UUID) par.get(0);
            when(decoder.decode("token-" + id)).thenReturn(jwt("token-" + id, id, UUID.randomUUID().toString()));
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) par.get(1)));
        }
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);

        when(servicio.misJugadores(ACUDIENTE_A)).thenReturn(List.of(lista(HIJO_A, "Luis")));
        when(servicio.misJugadores(ACUDIENTE_B)).thenReturn(List.of(lista(HIJO_B, "Pedro")));
        when(servicio.miJugador(ACUDIENTE_A, HIJO_A)).thenReturn(ficha(HIJO_A, "Luis"));
        when(servicio.miJugador(ACUDIENTE_B, HIJO_B)).thenReturn(ficha(HIJO_B, "Pedro"));
        // Ajeno e inexistente: el servicio responde igual
        when(servicio.miJugador(ACUDIENTE_A, HIJO_B)).thenThrow(new RecursoNoEncontradoException(MENSAJE_404));
        when(servicio.miJugador(ACUDIENTE_A, INEXISTENTE)).thenThrow(new RecursoNoEncontradoException(MENSAJE_404));
        when(servicio.miJugador(ACUDIENTE_B, HIJO_A)).thenThrow(new RecursoNoEncontradoException(MENSAJE_404));
    }

    private static Jwt jwt(String valor, UUID sub, String sesion) {
        Jwt.Builder b = Jwt.withTokenValue(valor).header("alg", "ES256").subject(sub.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
        if (sesion != null) {
            b.claim(JwtValidator.CLAIM_SESION, sesion);
        }
        return b.build();
    }

    private ResultActions como(UUID usuario, MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    private static MiJugadorResponseDTO lista(UUID id, String nombre) {
        return new MiJugadorResponseDTO(id, nombre, "Gómez", LocalDate.of(2018, 3, 3), 8, "Cachorros", CATEGORIA, "tarde",
                "Delantero", "https://firmada.test/" + id, "Madre", true, "PENDIENTE");
    }

    private static MiJugadorFichaResponseDTO ficha(UUID id, String nombre) {
        return new MiJugadorFichaResponseDTO(id, nombre, "Gómez", "TI", "1098765432", LocalDate.of(2018, 3, 3), 8,
                "3001234567", "Calle 10 # 20-30", "Cachorros", CATEGORIA, "tarde", "Delantero", "https://firmada.test/" + id,
                "Madre", true, "PENDIENTE", List.of("CERTIFICADO_ESTUDIO", "AUTORIZACION_HABEAS_DATA"));
    }

    // --- Seguridad -----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/api/acudientes/me/deportistas", "/api/acudientes/me/deportistas/00000000-0000-0000-0000-0000000000d1"})
    void sinTokenEs401(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/acudientes/me/deportistas", "/api/acudientes/me/deportistas/00000000-0000-0000-0000-0000000000d1"})
    void unTokenSinSessionIdEs401(String ruta) throws Exception {
        when(decoder.decode("token-sin-sesion")).thenReturn(jwt("token-sin-sesion", ACUDIENTE_A, null));

        mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer token-sin-sesion")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    /** En este backend la sesión sin 2FA completo no es 401 sino 403 con código 2FA_REQUERIDA (ver SesionVerificadaFilter). */
    @ParameterizedTest
    @ValueSource(strings = {"/api/acudientes/me/deportistas", "/api/acudientes/me/deportistas/00000000-0000-0000-0000-0000000000d1"})
    void conLaSesionSin2faVerificadoEs403Con2faRequerida(String ruta) throws Exception {
        when(sesiones.estaVerificada(any(), org.mockito.ArgumentMatchers.eq(ACUDIENTE_A), any())).thenReturn(false);

        como(ACUDIENTE_A, get(ruta))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/acudientes/me/deportistas", "/api/acudientes/me/deportistas/00000000-0000-0000-0000-0000000000d1"})
    void losOtrosRolesRecibenElError403(String ruta) throws Exception {
        for (UUID otro : List.of(ADMIN, TESORERO, ENTRENADOR)) {
            como(otro, get(ruta)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    // --- Lista ---------------------------------------------------------------------------------------------

    @Test
    void laListaDevuelveUnArregloConLosCamposDeLaLista() throws Exception {
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(HIJO_A.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Luis"))
                .andExpect(jsonPath("$[0].apellido").value("Gómez"))
                .andExpect(jsonPath("$[0].fechaNacimiento").value("2018-03-03"))
                .andExpect(jsonPath("$[0].edad").value(8))
                .andExpect(jsonPath("$[0].categoria").value("Cachorros"))
                .andExpect(jsonPath("$[0].idCategoria").value(CATEGORIA.toString()))
                .andExpect(jsonPath("$[0].grupo").value("tarde"))
                .andExpect(jsonPath("$[0].posicion").value("Delantero"))
                .andExpect(jsonPath("$[0].foto").value("https://firmada.test/" + HIJO_A))
                .andExpect(jsonPath("$[0].parentesco").value("Madre"))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].estadoInscripcion").value("PENDIENTE"));
    }

    @Test
    void laListaNoTraeDocumentoTelefonoNiDireccion() throws Exception {
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documento").doesNotExist())
                .andExpect(jsonPath("$[0].tipoDocumento").doesNotExist())
                .andExpect(jsonPath("$[0].telefono").doesNotExist())
                .andExpect(jsonPath("$[0].direccion").doesNotExist())
                .andExpect(jsonPath("$[0].documentosFaltantes").doesNotExist());
    }

    @Test
    void sinJugadoresLaListaEsUnArregloVacio() throws Exception {
        when(servicio.misJugadores(ACUDIENTE_A)).thenReturn(List.of());

        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- Ficha ---------------------------------------------------------------------------------------------

    @Test
    void laFichaTraeLosDatosDeContactoYLosDocumentosFaltantes() throws Exception {
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas/" + HIJO_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(HIJO_A.toString()))
                .andExpect(jsonPath("$.tipoDocumento").value("TI"))
                .andExpect(jsonPath("$.documento").value("1098765432"))
                .andExpect(jsonPath("$.telefono").value("3001234567"))
                .andExpect(jsonPath("$.direccion").value("Calle 10 # 20-30"))
                .andExpect(jsonPath("$.parentesco").value("Madre"))
                .andExpect(jsonPath("$.estadoInscripcion").value("PENDIENTE"))
                .andExpect(jsonPath("$.documentosFaltantes[0]").value("CERTIFICADO_ESTUDIO"))
                .andExpect(jsonPath("$.documentosFaltantes[1]").value("AUTORIZACION_HABEAS_DATA"));
    }

    @Test
    void unIdQueNoEsUuidEs400ConElFormatoDeErrorHabitual() throws Exception {
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("id")))
                .andExpect(jsonPath("$.ruta").value("/api/acudientes/me/deportistas/abc"));
        verifyNoInteractions(servicio);
    }

    @Test
    void unIdAjenoYUnoInexistenteDanElMismoCuerpo404() throws Exception {
        String ajeno = como(ACUDIENTE_A, get("/api/acudientes/me/deportistas/" + HIJO_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_404))
                .andReturn().getResponse().getContentAsString();
        String inexistente = como(ACUDIENTE_A, get("/api/acudientes/me/deportistas/" + INEXISTENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_404))
                .andReturn().getResponse().getContentAsString();

        // Solo cambian la marca de tiempo y la ruta (que lleva el id que el propio cliente pidió)
        org.assertj.core.api.Assertions.assertThat(sinVariables(ajeno)).isEqualTo(sinVariables(inexistente));
    }

    private static String sinVariables(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\"", "").replaceAll("\"ruta\":\"[^\"]*\"", "");
    }

    // --- El acudiente sale del token -----------------------------------------------------------------------

    @Test
    void elAcudienteASoloVeLoSuyoEnLasDosRutas() throws Exception {
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(Matchers.contains(HIJO_A.toString())))
                .andExpect(content().string(Matchers.not(Matchers.containsString(HIJO_B.toString()))));
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas/" + HIJO_B))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Pedro"))));

        verify(servicio, never()).misJugadores(ACUDIENTE_B);
        verify(servicio, never()).miJugador(ACUDIENTE_B, HIJO_B);
    }

    @Test
    void ningunParametroNiCabeceraCambiaDeQueAcudienteSeTrata() throws Exception {
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas")
                .param("idUsuario", ACUDIENTE_B.toString()).param("idAcudiente", ACUDIENTE_B.toString())
                .header("X-Acudiente", ACUDIENTE_B.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(HIJO_A.toString()));
        como(ACUDIENTE_A, get("/api/acudientes/me/deportistas/" + HIJO_B)
                .param("idUsuario", ACUDIENTE_B.toString()).header("X-Acudiente", ACUDIENTE_B.toString()))
                .andExpect(status().isNotFound());

        verify(servicio).misJugadores(ACUDIENTE_A);
        verify(servicio).miJugador(ACUDIENTE_A, HIJO_B);
        verify(servicio, never()).misJugadores(ACUDIENTE_B);
        verify(servicio, never()).miJugador(ACUDIENTE_B, HIJO_B);
    }

    @Test
    void cadaAcudienteVeLoSuyo() throws Exception {
        como(ACUDIENTE_B, get("/api/acudientes/me/deportistas/" + HIJO_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pedro"));
        como(ACUDIENTE_B, get("/api/acudientes/me/deportistas/" + HIJO_A)).andExpect(status().isNotFound());
    }
}
