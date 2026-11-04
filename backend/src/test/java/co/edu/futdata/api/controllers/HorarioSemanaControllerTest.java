package co.edu.futdata.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.Destinatario;
import co.edu.futdata.persistence.dao.IDestinatariosDAO;
import co.edu.futdata.persistence.dao.FranjaYaCanceladaException;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IExcepcionHorarioDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.util.security.JwtValidator;
import co.edu.futdata.util.security.RespuestasSeguridad;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * /api/horario-semana por HTTP con la cadena de seguridad real (JWT, 2FA y roles, como en {@code SeguridadTest}), el
 * servicio real y solo el DAO de franjas simulado. El reloj es ajustable para probar miércoles, sábado y domingo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class HorarioSemanaControllerTest {

    private static final String RUTA = "/api/horario-semana";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-00000000007e");
    private static final UUID SIN_ROL = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    /** Franja de Semillero, martes, "por turnos": de una categoría que NO es del entrenador de las pruebas. */
    private static final UUID FRANJA_SEMILLERO = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID CAT_SEMILLERO = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    /** Franja de Sub-12, sábado: de la categoría del entrenador de las pruebas. */
    private static final UUID FRANJA_SUB12 = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID CAT_SUB12 = UUID.fromString("c0000000-0000-0000-0000-000000000002");
    private static final UUID FILA_ENTRENADOR = UUID.fromString("e0000000-0000-0000-0000-00000000000e");
    private static final UUID FILA_INACTIVO = UUID.fromString("e0000000-0000-0000-0000-0000000000ff");
    private static final String SABADO = "2026-11-07";
    private static final String MARTES = "2026-11-10";

    /** Reloj que cada prueba puede mover; por defecto, miércoles 4 de noviembre de 2026 en Bogotá. */
    static class RelojAjustable extends Clock {
        volatile Instant ahora = Instant.parse("2026-11-04T15:00:00Z");

        @Override
        public ZoneId getZone() {
            return BOGOTA;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return Clock.fixed(ahora, zona);
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @TestConfiguration
    static class Configuracion {
        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable();
        }

        /** Sin base de datos: las anotaciones {@code @Transactional} del servicio real no abren conexión. */
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
    private RelojAjustable reloj;

    @MockitoBean
    private JwtDecoder decoder;

    @MockitoBean
    private IUsuarioDAO usuarios;

    @MockitoBean
    private ISesionVerificadaDAO sesiones;

    @MockitoBean
    private IFranjaHorarioDAO franjas;

    @MockitoBean
    private IExcepcionHorarioDAO excepciones;

    @MockitoBean
    private IEntrenadorDAO entrenadores;

    @MockitoBean
    private ICategoriaDAO categorias;

    @MockitoBean
    private IDestinatariosDAO destinatarios;

    @MockitoBean
    private INotificador notificador;

    @BeforeEach
    void preparar() {
        reloj.ahora = Instant.parse("2026-11-04T15:00:00Z");
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
        FranjaHorario semillero = franja(FRANJA_SEMILLERO, CAT_SEMILLERO, "Semillero", 2, "16:30", "18:00", "tarde");
        FranjaHorario sub12 = franja(FRANJA_SUB12, CAT_SUB12, "Sub-12", 6, "08:00", "09:00", null);
        when(franjas.listar(null, null, null)).thenReturn(List.of(semillero, sub12));
        when(franjas.buscarPorId(FRANJA_SEMILLERO)).thenReturn(Optional.of(semillero));
        when(franjas.buscarPorId(FRANJA_SUB12)).thenReturn(Optional.of(sub12));
        // El usuario ENTRENADOR es el entrenador de Sub-12
        Entrenador fila = new Entrenador();
        fila.setId(FILA_ENTRENADOR);
        when(entrenadores.buscarPorIdUsuario(ENTRENADOR)).thenReturn(Optional.of(fila));
        when(categorias.listarPorEntrenador(FILA_ENTRENADOR))
                .thenReturn(List.of(new Categoria(CAT_SUB12, "Sub-12", 12, 12, FILA_ENTRENADOR)));
        when(entrenadores.buscarPorId(FILA_ENTRENADOR)).thenReturn(Optional.of(fila));
        Entrenador inactivo = new Entrenador();
        inactivo.setId(FILA_INACTIVO);
        inactivo.setActivo(false);
        when(entrenadores.buscarPorId(FILA_INACTIVO)).thenReturn(Optional.of(inactivo));
        when(franjas.nombreDeCategoria(CAT_SUB12)).thenReturn(Optional.of("Sub-12"));
        when(franjas.nombreDeCategoria(CAT_SEMILLERO)).thenReturn(Optional.of("Semillero"));
    }

    private void token(String valor, UUID sub) {
        when(decoder.decode(valor)).thenReturn(Jwt.withTokenValue(valor).header("alg", "ES256").subject(sub.toString())
                .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private static FranjaHorario franja(UUID id, UUID idCategoria, String categoria, int dia, String inicio, String fin,
                                        String grupo) {
        FranjaHorario f = new FranjaHorario();
        f.setId(id);
        f.setIdCategoria(idCategoria);
        f.setCategoria(categoria);
        f.setDia(dia);
        f.setHoraInicio(LocalTime.parse(inicio));
        f.setHoraFin(LocalTime.parse(fin));
        f.setSede("Horizontes");
        f.setGrupo(grupo);
        return f;
    }

    private ResultActions pedir(UUID usuario, String consulta) throws Exception {
        return mvc.perform(get(RUTA + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    private static ResultActions error(ResultActions respuesta, int estado, String mensaje) throws Exception {
        return respuesta
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.ruta").value(RUTA))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void sinTokenEs401() throws Exception {
        error(mvc.perform(get(RUTA)), 401, RespuestasSeguridad.SIN_SESION);
    }

    @Test
    void conTokenVencidoEs401() throws Exception {
        error(mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-vencido")), 401,
                RespuestasSeguridad.SIN_SESION);
    }

    @Test
    void sinHaberCompletadoElDosFactoresEs403() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ADMIN), any())).thenReturn(false);

        pedir(ADMIN, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
    }

    @Test
    void unaCuentaSinRolEs403() throws Exception {
        error(pedir(SIN_ROL, ""), 403, RespuestasSeguridad.SIN_PERMISO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACUDIENTE", "ADMINISTRADOR", "ENTRENADOR", "TESORERO"})
    void losCuatroRolesLeenLaSemanaEnCurso(String rol) throws Exception {
        UUID usuario = switch (rol) {
            case "ACUDIENTE" -> ACUDIENTE;
            case "ADMINISTRADOR" -> ADMIN;
            case "ENTRENADOR" -> ENTRENADOR;
            default -> TESORERO;
        };

        pedir(usuario, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-11-02"))
                .andExpect(jsonPath("$.hasta").value("2026-11-07"))
                .andExpect(jsonPath("$.sesiones.length()").value(2))
                .andExpect(jsonPath("$.sesiones[0].fecha").value("2026-11-03"))
                .andExpect(jsonPath("$.sesiones[0].dia").value(2))
                .andExpect(jsonPath("$.sesiones[0].tipo").value("FRANJA"))
                .andExpect(jsonPath("$.sesiones[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.sesiones[0].categoria").value("Semillero"))
                .andExpect(jsonPath("$.sesiones[0].horaInicio").value("16:30"))
                .andExpect(jsonPath("$.sesiones[0].grupo").value("tarde"))
                .andExpect(jsonPath("$.sesiones[0].idExcepcion").doesNotExist())
                .andExpect(jsonPath("$.sesiones[1].fecha").value("2026-11-07"));
    }

    @Test
    void laFechaInvalidaEs400ConElCampoFecha() throws Exception {
        pedir(ADMIN, "?fecha=2026-02-30")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("fecha"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("AAAA-MM-DD")));
        pedir(ACUDIENTE, "?fecha=hoy").andExpect(status().isBadRequest());
    }

    @Test
    void elAdministradorPideCualquierSemana() throws Exception {
        pedir(ADMIN, "?fecha=2027-01-04")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2027-01-04"))
                .andExpect(jsonPath("$.sesiones[0].fecha").value("2027-01-05"));
    }

    @Test
    void elAcudienteQueAPideOtraSemanaEs403ConElMensajeDelContrato() throws Exception {
        error(pedir(ACUDIENTE, "?fecha=2026-11-09"), 403, "Solo puedes consultar la semana en curso");
        error(pedir(ACUDIENTE, "?fecha=2026-10-28"), 403, "Solo puedes consultar la semana en curso");
    }

    @Test
    void elAcudientePideSuSemanaConFechaDeCualquierDiaDeEsaSemana() throws Exception {
        pedir(ACUDIENTE, "?fecha=2026-11-05").andExpect(status().isOk()).andExpect(jsonPath("$.desde").value("2026-11-02"));
        pedir(ACUDIENTE, "?fecha=2026-11-02").andExpect(status().isOk());
    }

    @Test
    void elSabadoLaSemanaEnCursoSigueSiendoLaMisma() throws Exception {
        reloj.ahora = Instant.parse("2026-11-07T15:00:00Z");

        pedir(ACUDIENTE, "").andExpect(status().isOk()).andExpect(jsonPath("$.desde").value("2026-11-02"));
        pedir(ACUDIENTE, "?fecha=2026-11-07").andExpect(status().isOk());
        error(pedir(ACUDIENTE, "?fecha=2026-11-09"), 403, "Solo puedes consultar la semana en curso");
    }

    @Test
    void elDomingoLaSemanaEnCursoEsLaSiguienteYLaAnteriorSeCierraAlAcudiente() throws Exception {
        reloj.ahora = Instant.parse("2026-11-08T15:00:00Z");

        pedir(ACUDIENTE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-11-09"))
                .andExpect(jsonPath("$.hasta").value("2026-11-14"));
        pedir(ACUDIENTE, "?fecha=2026-11-09").andExpect(status().isOk());
        error(pedir(ACUDIENTE, "?fecha=2026-11-04"), 403, "Solo puedes consultar la semana en curso");
        pedir(ADMIN, "?fecha=2026-11-04").andExpect(status().isOk()).andExpect(jsonPath("$.desde").value("2026-11-02"));
    }

    // --- Cancelaciones en la semana y puedeGestionar --------------------------------------------------------

    private static ExcepcionHorario cancelacion(UUID id, UUID franja, UUID categoria, String nombre, int dia, String fecha,
                                                String motivo) {
        return new ExcepcionHorario(id, ExcepcionHorario.CANCELACION, LocalDate.parse(fecha), franja, categoria, nombre, dia,
                LocalTime.of(8, 0), LocalTime.of(9, 0), "Horizontes", null, null, null, null, motivo, null);
    }

    @Test
    void laSemanaMuestraLaFranjaCanceladaConSuIdYMotivo() throws Exception {
        UUID id = UUID.randomUUID();
        when(excepciones.listar(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-07")))
                .thenReturn(List.of(cancelacion(id, FRANJA_SUB12, CAT_SUB12, "Sub-12", 6, SABADO, "Lluvia")));

        pedir(ADMIN, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesiones[0].categoria").value("Semillero"))
                .andExpect(jsonPath("$.sesiones[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.sesiones[0].idExcepcion").doesNotExist())
                .andExpect(jsonPath("$.sesiones[1].categoria").value("Sub-12"))
                .andExpect(jsonPath("$.sesiones[1].tipo").value("FRANJA"))
                .andExpect(jsonPath("$.sesiones[1].estado").value("CANCELADO"))
                .andExpect(jsonPath("$.sesiones[1].idExcepcion").value(id.toString()))
                .andExpect(jsonPath("$.sesiones[1].motivo").value("Lluvia"));
    }

    @Test
    void puedeGestionarDependeDelRol() throws Exception {
        reloj.ahora = Instant.parse("2026-11-02T15:00:00Z"); // lunes: ninguna sesión de la semana es pasada
        // Administrador: todo. Entrenador (de Sub-12): solo Sub-12. Acudiente y tesorero: nada
        pedir(ADMIN, "").andExpect(jsonPath("$.sesiones[0].puedeGestionar").value(true))
                .andExpect(jsonPath("$.sesiones[1].puedeGestionar").value(true));
        pedir(ENTRENADOR, "").andExpect(jsonPath("$.sesiones[0].puedeGestionar").value(false))
                .andExpect(jsonPath("$.sesiones[1].puedeGestionar").value(true));
        pedir(ACUDIENTE, "").andExpect(jsonPath("$.sesiones[0].puedeGestionar").value(false))
                .andExpect(jsonPath("$.sesiones[1].puedeGestionar").value(false));
        pedir(TESORERO, "").andExpect(jsonPath("$.sesiones[0].puedeGestionar").value(false))
                .andExpect(jsonPath("$.sesiones[1].puedeGestionar").value(false));
    }

    // --- POST /cancelaciones --------------------------------------------------------------------------------

    private ResultActions cancelar(UUID usuario, String cuerpo) throws Exception {
        return mvc.perform(post(RUTA + "/cancelaciones").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String cuerpo(UUID franja, String fecha, String motivo) {
        return "{\"fecha\": " + (fecha == null ? "null" : "\"" + fecha + "\"") + ", \"idFranja\": "
                + (franja == null ? "null" : "\"" + franja + "\"") + ", \"motivo\": "
                + (motivo == null ? "null" : "\"" + motivo + "\"") + "}";
    }

    private static ResultActions errorEn(ResultActions respuesta, int estado, String mensaje, String ruta) throws Exception {
        return respuesta
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.ruta").value(ruta));
    }

    private void seCreaLaCancelacion(UUID id, UUID franja, UUID categoria, String nombre, int dia, String fecha, String motivo) {
        when(excepciones.crearCancelacion(any(), any(), any(), any())).thenReturn(id);
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(cancelacion(id, franja, categoria, nombre, dia, fecha, motivo)));
    }

    @Test
    void elAdministradorCancelaCualquierFranjaYResponde201ConLaSesionCancelada() throws Exception {
        UUID id = UUID.randomUUID();
        seCreaLaCancelacion(id, FRANJA_SEMILLERO, CAT_SEMILLERO, "Semillero", 2, MARTES, "Lluvia");

        cancelar(ADMIN, cuerpo(FRANJA_SEMILLERO, MARTES, "  Lluvia  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesion.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.sesion.tipo").value("FRANJA"))
                .andExpect(jsonPath("$.sesion.idExcepcion").value(id.toString()))
                .andExpect(jsonPath("$.sesion.idFranja").value(FRANJA_SEMILLERO.toString()))
                .andExpect(jsonPath("$.sesion.fecha").value(MARTES))
                .andExpect(jsonPath("$.sesion.motivo").value("Lluvia"))
                .andExpect(jsonPath("$.sesion.puedeGestionar").value(true))
                .andExpect(jsonPath("$.avisoEnviado").hasJsonPath())
                .andExpect(jsonPath("$.avisoEnviado").value(org.hamcrest.Matchers.nullValue()));
        // El motivo se guarda normalizado y creadoPor es el sub del token
        verify(excepciones).crearCancelacion(FRANJA_SEMILLERO, LocalDate.parse(MARTES), "Lluvia", ADMIN);
    }

    @Test
    void elEntrenadorDuenoCancelaSuFranja() throws Exception {
        when(excepciones.esDuenoDeFranja(ENTRENADOR, FRANJA_SUB12)).thenReturn(true);
        seCreaLaCancelacion(UUID.randomUUID(), FRANJA_SUB12, CAT_SUB12, "Sub-12", 6, SABADO, null);

        cancelar(ENTRENADOR, cuerpo(FRANJA_SUB12, SABADO, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesion.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.sesion.motivo").value(org.hamcrest.Matchers.nullValue()));
        verify(excepciones).crearCancelacion(FRANJA_SUB12, LocalDate.parse(SABADO), null, ENTRENADOR);
    }

    @Test
    void elEntrenadorAjenoEs403ConElMensajeDeLaRegla() throws Exception {
        when(excepciones.esDuenoDeFranja(any(), any())).thenReturn(false);

        errorEn(cancelar(ENTRENADOR, cuerpo(FRANJA_SUB12, SABADO, null)), 403,
                "Solo puedes modificar el horario de tus categorías", RUTA + "/cancelaciones");
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void unaFranjaPorTurnosDeUnaCategoriaAjenaEs403ParaElEntrenador() throws Exception {
        when(excepciones.esDuenoDeFranja(ENTRENADOR, FRANJA_SEMILLERO)).thenReturn(false);

        errorEn(cancelar(ENTRENADOR, cuerpo(FRANJA_SEMILLERO, MARTES, null)), 403,
                "Solo puedes modificar el horario de tus categorías", RUTA + "/cancelaciones");
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void elTesoreroYElAcudienteNoPuedenCancelar() throws Exception {
        for (UUID rol : List.of(TESORERO, ACUDIENTE)) {
            errorEn(cancelar(rol, cuerpo(FRANJA_SUB12, SABADO, null)), 403, RespuestasSeguridad.SIN_PERMISO,
                    RUTA + "/cancelaciones");
        }
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void cancelarSinTokenEs401YSinDosFactoresEs403() throws Exception {
        errorEn(mvc.perform(post(RUTA + "/cancelaciones").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(FRANJA_SUB12, SABADO, null))), 401, RespuestasSeguridad.SIN_SESION, RUTA + "/cancelaciones");

        when(sesiones.estaVerificada(any(), eq(ADMIN), any())).thenReturn(false);
        cancelar(ADMIN, cuerpo(FRANJA_SUB12, SABADO, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void unCuerpoInvalidoEs400ConTodosLosCamposQueFallan() throws Exception {
        cancelar(ADMIN, "{\"fecha\": \"mañana\", \"idFranja\": \"no-es-uuid\", \"motivo\": \"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", org.hamcrest.Matchers.containsInAnyOrder("fecha", "idFranja", "motivo")));
        cancelar(ADMIN, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", org.hamcrest.Matchers.containsInAnyOrder("fecha", "idFranja")));
        cancelar(ADMIN, "").andExpect(status().isBadRequest());
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void unaFranjaQueNoExisteEs404() throws Exception {
        errorEn(cancelar(ADMIN, cuerpo(UUID.randomUUID(), SABADO, null)), 404, "No existe una franja con ese id.",
                RUTA + "/cancelaciones");
    }

    @Test
    void unaFechaPasadaOQueNoEsElDiaDeLaFranjaEs400ConElCampoFecha() throws Exception {
        // Sábado 31 de octubre: pasada. Viernes 6 y domingo 8 de noviembre: la franja es de los sábados
        for (String fecha : List.of("2026-10-31", "2026-11-06", "2026-11-08")) {
            cancelar(ADMIN, cuerpo(FRANJA_SUB12, fecha, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0]").value("fecha"));
        }
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void cancelarDosVecesLaMismaFranjaEs409() throws Exception {
        when(excepciones.crearCancelacion(any(), any(), any(), any())).thenThrow(new FranjaYaCanceladaException(new RuntimeException()));

        errorEn(cancelar(ADMIN, cuerpo(FRANJA_SUB12, SABADO, null)), 409, "Esa franja ya está cancelada en esa fecha",
                RUTA + "/cancelaciones")
                .andExpect(jsonPath("$.campos[0]").value("fecha"));
    }

    // --- DELETE /cancelaciones/{id} -------------------------------------------------------------------------

    private ResultActions deshacer(UUID usuario, Object id) throws Exception {
        return mvc.perform(delete(RUTA + "/cancelaciones/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    @Test
    void elAdministradorDeshaceYLaFranjaVuelveAEstarActiva() throws Exception {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id))
                .thenReturn(Optional.of(cancelacion(id, FRANJA_SEMILLERO, CAT_SEMILLERO, "Semillero", 2, MARTES, "Lluvia")));

        deshacer(ADMIN, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesion.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.sesion.tipo").value("FRANJA"))
                .andExpect(jsonPath("$.sesion.idExcepcion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sesion.motivo").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sesion.fecha").value(MARTES))
                .andExpect(jsonPath("$.sesion.idFranja").value(FRANJA_SEMILLERO.toString()))
                .andExpect(jsonPath("$.avisoEnviado").hasJsonPath())
                .andExpect(jsonPath("$.avisoEnviado").value(org.hamcrest.Matchers.nullValue()));
        verify(excepciones).eliminar(id);
    }

    @Test
    void elEntrenadorDuenoDeshaceYElAjenoEs403() throws Exception {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id))
                .thenReturn(Optional.of(cancelacion(id, FRANJA_SUB12, CAT_SUB12, "Sub-12", 6, SABADO, null)));

        when(excepciones.esDuenoDeExcepcion(ENTRENADOR, id)).thenReturn(false);
        errorEn(deshacer(ENTRENADOR, id), 403, "Solo puedes modificar el horario de tus categorías",
                RUTA + "/cancelaciones/" + id);
        verify(excepciones, never()).eliminar(any());

        when(excepciones.esDuenoDeExcepcion(ENTRENADOR, id)).thenReturn(true);
        deshacer(ENTRENADOR, id).andExpect(status().isOk()).andExpect(jsonPath("$.sesion.estado").value("ACTIVO"));
        verify(excepciones).eliminar(id);
    }

    @Test
    void deshacerUnaCancelacionInexistenteOUnExtraEs404() throws Exception {
        UUID noExiste = UUID.randomUUID();
        UUID idExtra = UUID.randomUUID();
        when(excepciones.buscarPorId(idExtra)).thenReturn(Optional.of(new ExcepcionHorario(idExtra, ExcepcionHorario.EXTRA,
                LocalDate.parse(MARTES), null, CAT_SUB12, "Sub-12", null, LocalTime.of(18, 0), LocalTime.of(19, 0), "Horizontes",
                null, null, null, null, null, null)));

        for (UUID id : List.of(noExiste, idExtra)) {
            errorEn(deshacer(ADMIN, id), 404, "No existe una cancelación con ese id.", RUTA + "/cancelaciones/" + id);
        }
        verify(excepciones, never()).eliminar(any());
    }

    @Test
    void noSeDeshaceUnaCancelacionDeFechaPasada() throws Exception {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id))
                .thenReturn(Optional.of(cancelacion(id, FRANJA_SUB12, CAT_SUB12, "Sub-12", 6, "2026-10-31", null)));

        deshacer(ADMIN, id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0]").value("fecha"));
        verify(excepciones, never()).eliminar(any());
    }

    @Test
    void deshacerConUnIdQueNoEsUuidEs400YElTesoreroYElAcudienteSon403() throws Exception {
        deshacer(ADMIN, "no-es-uuid").andExpect(status().isBadRequest());
        for (UUID rol : List.of(TESORERO, ACUDIENTE)) {
            deshacer(rol, UUID.randomUUID()).andExpect(status().isForbidden());
        }
        verify(excepciones, never()).eliminar(any());
    }

    @Test
    void puedeGestionarEsFalsoEnLasFechasAnterioresAHoyParaTodosLosRoles() throws Exception {
        // Miércoles 4: la franja de Semillero es del martes 3 (pasada); la de Sub-12, del sábado 7
        for (UUID rol : List.of(ADMIN, ENTRENADOR)) {
            pedir(rol, "").andExpect(jsonPath("$.sesiones[0].puedeGestionar").value(false));
        }
        pedir(ADMIN, "").andExpect(jsonPath("$.sesiones[1].puedeGestionar").value(true));
        // La misma franja de Semillero, en la semana siguiente (futura), sí
        pedir(ADMIN, "?fecha=2026-11-10").andExpect(jsonPath("$.sesiones[0].puedeGestionar").value(true));
    }

    // --- Extras en la semana --------------------------------------------------------------------------------

    private static ExcepcionHorario extra(UUID id, UUID categoria, String nombre, String fecha, UUID entrenador,
                                          String nombreEntrenador, Boolean activo) {
        return new ExcepcionHorario(id, ExcepcionHorario.EXTRA, LocalDate.parse(fecha), null, categoria, nombre, null,
                LocalTime.of(18, 0), LocalTime.of(19, 30), "UVA El Paraíso", entrenador, nombreEntrenador, activo, null,
                "Partido", null);
    }

    @Test
    void laSemanaIncluyeLosExtrasOrdenadosConLasFranjas() throws Exception {
        UUID id = UUID.randomUUID();
        when(excepciones.listar(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-07")))
                .thenReturn(List.of(extra(id, CAT_SUB12, "Sub-12", "2026-11-05", FILA_ENTRENADOR, "Luis Mora", false)));

        pedir(ADMIN, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesiones.length()").value(3))
                .andExpect(jsonPath("$.sesiones[0].tipo").value("FRANJA"))
                .andExpect(jsonPath("$.sesiones[1].tipo").value("EXTRA"))
                .andExpect(jsonPath("$.sesiones[1].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.sesiones[1].idFranja").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sesiones[1].idExcepcion").value(id.toString()))
                .andExpect(jsonPath("$.sesiones[1].fecha").value("2026-11-05"))
                .andExpect(jsonPath("$.sesiones[1].dia").value(4))
                .andExpect(jsonPath("$.sesiones[1].horaInicio").value("18:00"))
                .andExpect(jsonPath("$.sesiones[1].horaFin").value("19:30"))
                .andExpect(jsonPath("$.sesiones[1].categoria").value("Sub-12"))
                .andExpect(jsonPath("$.sesiones[1].sede").value("UVA El Paraíso"))
                .andExpect(jsonPath("$.sesiones[1].entrenador").value("Luis Mora"))
                .andExpect(jsonPath("$.sesiones[1].entrenadorActivo").value(false))
                .andExpect(jsonPath("$.sesiones[1].motivo").value("Partido"))
                .andExpect(jsonPath("$.sesiones[1].puedeGestionar").value(true))
                .andExpect(jsonPath("$.sesiones[2].tipo").value("FRANJA"));
    }

    @Test
    void elEntrenadorSoloGestionaLosExtrasDeSusCategoriasYUnExtraPasadoNoSeGestionaNiElAdministrador() throws Exception {
        when(excepciones.listar(any(), any())).thenReturn(List.of(
                extra(UUID.randomUUID(), CAT_SEMILLERO, "Semillero", "2026-11-05", FILA_ENTRENADOR, "Luis Mora", true), // lo dirige él
                extra(UUID.randomUUID(), CAT_SUB12, "Sub-12", "2026-11-06", null, null, null),
                extra(UUID.randomUUID(), CAT_SUB12, "Sub-12", "2026-11-03", null, null, null))); // pasado
        // Orden: 11-03 pasado (Sub-12), 11-03 franja Semillero (martes), 11-05, 11-06, 11-07 franja

        for (UUID quien : List.of(ADMIN, ENTRENADOR)) {
            String esperadoSemillero = quien.equals(ADMIN) ? "true" : "false"; // por ser asignado no es de Luis
            pedir(quien, "").andExpect(status().isOk())
                    .andExpect(jsonPath("$.sesiones[?(@.tipo=='EXTRA' && @.categoria=='Semillero')].puedeGestionar")
                            .value(org.hamcrest.Matchers.contains(Boolean.valueOf(esperadoSemillero))))
                    .andExpect(jsonPath("$.sesiones[?(@.tipo=='EXTRA' && @.fecha=='2026-11-06')].puedeGestionar")
                            .value(org.hamcrest.Matchers.contains(true)))
                    .andExpect(jsonPath("$.sesiones[?(@.tipo=='EXTRA' && @.fecha=='2026-11-03')].puedeGestionar")
                            .value(org.hamcrest.Matchers.contains(false)));
        }
        pedir(ACUDIENTE, "").andExpect(jsonPath("$.sesiones[?(@.puedeGestionar==true)]").isEmpty());
        pedir(TESORERO, "").andExpect(jsonPath("$.sesiones[?(@.puedeGestionar==true)]").isEmpty());
    }

    // --- POST /extras ---------------------------------------------------------------------------------------

    private ResultActions crearExtra(UUID usuario, String cuerpo) throws Exception {
        return mvc.perform(post(RUTA + "/extras").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String textoJson(Object valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    private static String extraJson(UUID categoria, String fecha, String inicio, String fin, String sede, UUID entrenador,
                                    String grupo, String motivo) {
        return "{\"idCategoria\": " + textoJson(categoria) + ", \"fecha\": " + textoJson(fecha) + ", \"horaInicio\": "
                + textoJson(inicio) + ", \"horaFin\": " + textoJson(fin) + ", \"sede\": " + textoJson(sede)
                + ", \"idEntrenador\": " + textoJson(entrenador) + ", \"grupo\": " + textoJson(grupo)
                + ", \"motivo\": " + textoJson(motivo) + "}";
    }

    private static String sub12Valido() {
        return extraJson(CAT_SUB12, "2026-11-11", "18:00", "19:30", "Horizontes", FILA_ENTRENADOR, null, "  Partido  ");
    }

    private UUID seCreaElExtra(UUID categoria, String nombre, String fecha, UUID entrenador, String nombreEntrenador) {
        UUID id = UUID.randomUUID();
        when(excepciones.crearExtra(any())).thenReturn(id);
        when(excepciones.buscarPorId(id))
                .thenReturn(Optional.of(extra(id, categoria, nombre, fecha, entrenador, nombreEntrenador, entrenador == null ? null : true)));
        return id;
    }

    @Test
    void elAdministradorCreaUnExtraYResponde201ConLaSesion() throws Exception {
        UUID id = seCreaElExtra(CAT_SUB12, "Sub-12", "2026-11-11", FILA_ENTRENADOR, "Luis Mora");

        crearExtra(ADMIN, sub12Valido())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesion.tipo").value("EXTRA"))
                .andExpect(jsonPath("$.sesion.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.sesion.idFranja").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sesion.idExcepcion").value(id.toString()))
                .andExpect(jsonPath("$.sesion.fecha").value("2026-11-11"))
                .andExpect(jsonPath("$.sesion.dia").value(3))
                .andExpect(jsonPath("$.sesion.categoria").value("Sub-12"))
                .andExpect(jsonPath("$.sesion.entrenador").value("Luis Mora"))
                .andExpect(jsonPath("$.sesion.puedeGestionar").value(true))
                .andExpect(jsonPath("$.avisoEnviado").hasJsonPath())
                .andExpect(jsonPath("$.avisoEnviado").value(org.hamcrest.Matchers.nullValue()));
        org.mockito.ArgumentCaptor<ExcepcionHorario> captura = org.mockito.ArgumentCaptor.forClass(ExcepcionHorario.class);
        verify(excepciones).crearExtra(captura.capture());
        assertThat(captura.getValue().creadoPor()).isEqualTo(ADMIN);
        assertThat(captura.getValue().motivo()).isEqualTo("Partido");
        assertThat(captura.getValue().idCategoria()).isEqualTo(CAT_SUB12);
    }

    @Test
    void elEntrenadorDuenoDeLaCategoriaCreaElExtra() throws Exception {
        when(excepciones.esDuenoDeCategoria(ENTRENADOR, CAT_SUB12)).thenReturn(true);
        seCreaElExtra(CAT_SUB12, "Sub-12", "2026-11-11", FILA_ENTRENADOR, "Luis Mora");

        crearExtra(ENTRENADOR, sub12Valido()).andExpect(status().isCreated());
        org.mockito.ArgumentCaptor<ExcepcionHorario> captura = org.mockito.ArgumentCaptor.forClass(ExcepcionHorario.class);
        verify(excepciones).crearExtra(captura.capture());
        assertThat(captura.getValue().creadoPor()).isEqualTo(ENTRENADOR);
    }

    @Test
    void elEntrenadorNoCreaExtrasEnUnaCategoriaAjenaAunqueSeaElAsignado() throws Exception {
        when(excepciones.esDuenoDeCategoria(any(), any())).thenReturn(false);

        errorEn(crearExtra(ENTRENADOR, extraJson(CAT_SEMILLERO, "2026-11-11", "18:00", "19:00", "Horizontes", FILA_ENTRENADOR,
                null, null)), 403, "Solo puedes modificar el horario de tus categorías", RUTA + "/extras");
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void elTesoreroYElAcudienteNoPuedenCrearExtras() throws Exception {
        for (UUID rol : List.of(TESORERO, ACUDIENTE)) {
            errorEn(crearExtra(rol, sub12Valido()), 403, RespuestasSeguridad.SIN_PERMISO, RUTA + "/extras");
        }
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void crearExtraSinTokenEs401YSinDosFactoresEs403() throws Exception {
        errorEn(mvc.perform(post(RUTA + "/extras").contentType(MediaType.APPLICATION_JSON).content(sub12Valido())), 401,
                RespuestasSeguridad.SIN_SESION, RUTA + "/extras");

        when(sesiones.estaVerificada(any(), eq(ADMIN), any())).thenReturn(false);
        crearExtra(ADMIN, sub12Valido()).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
    }

    @Test
    void todosLosErroresDelCuerpoSalenJuntosEnUn400() throws Exception {
        crearExtra(ADMIN, "{\"idCategoria\": \"x\", \"fecha\": \"mañana\", \"horaInicio\": \"25:00\", \"horaFin\": \"\","
                + " \"sede\": \"Cancha 3\", \"idEntrenador\": \"y\", \"grupo\": \"noche\", \"motivo\": \"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", org.hamcrest.Matchers.containsInAnyOrder("idCategoria", "fecha", "horaInicio",
                        "horaFin", "sede", "idEntrenador", "grupo", "motivo")));
        crearExtra(ADMIN, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", org.hamcrest.Matchers.containsInAnyOrder("idCategoria", "fecha", "horaInicio",
                        "horaFin", "sede")));
        // La hora de fin anterior y el grupo en una categoría que no lo admite
        crearExtra(ADMIN, extraJson(CAT_SUB12, "2026-11-11", "19:00", "18:00", "Horizontes", null, "tarde", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", org.hamcrest.Matchers.containsInAnyOrder("horaFin", "grupo")));
        crearExtra(ADMIN, "").andExpect(status().isBadRequest());
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void unaCategoriaQueNoExisteEs404() throws Exception {
        errorEn(crearExtra(ADMIN, extraJson(UUID.randomUUID(), "2026-11-11", "18:00", "19:00", "Horizontes", null, null, null)),
                404, "No existe una categoría con ese id.", RUTA + "/extras");
    }

    @Test
    void unaFechaPasadaOEnDomingoEs400ConElCampoFecha() throws Exception {
        for (String fecha : List.of("2026-11-03", "2026-11-08")) {
            crearExtra(ADMIN, extraJson(CAT_SUB12, fecha, "18:00", "19:00", "Horizontes", null, null, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0]").value("fecha"));
        }
        // Hoy cuenta como no pasada
        seCreaElExtra(CAT_SUB12, "Sub-12", "2026-11-04", null, null);
        crearExtra(ADMIN, extraJson(CAT_SUB12, "2026-11-04", "18:00", "19:00", "Horizontes", null, null, null))
                .andExpect(status().isCreated());
    }

    @Test
    void unEntrenadorAsignadoQueNoExisteOEstaInactivoEs400ConElCampoIdEntrenador() throws Exception {
        crearExtra(ADMIN, extraJson(CAT_SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", UUID.randomUUID(), null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("idEntrenador"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no existe un entrenador con ese id")));
        crearExtra(ADMIN, extraJson(CAT_SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", FILA_INACTIVO, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("idEntrenador"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("el entrenador está inactivo")));
    }

    @Test
    void elCruceConUnaFranjaDelEntrenadorEs409ConElMensajeDeLasFranjas() throws Exception {
        when(excepciones.hayFranjaQueSeCruza(any(), any(), any(), any())).thenReturn(true);

        errorEn(crearExtra(ADMIN, sub12Valido()), 409, "Ese entrenador ya tiene otra franja que se cruza con este horario",
                RUTA + "/extras")
                .andExpect(jsonPath("$.campos[0]").value("idEntrenador"));
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void elCruceConOtroExtraQueDetectaLaBaseEsElMismo409() throws Exception {
        when(excepciones.crearExtra(any())).thenThrow(new CruceDeHorarioException(new RuntimeException()));

        errorEn(crearExtra(ADMIN, sub12Valido()), 409, "Ese entrenador ya tiene otra franja que se cruza con este horario",
                RUTA + "/extras")
                .andExpect(jsonPath("$.campos[0]").value("idEntrenador"));
    }

    @Test
    void unExtraPorTurnosNoCompruebaCruces() throws Exception {
        seCreaElExtra(CAT_SEMILLERO, "Semillero", "2026-11-11", null, null);

        crearExtra(ADMIN, extraJson(CAT_SEMILLERO, "2026-11-11", "18:00", "19:00", "Horizontes", null, "tarde", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesion.idEntrenador").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sesion.entrenador").value(org.hamcrest.Matchers.nullValue()));
        verify(excepciones, never()).hayFranjaQueSeCruza(any(), any(), any(), any());
    }

    // --- DELETE /extras/{id} --------------------------------------------------------------------------------

    private ResultActions eliminarExtra(UUID usuario, Object id) throws Exception {
        return mvc.perform(delete(RUTA + "/extras/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    @Test
    void elAdministradorEliminaUnExtraYResponde200ConSesionNull() throws Exception {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(extra(id, CAT_SUB12, "Sub-12", "2026-11-11", null, null, null)));

        eliminarExtra(ADMIN, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesion").hasJsonPath())
                .andExpect(jsonPath("$.sesion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.avisoEnviado").hasJsonPath())
                .andExpect(jsonPath("$.avisoEnviado").value(org.hamcrest.Matchers.nullValue()));
        verify(excepciones).eliminar(id);
    }

    @Test
    void elEntrenadorDuenoEliminaYElAjenoEs403() throws Exception {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(extra(id, CAT_SUB12, "Sub-12", "2026-11-11", null, null, null)));

        when(excepciones.esDuenoDeExcepcion(ENTRENADOR, id)).thenReturn(false);
        errorEn(eliminarExtra(ENTRENADOR, id), 403, "Solo puedes modificar el horario de tus categorías", RUTA + "/extras/" + id);
        verify(excepciones, never()).eliminar(any());

        when(excepciones.esDuenoDeExcepcion(ENTRENADOR, id)).thenReturn(true);
        eliminarExtra(ENTRENADOR, id).andExpect(status().isOk());
        verify(excepciones).eliminar(id);
    }

    @Test
    void eliminarUnExtraQueNoExisteOUnaCancelacionEs404() throws Exception {
        UUID noExiste = UUID.randomUUID();
        UUID idCancelacion = UUID.randomUUID();
        when(excepciones.buscarPorId(idCancelacion)).thenReturn(Optional.of(
                cancelacion(idCancelacion, FRANJA_SEMILLERO, CAT_SEMILLERO, "Semillero", 2, MARTES, null)));

        for (UUID id : List.of(noExiste, idCancelacion)) {
            errorEn(eliminarExtra(ADMIN, id), 404, "No existe un extra con ese id.", RUTA + "/extras/" + id);
        }
        verify(excepciones, never()).eliminar(any());
    }

    @Test
    void noSeEliminaUnExtraDeFechaPasadaYLosDemasRolesSon403() throws Exception {
        UUID pasado = UUID.randomUUID();
        when(excepciones.buscarPorId(pasado)).thenReturn(Optional.of(extra(pasado, CAT_SUB12, "Sub-12", "2026-11-03", null, null, null)));

        eliminarExtra(ADMIN, pasado).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0]").value("fecha"));
        eliminarExtra(ADMIN, "no-es-uuid").andExpect(status().isBadRequest());
        for (UUID rol : List.of(TESORERO, ACUDIENTE)) {
            eliminarExtra(rol, UUID.randomUUID()).andExpect(status().isForbidden());
        }
        verify(excepciones, never()).eliminar(any());
    }

    // --- avisoEnviado ---------------------------------------------------------------------------------------

    /** Las cuatro acciones de escritura, cada una con sus datos simulados, para probar el aviso en todas. */
    private List<ResultActions> lasCuatroAcciones() throws Exception {
        UUID cancelacion = UUID.randomUUID();
        UUID paraDeshacer = UUID.randomUUID();
        UUID paraEliminar = UUID.randomUUID();
        seCreaLaCancelacion(cancelacion, FRANJA_SUB12, CAT_SUB12, "Sub-12", 6, SABADO, "Lluvia");
        when(excepciones.buscarPorId(paraDeshacer)).thenReturn(Optional.of(
                cancelacion(paraDeshacer, FRANJA_SEMILLERO, CAT_SEMILLERO, "Semillero", 2, MARTES, null)));
        when(excepciones.buscarPorId(paraEliminar)).thenReturn(Optional.of(
                extra(paraEliminar, CAT_SUB12, "Sub-12", "2026-11-11", null, null, null)));
        UUID extraCreado = UUID.randomUUID();
        when(excepciones.crearExtra(any())).thenReturn(extraCreado);
        when(excepciones.buscarPorId(extraCreado))
                .thenReturn(Optional.of(extra(extraCreado, CAT_SUB12, "Sub-12", "2026-11-11", FILA_ENTRENADOR, "Luis Mora", true)));
        return List.of(
                cancelar(ADMIN, cuerpo(FRANJA_SUB12, SABADO, "Lluvia")).andExpect(status().isCreated()),
                deshacer(ADMIN, paraDeshacer).andExpect(status().isOk()),
                crearExtra(ADMIN, sub12Valido()).andExpect(status().isCreated()),
                eliminarExtra(ADMIN, paraEliminar).andExpect(status().isOk()));
    }

    @Test
    void sinAcudientesALosQueAvisarAvisoEnviadoEsNullEnLasCuatroAcciones() throws Exception {
        when(destinatarios.acudientesDeCategoria(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        for (ResultActions accion : lasCuatroAcciones()) {
            accion.andExpect(jsonPath("$.avisoEnviado").hasJsonPath())
                    .andExpect(jsonPath("$.avisoEnviado").value(org.hamcrest.Matchers.nullValue()));
        }
        verify(notificador, never()).notificar(any(), any());
    }

    @Test
    void cuandoN8nAceptaElAvisoAvisoEnviadoEsTrueEnLasCuatroAcciones() throws Exception {
        when(destinatarios.acudientesDeCategoria(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new Destinatario("maria@correo.co", "María Torres")));

        for (ResultActions accion : lasCuatroAcciones()) {
            accion.andExpect(jsonPath("$.avisoEnviado").value(true));
        }
        verify(notificador, org.mockito.Mockito.times(4)).notificar(eq("CAMBIO_ENTRENAMIENTO"), any());
    }

    @Test
    void siElAvisoFallaLaRespuestaSigueSiendoExitosaYAvisoEnviadoEsFalseEnLasCuatroAcciones() throws Exception {
        when(destinatarios.acudientesDeCategoria(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new Destinatario("maria@correo.co", "María Torres")));
        org.mockito.Mockito.doThrow(new NotificacionNoEnviadaException("tiempo agotado", "CAMBIO_ENTRENAMIENTO", null, null))
                .when(notificador).notificar(any(), any());

        // lasCuatroAcciones() ya exige 201 o 200 en cada una: el fallo del aviso no cambia el código de la respuesta
        List<ResultActions> acciones = lasCuatroAcciones();
        for (ResultActions accion : acciones) {
            accion.andExpect(jsonPath("$.avisoEnviado").value(false));
        }
        // y la operación se hizo igual
        verify(excepciones).crearCancelacion(any(), any(), any(), any());
        verify(excepciones).crearExtra(any());
        verify(excepciones, org.mockito.Mockito.times(2)).eliminar(any());
    }
}
