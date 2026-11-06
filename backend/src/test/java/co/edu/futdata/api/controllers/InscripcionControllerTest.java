package co.edu.futdata.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.datasource.external.AuthNoDisponibleException;
import co.edu.futdata.domain.entities.Acudiente;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.persistence.dao.IAcudienteDAO;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.IInscripcionService;
import co.edu.futdata.services.facade.InscripcionEntrada;
import co.edu.futdata.services.facade.InscripcionResultado;
import co.edu.futdata.services.facade.Solicitante;
import co.edu.futdata.services.impl.CuentasDeAcceso;
import co.edu.futdata.services.impl.InscripcionServiceImpl;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.transaction.support.TransactionOperations;
import co.edu.futdata.util.security.JwtValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code POST /api/inscripciones} con la cadena de seguridad completa (JWT, sesión 2FA y roles) y el servicio simulado:
 * roles, forma exacta del JSON, errores con los campos prefijados y {@code Cache-Control: no-store}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class InscripcionControllerTest {

    private static final String RUTA = "/api/inscripciones";
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID SIN_ROL = UUID.fromString("00000000-0000-0000-0000-0000000000f0");
    private static final UUID DEPORTISTA = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID FICHA = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CUENTA = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static final String CUERPO = """
            {"deportista": {"nombre": "Mateo", "apellido": "Ríos", "tipoDocumento": "TI", "documento": "1234567890",
                            "fechaNacimiento": "2016-03-03", "telefono": "3001234567", "direccion": "Calle 1 # 2-3",
                            "posicion": "Delantero", "grupo": "mañana"},
             "acudiente": {"nombre": "Ana", "apellido": "Ríos", "correo": "ana@correo.co", "telefono": "3001234567",
                           "parentesco": "Madre"},
             "campoQueNoExiste": 1}""";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;
    @MockitoBean
    private IInscripcionService servicio;
    @MockitoBean
    private IDeportistaService deportistas;

    @BeforeEach
    void preparar() {
        for (var par : List.of(List.of(ACUDIENTE, "ACUDIENTE"), List.of(ADMIN, "ADMINISTRADOR"),
                List.of(TESORERO, "TESORERO"), List.of(ENTRENADOR, "ENTRENADOR"))) {
            UUID id = (UUID) par.get(0);
            when(decoder.decode("token-" + id)).thenReturn(jwt("token-" + id, id));
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) par.get(1)));
        }
        when(decoder.decode("token-" + SIN_ROL)).thenReturn(jwt("token-" + SIN_ROL, SIN_ROL));
        when(usuarios.rolesActivos(SIN_ROL)).thenReturn(List.of());
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
        when(servicio.inscribir(any(), any())).thenReturn(resultado(true, false, null));
        when(deportistas.consultar(DEPORTISTA)).thenReturn(deportista());
    }

    // --- Seguridad --------------------------------------------------------------------------------------------

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(peticion(CUERPO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENTRENADOR", "TESORERO", "ACUDIENTE", "SIN_ROL"})
    void soloElAdministradorInscribe(String quien) throws Exception {
        UUID usuario = switch (quien) {
            case "ENTRENADOR" -> ENTRENADOR;
            case "TESORERO" -> TESORERO;
            case "ACUDIENTE" -> ACUDIENTE;
            default -> SIN_ROL;
        };

        como(usuario, peticion(CUERPO)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio, deportistas);
    }

    @Test
    void sinLaSesion2faVerificadaEs403Con2faRequerida() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ADMIN), any())).thenReturn(false);

        como(ADMIN, peticion(CUERPO)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        verifyNoInteractions(servicio);
    }

    // --- Respuesta 201 ----------------------------------------------------------------------------------------

    @Test
    void inscribeYDevuelveLaFormaExactaConNoStore() throws Exception {
        when(servicio.inscribir(any(), any())).thenReturn(resultado(false, true, true));

        como(ADMIN, peticion(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.deportista.id").value(DEPORTISTA.toString()))
                .andExpect(jsonPath("$.deportista.nombre").value("Mateo"))
                .andExpect(jsonPath("$.deportista.documento").value("1234567890"))
                .andExpect(jsonPath("$.deportista.categoria").value("Semillero"))
                .andExpect(jsonPath("$.deportista.grupo").value("mañana"))
                .andExpect(jsonPath("$.deportista.activo").value(true))
                .andExpect(jsonPath("$.deportista.estadoInscripcion").value("PENDIENTE"))
                .andExpect(jsonPath("$.acudiente.id").value(FICHA.toString()))
                .andExpect(jsonPath("$.acudiente.nombre").value("Ana"))
                .andExpect(jsonPath("$.acudiente.apellido").value("Ríos"))
                .andExpect(jsonPath("$.acudiente.correo").value("ana@correo.co"))
                .andExpect(jsonPath("$.acudiente.reutilizado").value(false))
                .andExpect(jsonPath("$.acudiente.cuentaCreada").value(true))
                .andExpect(jsonPath("$.acudiente.idUsuario").doesNotExist())
                .andExpect(jsonPath("$.acudiente.telefono").doesNotExist())
                .andExpect(jsonPath("$.parentesco").value("Madre"))
                .andExpect(jsonPath("$.correoEnviado").value(true))
                .andExpect(content().string(not(containsString(CUENTA.toString()))));
    }

    @Test
    void lasClavesSonExactamenteLasAcordadas() throws Exception {
        String json = como(ADMIN, peticion(CUERPO)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();

        var arbol = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertThat(arbol.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactly("deportista", "acudiente", "parentesco", "correoEnviado");
        assertThat(arbol.get("acudiente").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactly("id", "nombre", "apellido", "correo", "reutilizado", "cuentaCreada");
    }

    @Test
    void correoEnviadoNuloSeSerializaComoNull() throws Exception {
        como(ADMIN, peticion(CUERPO)).andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"correoEnviado\":null")))
                .andExpect(jsonPath("$.acudiente.reutilizado").value(true))
                .andExpect(jsonPath("$.acudiente.cuentaCreada").value(false));
    }

    @Test
    void correoEnviadoFalsoSeSerializaComoFalse() throws Exception {
        when(servicio.inscribir(any(), any())).thenReturn(resultado(false, true, false));

        como(ADMIN, peticion(CUERPO)).andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"correoEnviado\":false")));
    }

    @Test
    void elSolicitanteSaleDelTokenYElCuerpoLlegaAlServicio() throws Exception {
        como(ADMIN, peticion(CUERPO)).andExpect(status().isCreated());

        ArgumentCaptor<InscripcionEntrada> entrada = ArgumentCaptor.forClass(InscripcionEntrada.class);
        ArgumentCaptor<Solicitante> solicitante = ArgumentCaptor.forClass(Solicitante.class);
        verify(servicio).inscribir(entrada.capture(), solicitante.capture());
        assertThat(solicitante.getValue()).isEqualTo(new Solicitante(ADMIN, "ADMINISTRADOR"));
        assertThat(entrada.getValue().deportista().documento()).isEqualTo("1234567890");
        assertThat(entrada.getValue().deportista().grupo()).isEqualTo("mañana");
        assertThat(entrada.getValue().acudiente().correo()).isEqualTo("ana@correo.co");
        assertThat(entrada.getValue().acudiente().parentesco()).isEqualTo("Madre");
    }

    // --- Errores ----------------------------------------------------------------------------------------------

    /** Con la validación real del servicio: el array campos trae los nombres prefijados tal cual, todos juntos. */
    @Test
    void un400TraeTodosLosErroresConLosCamposPrefijados() throws Exception {
        var real = soloValidacion();
        when(servicio.inscribir(any(), any())).thenAnswer(i -> real.inscribir(i.getArgument(0), i.getArgument(1)));
        String malo = """
                {"deportista": {"nombre": "Mateo", "apellido": "Ríos", "tipoDocumento": "CC", "documento": "12",
                                "fechaNacimiento": "2016-03-03", "telefono": "3001234567", "direccion": "Calle 1 # 2-3"},
                 "acudiente": {"nombre": "Ana", "apellido": "Ríos", "correo": "no-es-correo", "telefono": "123",
                               "parentesco": "Primo"}}""";

        como(ADMIN, peticion(malo)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos").value(contains("acudiente.correo", "acudiente.parentesco",
                        "acudiente.telefono", "deportista.documento", "deportista.tipoDocumento")))
                .andExpect(jsonPath("$.mensaje").value(containsString("acudiente.correo: no es válido")));
        verifyNoInteractions(deportistas);
    }

    @Test
    void sinCuerpoEs400ConCampoCuerpo() throws Exception {
        var real = soloValidacion();
        when(servicio.inscribir(any(), any())).thenAnswer(i -> real.inscribir(i.getArgument(0), i.getArgument(1)));

        como(ADMIN, post(RUTA).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos").value(contains("cuerpo")));
    }

    @Test
    void unJsonIlegibleEs400ComoEnElRestoDeLaApi() throws Exception {
        como(ADMIN, peticion("{ esto no es json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición falta o no es un JSON válido"));
        verifyNoInteractions(servicio);
    }

    @Test
    void unaFechaConFormatoInvalidoTraeElCampoPrefijadoPorLaRuta() throws Exception {
        como(ADMIN, peticion("{\"deportista\": {\"fechaNacimiento\": \"03/03/2016\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos").value(contains("deportista.fechaNacimiento")));
        verifyNoInteractions(servicio);
    }

    @Test
    void unDocumentoRepetidoEs409ConElCampoPrefijado() throws Exception {
        when(servicio.inscribir(any(), any())).thenThrow(new ConflictoException(
                "deportista.documento: ya está registrado en otro deportista", List.of("deportista.documento")));

        como(ADMIN, peticion(CUERPO)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos").value(contains("deportista.documento")))
                .andExpect(jsonPath("$.mensaje").value("deportista.documento: ya está registrado en otro deportista"));
        verifyNoInteractions(deportistas);
    }

    @Test
    void unCorreoDeOtroRolEs409ConElCampoPrefijado() throws Exception {
        when(servicio.inscribir(any(), any())).thenThrow(new ConflictoException(
                "acudiente.correo: ese correo ya pertenece a una cuenta con otro rol", List.of("acudiente.correo")));

        como(ADMIN, peticion(CUERPO)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos").value(contains("acudiente.correo")))
                .andExpect(jsonPath("$.mensaje").value("acudiente.correo: ese correo ya pertenece a una cuenta con otro rol"));
    }

    @Test
    void siAuthEstaCaidoEs503() throws Exception {
        when(servicio.inscribir(any(), any())).thenThrow(new AuthNoDisponibleException("Supabase Auth no responde", 503));

        como(ADMIN, peticion(CUERPO)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(deportistas);
    }

    // --- Ayudas -----------------------------------------------------------------------------------------------

    private static Jwt jwt(String valor, UUID sub) {
        return Jwt.withTokenValue(valor).header("alg", "ES256").subject(sub.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString()).build();
    }

    private ResultActions como(UUID usuario, MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    private static MockHttpServletRequestBuilder peticion(String cuerpo) {
        return post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static InscripcionResultado resultado(boolean reutilizado, boolean cuentaCreada, Boolean correoEnviado) {
        Deportista d = new Deportista();
        d.setId(DEPORTISTA);
        return new InscripcionResultado(d, new Acudiente(FICHA, "Ana", "Ríos", "ana@correo.co", "3001234567", CUENTA),
                reutilizado, cuentaCreada, "Madre", correoEnviado);
    }

    private static DeportistaResponseDTO deportista() {
        return new DeportistaResponseDTO(DEPORTISTA, "Mateo", "Ríos", "TI", "1234567890", LocalDate.of(2016, 3, 3), 10,
                "3001234567", "Calle 1 # 2-3", "Delantero", null, "Semillero",
                UUID.fromString("00000000-0000-0000-0000-00000000ca7e"), "mañana", true, "PENDIENTE");
    }

    /** El servicio real con sus dependencias simuladas: solo llega a validar (los datos de estas pruebas no pasan). */
    private static InscripcionServiceImpl soloValidacion() {
        return new InscripcionServiceImpl(mock(IDeportistaDAO.class), mock(IAcudienteDAO.class), mock(IUsuarioDAO.class),
                mock(CuentasDeAcceso.class), mock(TransactionOperations.class),
                Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota")));
    }
}
