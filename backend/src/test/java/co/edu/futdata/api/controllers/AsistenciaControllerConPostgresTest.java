package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.util.security.JwtValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * /api/asistencia/sesion de punta a punta por HTTP con la cadena de seguridad real, el servicio y los DAO reales y un
 * PostgreSQL real (todas las migraciones): GET sin marcas, PUT con dos deportistas, GET con marcas, PUT corrigiendo uno y
 * GET con la corrección; como administrador y como entrenador en una franja "por turnos".
 */
@ExtendWith(DockerDisponible.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class AsistenciaControllerConPostgresTest {

    private static final String RUTA = "/api/asistencia/sesion";
    private static final String MARTES = "2026-11-03";

    @TestConfiguration
    static class Configuracion {
        /** Miércoles 4 de noviembre de 2026, 10:00 en Bogotá. */
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Bogota"));
        }

        /** La base real de las pruebas en lugar del puerto cerrado de pruebas.properties. */
        @Bean
        @Primary
        DataSource baseDePruebas() {
            return PostgresDePrueba.dataSource();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;

    private NamedParameterJdbcTemplate jdbc;
    private UUID usuarioAdmin;
    private UUID usuarioAna;
    private UUID ana;
    private UUID sub12;
    private UUID semillero;
    private UUID franjaSub12;
    private UUID franjaPorTurnos;
    private int contador;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiar();
        UUID luis = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        ana = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        usuarioAdmin = cuenta("asistencia-http-admin@pradofc.co", "ADMINISTRADOR");
        usuarioAna = cuenta("asistencia-http-ana@pradofc.co", "ENTRENADOR");
        jdbc.update("UPDATE entrenador SET id_usuario = :u WHERE id_entrenador = :e",
                new MapSqlParameterSource("u", usuarioAna).addValue("e", ana));
        sub12 = PostgresDePrueba.categoria("Sub-12", luis);
        semillero = PostgresDePrueba.categoria("Semillero", ana);
        franjaSub12 = franja(sub12, luis);
        franjaPorTurnos = franja(semillero, null);
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
    }

    @AfterEach
    void limpiar() {
        jdbc.getJdbcOperations().update("DELETE FROM asistencia");
        jdbc.getJdbcOperations().update("DELETE FROM entrenamiento");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");
        jdbc.getJdbcOperations().update("DELETE FROM deportista WHERE documento_identidad LIKE '1096%'");
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'asistencia-http-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
        PostgresDePrueba.limpiar();
    }

    @Test
    void elAdministradorPasaListaYCorrigeEnUnaFranjaDeSubDoce() throws Exception {
        UUID alfa = deportista("Ana", "Alfa", 2014);
        UUID beta = deportista("Beto", "Beta", 2014);

        recorrido(usuarioAdmin, franjaSub12, alfa, beta);

        // el administrador no es entrenador: nadie queda como quien pasó lista; el asignado es el de la franja
        assertCount("SELECT count(*) FROM asistencia WHERE id_entrenador IS NULL", 2);
        assertCount("SELECT count(*) FROM entrenamiento WHERE id_entrenador IS NOT NULL", 1);
    }

    @Test
    void elEntrenadorPasaListaYCorrigeEnUnaFranjaPorTurnos() throws Exception {
        UUID alfa = deportista("Ana", "Alfa", 2016);
        UUID beta = deportista("Beto", "Beta", 2016);

        recorrido(usuarioAna, franjaPorTurnos, alfa, beta);

        // "por turnos": el entrenamiento no tiene entrenador asignado, pero la asistencia sí quien pasó lista
        assertCount("SELECT count(*) FROM entrenamiento WHERE id_entrenador IS NULL", 1);
        assertCount("SELECT count(*) FROM asistencia WHERE id_entrenador IS NOT NULL", 2);
    }

    @Test
    void elAdministradorEnUnaFranjaPorTurnosDejaTodoEnNull() throws Exception {
        UUID alfa = deportista("Ana", "Alfa", 2016);
        UUID beta = deportista("Beto", "Beta", 2016);

        recorrido(usuarioAdmin, franjaPorTurnos, alfa, beta);

        assertCount("SELECT count(*) FROM entrenamiento WHERE id_entrenador IS NULL", 1);
        assertCount("SELECT count(*) FROM asistencia WHERE id_entrenador IS NULL", 2);
    }

    /** GET sin marcas -> PUT con 2 deportistas -> GET con marcas -> PUT corrigiendo uno -> GET con la corrección. */
    private void recorrido(UUID usuario, UUID franja, UUID alfa, UUID beta) throws Exception {
        String consulta = "?fecha=" + MARTES + "&idFranja=" + franja;

        leer(usuario, consulta)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeRegistrar").value(true))
                .andExpect(jsonPath("$.deportistas.length()").value(2))
                .andExpect(jsonPath("$.deportistas[0].idDeportista").value(alfa.toString()))
                .andExpect(jsonPath("$.deportistas[0].estado").value((Object) null))
                .andExpect(jsonPath("$.deportistas[1].idDeportista").value(beta.toString()))
                .andExpect(jsonPath("$.deportistas[1].estado").value((Object) null));
        assertCount("SELECT count(*) FROM entrenamiento", 0); // el GET no crea nada

        guardar(usuario, franja, "[" + registro(alfa, "PRESENTE", null) + "," + registro(beta, "AUSENTE", "Sin aviso") + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deportistas[0].estado").value("PRESENTE"))
                .andExpect(jsonPath("$.deportistas[1].estado").value("AUSENTE"))
                .andExpect(jsonPath("$.deportistas[1].observaciones").value("Sin aviso"));

        leer(usuario, consulta)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deportistas[0].estado").value("PRESENTE"))
                .andExpect(jsonPath("$.deportistas[0].observaciones").value((Object) null))
                .andExpect(jsonPath("$.deportistas[1].estado").value("AUSENTE"))
                .andExpect(jsonPath("$.deportistas[1].observaciones").value("Sin aviso"));

        guardar(usuario, franja, "[" + registro(beta, "JUSTIFICADO", "Cita médica") + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deportistas[1].estado").value("JUSTIFICADO"));

        leer(usuario, consulta)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deportistas[0].estado").value("PRESENTE"))
                .andExpect(jsonPath("$.deportistas[1].estado").value("JUSTIFICADO"))
                .andExpect(jsonPath("$.deportistas[1].observaciones").value("Cita médica"));
        assertCount("SELECT count(*) FROM entrenamiento", 1);
        assertCount("SELECT count(*) FROM asistencia", 2);
    }

    @Test
    void unRegistroInvalidoPorHttpNoDejaNadaEnLaBase() throws Exception {
        UUID alfa = deportista("Ana", "Alfa", 2014);

        guardar(usuarioAdmin, franjaSub12, "[" + registro(alfa, "PRESENTE", null) + "," + registro(UUID.randomUUID(),
                "PRESENTE", null) + "]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("registros"));

        assertCount("SELECT count(*) FROM entrenamiento", 0);
        assertCount("SELECT count(*) FROM asistencia", 0);
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------

    private ResultActions leer(UUID usuario, String consulta) throws Exception {
        return mvc.perform(get(RUTA + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    private ResultActions guardar(UUID usuario, UUID franja, String registros) throws Exception {
        return mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + MARTES + "\",\"idFranja\":\"" + franja + "\",\"registros\":" + registros + "}"));
    }

    private static String registro(UUID id, String estado, String observaciones) {
        return "{\"idDeportista\":\"" + id + "\",\"estado\":\"" + estado + "\""
                + (observaciones == null ? "" : ",\"observaciones\":\"" + observaciones + "\"") + "}";
    }

    private void assertCount(String sql, int esperado) {
        org.assertj.core.api.Assertions.assertThat(jdbc.getJdbcOperations().queryForObject(sql, Integer.class))
                .isEqualTo(esperado);
    }

    /** Una cuenta con rol y un token que el decodificador simulado acepta. */
    private UUID cuenta(String correo, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", new MapSqlParameterSource("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, 'Cuenta', 'Prueba', :c)",
                new MapSqlParameterSource("id", id).addValue("c", correo));
        when(usuarios.rolesActivos(id)).thenReturn(List.of(rol));
        when(decoder.decode("token-" + id)).thenReturn(Jwt.withTokenValue("token-" + id).header("alg", "ES256")
                .subject(id.toString()).claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
        return id;
    }

    /** Martes 16:00-17:30. */
    private UUID franja(UUID categoria, UUID entrenador) {
        return jdbc.queryForObject("""
                INSERT INTO franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, id_entrenador)
                VALUES (:cat, 2, :inicio, :fin, 'Horizontes', :entrenador) RETURNING id_franja""",
                new MapSqlParameterSource("cat", categoria).addValue("inicio", LocalTime.of(16, 0))
                        .addValue("fin", LocalTime.of(17, 30)).addValue("entrenador", entrenador),
                UUID.class);
    }

    private UUID deportista(String nombre, String apellido, int anioNacimiento) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento)
                VALUES (:nombre, :apellido, 'TI', :doc, :nacimiento) RETURNING id_deportista""",
                new MapSqlParameterSource("nombre", nombre).addValue("apellido", apellido)
                        .addValue("doc", "1096" + (++contador)).addValue("nacimiento", LocalDate.of(anioNacimiento, 6, 15)),
                UUID.class);
    }
}
