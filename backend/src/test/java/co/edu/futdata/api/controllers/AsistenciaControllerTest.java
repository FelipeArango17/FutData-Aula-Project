package co.edu.futdata.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.domain.rules.ReglasAsistencia;
import co.edu.futdata.persistence.dao.DeportistaDeSesion;
import co.edu.futdata.persistence.dao.IAsistenciaDAO;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IExcepcionHorarioDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.persistence.dao.SolapeDeEntrenamientoException;
import co.edu.futdata.util.security.JwtValidator;
import co.edu.futdata.util.security.RespuestasSeguridad;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * /api/asistencia/sesion por HTTP con la cadena de seguridad real (JWT, 2FA y roles), el servicio real y los DAO
 * simulados. El recorrido contra PostgreSQL está en {@code AsistenciaControllerConPostgresTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class AsistenciaControllerTest {

    private static final String RUTA = "/api/asistencia/sesion";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-00000000007e");
    private static final UUID SIN_ROL = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    /** Sub-12, martes 16:00-17:30: de la categoría del entrenador de las pruebas. */
    private static final UUID FRANJA_SUB12 = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID CAT_SUB12 = UUID.fromString("c0000000-0000-0000-0000-000000000002");
    /** Semillero, martes 16:00-17:30, "por turnos": de otra categoría. */
    private static final UUID FRANJA_SEMILLERO = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID CAT_SEMILLERO = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID EXTRA_SUB12 = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID FILA_ENTRENADOR = UUID.fromString("e0000000-0000-0000-0000-00000000000e");
    private static final UUID ENTRENAMIENTO = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID NINO_1 = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID NINO_2 = UUID.fromString("d0000000-0000-0000-0000-000000000002");
    private static final String MARTES = "2026-11-03";
    private static final String MARTES_FUTURO = "2026-11-10";

    @TestConfiguration
    static class Configuracion {
        /** Miércoles 4 de noviembre de 2026, 10:00 en Bogotá. */
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), BOGOTA);
        }

        /** Sin base de datos: la transacción del servicio real no abre conexión. */
        @Bean
        @Primary
        PlatformTransactionManager transaccionesSinBase() {
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definicion) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus estado) {
                }

                @Override
                public void rollback(TransactionStatus estado) {
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;
    @MockitoBean
    private IAsistenciaDAO asistencias;
    @MockitoBean
    private IFranjaHorarioDAO franjas;
    @MockitoBean
    private IExcepcionHorarioDAO excepciones;
    @MockitoBean
    private IEntrenadorDAO entrenadores;
    @MockitoBean
    private ICategoriaDAO categorias;

    @BeforeEach
    void preparar() {
        for (Object[] rol : new Object[][] {{ACUDIENTE, "ACUDIENTE"}, {ADMIN, "ADMINISTRADOR"},
                {ENTRENADOR, "ENTRENADOR"}, {TESORERO, "TESORERO"}}) {
            UUID id = (UUID) rol[0];
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) rol[1]));
            token("token-" + id, id);
        }
        token("token-" + SIN_ROL, SIN_ROL);
        when(usuarios.rolesActivos(SIN_ROL)).thenReturn(List.of());
        when(decoder.decode("token-vencido")).thenThrow(new BadJwtException("Jwt expired at 2026-10-30T10:00:00Z"));
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);

        FranjaHorario sub12 = franja(FRANJA_SUB12, CAT_SUB12, "Sub-12", "Luis Pérez", FILA_ENTRENADOR, null);
        FranjaHorario semillero = franja(FRANJA_SEMILLERO, CAT_SEMILLERO, "Semillero", null, null, "tarde");
        when(franjas.buscarPorId(FRANJA_SUB12)).thenReturn(Optional.of(sub12));
        when(franjas.buscarPorId(FRANJA_SEMILLERO)).thenReturn(Optional.of(semillero));
        when(excepciones.buscarPorId(EXTRA_SUB12)).thenReturn(Optional.of(new ExcepcionHorario(EXTRA_SUB12,
                ExcepcionHorario.EXTRA, LocalDate.parse(MARTES), null, CAT_SUB12, "Sub-12", null, LocalTime.of(18, 0),
                LocalTime.of(19, 0), "UVA El Paraíso", null, null, null, null, null, null)));
        // El usuario ENTRENADOR es el entrenador de Sub-12
        Entrenador fila = new Entrenador();
        fila.setId(FILA_ENTRENADOR);
        when(entrenadores.buscarPorIdUsuario(ENTRENADOR)).thenReturn(Optional.of(fila));
        when(categorias.listarPorEntrenador(FILA_ENTRENADOR))
                .thenReturn(List.of(new Categoria(CAT_SUB12, "Sub-12", 12, 12, FILA_ENTRENADOR)));
        when(asistencias.listarDeportistas(any(), any(), anyInt(), any())).thenReturn(List.of(
                new DeportistaDeSesion(NINO_1, "Ana", "Alfa", null, null),
                new DeportistaDeSesion(NINO_2, "Beto", "Beta", "AUSENTE", "Sin aviso")));
        when(asistencias.buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any())).thenReturn(ENTRENAMIENTO);
    }

    private void token(String valor, UUID sub) {
        when(decoder.decode(valor)).thenReturn(Jwt.withTokenValue(valor).header("alg", "ES256").subject(sub.toString())
                .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private static FranjaHorario franja(UUID id, UUID idCategoria, String categoria, String entrenador, UUID idEntrenador,
                                        String grupo) {
        FranjaHorario f = new FranjaHorario();
        f.setId(id);
        f.setIdCategoria(idCategoria);
        f.setCategoria(categoria);
        f.setDia(2);
        f.setHoraInicio(LocalTime.of(16, 0));
        f.setHoraFin(LocalTime.of(17, 30));
        f.setSede("Horizontes");
        f.setIdEntrenador(idEntrenador);
        f.setEntrenador(entrenador);
        f.setGrupo(grupo);
        return f;
    }

    private ResultActions pedir(UUID usuario, String consulta) throws Exception {
        return mvc.perform(get(RUTA + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    private ResultActions guardar(UUID usuario, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder peticion = put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario);
        return mvc.perform(cuerpo == null ? peticion.contentType(MediaType.APPLICATION_JSON)
                : peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String cuerpo(String fecha, String sesion, String registros) {
        return "{\"fecha\":\"" + fecha + "\"," + sesion + ",\"registros\":" + registros + "}";
    }

    private static String franjaSub12() {
        return "\"idFranja\":\"" + FRANJA_SUB12 + "\"";
    }

    private static String registro(UUID id, String estado) {
        return "{\"idDeportista\":\"" + id + "\",\"estado\":\"" + estado + "\"}";
    }

    /** El formato único de error: estado, error, mensaje, ruta y timestamp (y campos si el error es de campos). */
    private static ResultActions error(ResultActions respuesta, int estado, String mensaje) throws Exception {
        return respuesta
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.ruta").value(RUTA))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // --- Seguridad ------------------------------------------------------------------------------------------

    @Test
    void sinTokenEs401EnElGetYEnElPut() throws Exception {
        error(mvc.perform(get(RUTA)), 401, RespuestasSeguridad.SIN_SESION);
        error(mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}")), 401, RespuestasSeguridad.SIN_SESION);
    }

    @Test
    void conTokenVencidoEs401() throws Exception {
        error(mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-vencido")), 401,
                RespuestasSeguridad.SIN_SESION);
    }

    @Test
    void sinHaberCompletadoElDosFactoresEs403ConElCodigo() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ADMIN), any())).thenReturn(false);

        pedir(ADMIN, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        guardar(ADMIN, "{}").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
    }

    @Test
    void elTesoreroElAcudienteYUnaCuentaSinRolRecibenElMismo403() throws Exception {
        for (UUID usuario : List.of(TESORERO, ACUDIENTE, SIN_ROL)) {
            error(pedir(usuario, "?fecha=" + MARTES + "&idFranja=" + FRANJA_SUB12), 403, RespuestasSeguridad.SIN_PERMISO);
            error(guardar(usuario, cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "]")), 403,
                    RespuestasSeguridad.SIN_PERMISO);
        }
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void elEntrenadorDeOtraCategoriaRecibe403ConSuMensaje() throws Exception {
        error(pedir(ENTRENADOR, "?fecha=" + MARTES + "&idFranja=" + FRANJA_SEMILLERO), 403, ReglasAsistencia.SIN_PERMISO);
        error(guardar(ENTRENADOR, cuerpo(MARTES, "\"idFranja\":\"" + FRANJA_SEMILLERO + "\"",
                "[" + registro(NINO_1, "PRESENTE") + "]")), 403, ReglasAsistencia.SIN_PERMISO)
                .andExpect(jsonPath("$.campos").doesNotExist());
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    // --- 200 ------------------------------------------------------------------------------------------------

    @Test
    void elAdministradorYElEntrenadorLeenLaSesion() throws Exception {
        for (UUID usuario : List.of(ADMIN, ENTRENADOR)) {
            pedir(usuario, "?fecha=" + MARTES + "&idFranja=" + FRANJA_SUB12)
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.sesion.fecha").value(MARTES))
                    .andExpect(jsonPath("$.sesion.categoria").value("Sub-12"))
                    .andExpect(jsonPath("$.puedeRegistrar").value(true))
                    .andExpect(jsonPath("$.deportistas.length()").value(2));
        }
    }

    @Test
    void elAdministradorYElEntrenadorGuardanYRecibenLaSesion() throws Exception {
        for (UUID usuario : List.of(ADMIN, ENTRENADOR)) {
            guardar(usuario, cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sesion.horaInicio").value("16:00"))
                    .andExpect(jsonPath("$.puedeRegistrar").value(true))
                    .andExpect(jsonPath("$.deportistas[1].estado").value("AUSENTE"));
        }
    }

    @Test
    void unExtraSeLeeYSeGuardaConIdExcepcion() throws Exception {
        pedir(ENTRENADOR, "?fecha=" + MARTES + "&idExcepcion=" + EXTRA_SUB12)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesion.horaInicio").value("18:00"))
                .andExpect(jsonPath("$.sesion.sede").value("UVA El Paraíso"));
        guardar(ENTRENADOR, cuerpo(MARTES, "\"idExcepcion\":\"" + EXTRA_SUB12 + "\"",
                "[" + registro(NINO_1, "JUSTIFICADO") + "]")).andExpect(status().isOk());
    }

    @Test
    void elJsonDeRespuestaTieneExactamenteLosCamposDelContratoYLosNulosSalenComoNull() throws Exception {
        // Semillero "por turnos" con grupo: el administrador lo ve; idEntrenador y entrenador salen null
        MvcResult resultado = pedir(ADMIN, "?fecha=" + MARTES + "&idFranja=" + FRANJA_SEMILLERO).andExpect(status().isOk())
                .andReturn();
        JsonNode raiz = json.readTree(resultado.getResponse().getContentAsString());

        assertThat(nombres(raiz)).containsExactly("sesion", "puedeRegistrar", "deportistas");
        JsonNode sesion = raiz.get("sesion");
        assertThat(nombres(sesion)).containsExactly("fecha", "horaInicio", "horaFin", "idCategoria", "categoria", "sede",
                "grupo", "idEntrenador", "entrenador");
        assertThat(sesion.get("fecha").asText()).isEqualTo(MARTES);
        assertThat(sesion.get("horaInicio").asText()).isEqualTo("16:00");
        assertThat(sesion.get("horaFin").asText()).isEqualTo("17:30");
        assertThat(sesion.get("idCategoria").asText()).isEqualTo(CAT_SEMILLERO.toString());
        assertThat(sesion.get("categoria").asText()).isEqualTo("Semillero");
        assertThat(sesion.get("sede").asText()).isEqualTo("Horizontes");
        assertThat(sesion.get("grupo").asText()).isEqualTo("tarde");
        assertThat(sesion.get("idEntrenador").isNull()).isTrue();
        assertThat(sesion.get("entrenador").isNull()).isTrue();
        assertThat(raiz.get("puedeRegistrar").isBoolean()).isTrue();
        JsonNode sinMarcas = raiz.get("deportistas").get(0);
        assertThat(nombres(sinMarcas)).containsExactly("idDeportista", "nombre", "apellido", "estado", "observaciones");
        assertThat(sinMarcas.get("idDeportista").asText()).isEqualTo(NINO_1.toString());
        assertThat(sinMarcas.get("nombre").asText()).isEqualTo("Ana");
        assertThat(sinMarcas.get("apellido").asText()).isEqualTo("Alfa");
        assertThat(sinMarcas.get("estado").isNull()).isTrue();
        assertThat(sinMarcas.get("observaciones").isNull()).isTrue();
        JsonNode conMarcas = raiz.get("deportistas").get(1);
        assertThat(conMarcas.get("estado").asText()).isEqualTo("AUSENTE");
        assertThat(conMarcas.get("observaciones").asText()).isEqualTo("Sin aviso");
    }

    @Test
    void laSesionSinGrupoNiEntrenadorLoDejaComoNullEnElJsonTambienEnElPut() throws Exception {
        FranjaHorario sinGrupo = franja(FRANJA_SEMILLERO, CAT_SEMILLERO, "Semillero", null, null, null);
        when(franjas.buscarPorId(FRANJA_SEMILLERO)).thenReturn(Optional.of(sinGrupo));

        MvcResult resultado = guardar(ADMIN, cuerpo(MARTES, "\"idFranja\":\"" + FRANJA_SEMILLERO + "\"",
                "[" + registro(NINO_1, "PRESENTE") + "]")).andExpect(status().isOk()).andReturn();

        JsonNode sesion = json.readTree(resultado.getResponse().getContentAsString()).get("sesion");
        assertThat(sesion.has("grupo")).isTrue();
        assertThat(sesion.get("grupo").isNull()).isTrue();
        assertThat(sesion.get("idEntrenador").isNull()).isTrue();
    }

    private static List<String> nombres(JsonNode nodo) {
        List<String> nombres = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    // --- 400 ------------------------------------------------------------------------------------------------

    @Test
    void unaFechaFuturaEs400ConElCampoFechaEnElGetYEnElPut() throws Exception {
        error(pedir(ADMIN, "?fecha=" + MARTES_FUTURO + "&idFranja=" + FRANJA_SUB12), 400,
                "fecha: " + ReglasAsistencia.FECHA_FUTURA).andExpect(jsonPath("$.campos[0]").value("fecha"));
        error(guardar(ADMIN, cuerpo(MARTES_FUTURO, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "]")), 400,
                "fecha: " + ReglasAsistencia.FECHA_FUTURA).andExpect(jsonPath("$.campos[0]").value("fecha"));
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void ningunaSesionOLasDosSonUn400ConLosDosCampos() throws Exception {
        pedir(ADMIN, "?fecha=" + MARTES).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[0]").value("idExcepcion"))
                .andExpect(jsonPath("$.campos[1]").value("idFranja"))
                .andExpect(jsonPath("$.mensaje").value(containsString("exactamente uno")));
        pedir(ADMIN, "?fecha=" + MARTES + "&idFranja=" + FRANJA_SUB12 + "&idExcepcion=" + EXTRA_SUB12)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.length()").value(2));
        guardar(ADMIN, cuerpo(MARTES, "\"idFranja\":null", "[" + registro(NINO_1, "PRESENTE") + "]"))
                .andExpect(status().isBadRequest());
        guardar(ADMIN, cuerpo(MARTES, franjaSub12() + ",\"idExcepcion\":\"" + EXTRA_SUB12 + "\"",
                "[" + registro(NINO_1, "PRESENTE") + "]")).andExpect(status().isBadRequest());
    }

    @Test
    void laFechaAusenteOInvalidaEs400() throws Exception {
        pedir(ADMIN, "?idFranja=" + FRANJA_SUB12).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("fecha"));
        pedir(ADMIN, "?fecha=2026-02-30&idFranja=" + FRANJA_SUB12).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("AAAA-MM-DD")));
    }

    @Test
    void unDiaQueNoCoincideConLaFranjaEs400ConElCampoFecha() throws Exception {
        pedir(ADMIN, "?fecha=2026-11-04&idFranja=" + FRANJA_SUB12).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("fecha"));
    }

    @Test
    void unEstadoInvalidoUnDeportistaRepetidoYUnoFueraDeLaListaSon400EnRegistros() throws Exception {
        String invalido = cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "TARDE") + "]");
        String repetido = cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "," + registro(NINO_1, "AUSENTE") + "]");
        String fuera = cuerpo(MARTES, franjaSub12(), "[" + registro(UUID.randomUUID(), "PRESENTE") + "]");

        for (String cuerpo : List.of(invalido, repetido, fuera)) {
            guardar(ENTRENADOR, cuerpo).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.length()").value(1))
                    .andExpect(jsonPath("$.campos[0]").value("registros"))
                    .andExpect(jsonPath("$.mensaje").value(containsString("registros: ")));
        }
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void sinRegistrosOConObservacionesLargasSon400() throws Exception {
        guardar(ADMIN, cuerpo(MARTES, franjaSub12(), "[]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("registros"));
        guardar(ADMIN, cuerpo(MARTES, franjaSub12(), "[{\"idDeportista\":\"" + NINO_1
                + "\",\"estado\":\"PRESENTE\",\"observaciones\":\"" + "a".repeat(301) + "\"}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0]").value("registros"));
    }

    @Test
    void unCuerpoVacioOUnJsonMalFormadoNoSonUn500() throws Exception {
        guardar(ADMIN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos").isArray());
        guardar(ADMIN, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(4));
        error(guardar(ADMIN, "{\"fecha\":"), 400, "El cuerpo de la petición falta o no es un JSON válido");
        error(guardar(ADMIN, "esto no es json"), 400, "El cuerpo de la petición falta o no es un JSON válido");
        guardar(ADMIN, "{\"registros\":\"no es una lista\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("registros"));
    }

    // --- 404 y 409 ------------------------------------------------------------------------------------------

    @Test
    void unaFranjaOUnExtraInexistentesSon404() throws Exception {
        UUID otro = UUID.randomUUID();

        error(pedir(ADMIN, "?fecha=" + MARTES + "&idFranja=" + otro), 404, "No existe una franja con ese id.");
        error(pedir(ADMIN, "?fecha=" + MARTES + "&idExcepcion=" + otro), 404, "No existe un extra con ese id.");
        error(guardar(ADMIN, cuerpo(MARTES, "\"idFranja\":\"" + otro + "\"", "[" + registro(NINO_1, "PRESENTE") + "]")), 404,
                "No existe una franja con ese id.");
        error(guardar(ADMIN, cuerpo(MARTES, "\"idExcepcion\":\"" + otro + "\"", "[" + registro(NINO_1, "PRESENTE") + "]")),
                404, "No existe un extra con ese id.");
    }

    @Test
    void unaSesionCanceladaEs409ConElCampoFecha() throws Exception {
        when(asistencias.franjaCancelada(FRANJA_SUB12, LocalDate.parse(MARTES))).thenReturn(true);

        error(pedir(ADMIN, "?fecha=" + MARTES + "&idFranja=" + FRANJA_SUB12), 409, ReglasAsistencia.SESION_CANCELADA)
                .andExpect(jsonPath("$.campos[0]").value("fecha"));
        error(guardar(ADMIN, cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "]")), 409,
                ReglasAsistencia.SESION_CANCELADA).andExpect(jsonPath("$.campos[0]").value("fecha"));
    }

    @Test
    void unSolapeConOtroEntrenamientoEs409SinCampos() throws Exception {
        when(asistencias.buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any()))
                .thenThrow(new SolapeDeEntrenamientoException());

        error(guardar(ENTRENADOR, cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "]")), 409,
                ReglasAsistencia.SOLAPE).andExpect(jsonPath("$.campos").doesNotExist());
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void elGetNoCreaNadaYElPutPasaQuienPasoLista() throws Exception {
        pedir(ENTRENADOR, "?fecha=" + MARTES + "&idFranja=" + FRANJA_SUB12).andExpect(status().isOk());
        verify(asistencias, never()).buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any());

        guardar(ENTRENADOR, cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "]")).andExpect(status().isOk());
        guardar(ADMIN, cuerpo(MARTES, franjaSub12(), "[" + registro(NINO_1, "PRESENTE") + "]")).andExpect(status().isOk());

        verify(asistencias).guardar(eq(ENTRENAMIENTO), eq(FILA_ENTRENADOR), any());
        verify(asistencias).guardar(eq(ENTRENAMIENTO), eq(null), any());
    }
}
