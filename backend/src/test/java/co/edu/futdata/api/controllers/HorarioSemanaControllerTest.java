package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.util.security.JwtValidator;
import co.edu.futdata.util.security.RespuestasSeguridad;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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
        when(franjas.listar(null, null, null)).thenReturn(List.of(
                franja("Semillero", 2, "16:30", "18:00", "tarde"),
                franja("Sub-12", 6, "08:00", "09:00", null)));
    }

    private void token(String valor, UUID sub) {
        when(decoder.decode(valor)).thenReturn(Jwt.withTokenValue(valor).header("alg", "ES256").subject(sub.toString())
                .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private static FranjaHorario franja(String categoria, int dia, String inicio, String fin, String grupo) {
        FranjaHorario f = new FranjaHorario();
        f.setId(UUID.randomUUID());
        f.setIdCategoria(UUID.randomUUID());
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
                .andExpect(jsonPath("$.sesiones[0].puedeGestionar").value(false))
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
}
