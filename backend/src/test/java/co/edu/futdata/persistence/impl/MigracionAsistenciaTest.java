package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * La migración 012 contra un PostgreSQL real: el entrenador de {@code entrenamiento} y de {@code asistencia} admite
 * NULL, lo demás de las dos tablas sigue igual y volver a correrla no cambia nada.
 */
@ExtendWith(DockerDisponible.class)
class MigracionAsistenciaTest {

    private NamedParameterJdbcTemplate jdbc;
    private UUID luis;
    private UUID sub12;
    private UUID deportista;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        luis = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        sub12 = PostgresDePrueba.categoria("Sub-12", luis);
        deportista = jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, id_categoria)
                VALUES ('Ana', 'Pérez', 'TI', '1099000001', date '2014-03-01', :categoria)
                RETURNING id_deportista""", new MapSqlParameterSource("categoria", sub12), UUID.class);
    }

    @AfterEach
    void limpiarTodo() {
        jdbc.getJdbcOperations().update("DELETE FROM asistencia");
        jdbc.getJdbcOperations().update("DELETE FROM entrenamiento");
        jdbc.getJdbcOperations().update("DELETE FROM deportista WHERE documento_identidad = '1099000001'");
        PostgresDePrueba.limpiar();
    }

    @Test
    void elEntrenamientoAdmiteEntrenadorNuloYAsistenciaTambien() {
        UUID entrenamiento = entrenamiento("2026-11-04", "16:00", null);

        assertThatCode(() -> asistencia(entrenamiento, null)).doesNotThrowAnyException();

        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT count(*) FROM asistencia WHERE id_entrenador IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void conEntrenadorSigueSiendoUnaReferenciaValida() {
        assertThatThrownBy(() -> entrenamiento("2026-11-04", "16:00", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID entrenamiento = entrenamiento("2026-11-04", "16:00", luis);

        assertThatThrownBy(() -> asistencia(entrenamiento, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> asistencia(entrenamiento, luis)).doesNotThrowAnyException();
    }

    @Test
    void sigueUnaSolaAsistenciaPorDeportistaYSesion() {
        UUID entrenamiento = entrenamiento("2026-11-04", "16:00", null);
        asistencia(entrenamiento, null);

        assertThatThrownBy(() -> asistencia(entrenamiento, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elEntrenamientoSinEntrenadorNoChocaConOtroSinEntrenadorPeroLaCategoriaSiSeCruza() {
        entrenamiento("2026-11-04", "16:00", null);

        // otra categoría a la misma hora y sede: cabe (no hay unique por lugar, fecha y hora)
        UUID sub13 = PostgresDePrueba.categoria("Sub-13", luis);
        assertThatCode(() -> jdbc.update("""
                INSERT INTO entrenamiento (id_categoria, id_entrenador, fecha, hora_inicio, hora_fin, lugar)
                VALUES (:categoria, NULL, date '2026-11-04', time '16:00', time '17:30', 'Horizontes')""",
                new MapSqlParameterSource("categoria", sub13))).doesNotThrowAnyException();
        // la misma categoría con otra hora que se cruza: la exclusión la rechaza
        assertThatThrownBy(() -> entrenamiento("2026-11-04", "17:00", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("entrenamiento_cruce_categoria_ex");
    }

    @Test
    void volverACorrerLaMigracionNoCambiaNada() throws Exception {
        String sql = Files.readString(PostgresDePrueba.MIGRACIONES.resolve("012_asistencia_entrenador_opcional.sql"));
        UUID entrenamiento = entrenamiento("2026-11-04", "16:00", null);
        asistencia(entrenamiento, null);

        assertThatCode(() -> jdbc.getJdbcOperations().execute(sql)).doesNotThrowAnyException();

        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM asistencia", Integer.class)).isEqualTo(1);
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_name IN ('entrenamiento', 'asistencia') AND column_name = 'id_entrenador'
                   AND is_nullable = 'YES'""", Integer.class)).isEqualTo(2);
    }

    private UUID entrenamiento(String fecha, String hora, UUID entrenador) {
        return jdbc.queryForObject("""
                INSERT INTO entrenamiento (id_categoria, id_entrenador, fecha, hora_inicio, hora_fin, lugar)
                VALUES (:categoria, :entrenador, CAST(:fecha AS date), CAST(:hora AS time),
                        CAST(:hora AS time) + interval '90 minutes', 'Horizontes')
                RETURNING id_entrenamiento""",
                new MapSqlParameterSource("categoria", sub12).addValue("entrenador", entrenador)
                        .addValue("fecha", fecha).addValue("hora", hora),
                UUID.class);
    }

    private void asistencia(UUID entrenamiento, UUID entrenador) {
        jdbc.update("""
                INSERT INTO asistencia (id_deportista, id_entrenador, id_entrenamiento, estado)
                VALUES (:deportista, :entrenador, :entrenamiento, 'PRESENTE')""",
                new MapSqlParameterSource("deportista", deportista).addValue("entrenador", entrenador)
                        .addValue("entrenamiento", entrenamiento));
    }
}
