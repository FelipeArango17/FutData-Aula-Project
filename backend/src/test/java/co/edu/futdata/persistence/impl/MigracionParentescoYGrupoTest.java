package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Las reglas que deja la migración 008 (parentesco del acudiente, grupo y activo del deportista) contra un
 * PostgreSQL real. Cada rechazo se comprueba por el nombre de la restricción.
 */
@ExtendWith(DockerDisponible.class)
class MigracionParentescoYGrupoTest {

    private static final List<String> PARENTESCOS = List.of("Madre", "Padre", "Abuelo", "Abuela", "Tia", "Tio", "Otro");

    private NamedParameterJdbcTemplate jdbc;
    private int documentos;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
    }

    @AfterEach
    void limpiarTodo() {
        PostgresDePrueba.limpiarDatosDeDeportistas();
        jdbc.getJdbcOperations().update("DELETE FROM acudiente");
        PostgresDePrueba.limpiar();
    }

    // --- Parentesco -----------------------------------------------------------------------------------------

    @Test
    void aceptaLosSieteParentescos() {
        for (String parentesco : PARENTESCOS) {
            assertThatCode(() -> enlazar(parentesco)).as(parentesco).doesNotThrowAnyException();
        }
        assertThat(jdbc.getJdbcOperations().queryForList(
                "SELECT parentesco FROM acudiente_deportista", String.class)).containsExactlyInAnyOrderElementsOf(PARENTESCOS);
    }

    @Test
    void rechazaUnParentescoQueNoEsDeLaLista() {
        for (String parentesco : List.of("Primo", "Tía", "madre")) {
            assertThatThrownBy(() -> enlazar(parentesco)).as(parentesco)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("acudiente_deportista_parentesco_ck");
        }
    }

    @Test
    void elParentescoEsOpcional() {
        assertThatCode(() -> enlazar(null)).doesNotThrowAnyException();
    }

    // --- Grupo ----------------------------------------------------------------------------------------------

    @Test
    void aceptaGrupoMananaTardeYNulo() {
        assertThatCode(() -> insertarDeportista("mañana")).doesNotThrowAnyException();
        assertThatCode(() -> insertarDeportista("tarde")).doesNotThrowAnyException();
        assertThatCode(() -> insertarDeportista(null)).doesNotThrowAnyException();
    }

    @Test
    void rechazaOtroGrupo() {
        for (String grupo : List.of("noche", "manana", "Mañana")) {
            assertThatThrownBy(() -> insertarDeportista(grupo)).as(grupo)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("deportista_grupo_ck");
        }
    }

    // --- Activo ---------------------------------------------------------------------------------------------

    @Test
    void unDeportistaNuevoQuedaActivo() {
        UUID id = insertarDeportista(null);

        assertThat(activo(id)).isTrue();
    }

    /**
     * El arnés aplica todas las migraciones juntas (no se puede parar en la 007), así que el {@code update} de la
     * 008 se toma del archivo y se corre sobre un deportista RETIRADA y otro ACTIVA.
     */
    @Test
    void elUpdateDeLaMigracionDejaInactivosSoloALosRetirados() throws IOException {
        UUID entrenador = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        UUID categoria = PostgresDePrueba.categoria("Semillero", entrenador);
        UUID retirado = insertarDeportista(null);
        UUID vigente = insertarDeportista(null);
        jdbc.update("UPDATE deportista SET estado_inscripcion = 'RETIRADA' WHERE id_deportista = :id",
                new MapSqlParameterSource("id", retirado));
        jdbc.update("UPDATE deportista SET estado_inscripcion = 'ACTIVA', id_categoria = :c WHERE id_deportista = :id",
                new MapSqlParameterSource("id", vigente).addValue("c", categoria));

        jdbc.getJdbcOperations().update(updateDeLaMigracion());

        assertThat(activo(retirado)).isFalse();
        assertThat(activo(vigente)).isTrue();
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------

    private static String updateDeLaMigracion() throws IOException {
        String sql = Files.readString(PostgresDePrueba.MIGRACIONES.resolve("008_parentesco_y_grupo.sql"),
                StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("update public\\.deportista set activo = false[^;]*;").matcher(sql);
        assertThat(m.find()).as("la 008 tiene el update de activo").isTrue();
        return m.group();
    }

    private boolean activo(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT activo FROM deportista WHERE id_deportista = :id",
                new MapSqlParameterSource("id", id), Boolean.class));
    }

    private UUID insertarDeportista(String grupo) {
        documentos++;
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, grupo)
                VALUES ('Pedro', 'Gómez', 'TI', :documento, :nacimiento, :grupo)
                RETURNING id_deportista""",
                new MapSqlParameterSource("documento", String.valueOf(1098765000 + documentos))
                        .addValue("nacimiento", LocalDate.of(2017, 3, 3)).addValue("grupo", grupo), UUID.class);
    }

    private void enlazar(String parentesco) {
        documentos++;
        UUID deportista = insertarDeportista(null);
        UUID acudiente = jdbc.queryForObject("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento)
                VALUES ('Marta', 'Gómez', 'CC', :documento, :nacimiento)
                RETURNING id_acudiente""",
                new MapSqlParameterSource("documento", String.valueOf(43000000 + documentos))
                        .addValue("nacimiento", LocalDate.of(1985, 1, 20)), UUID.class);
        jdbc.update("""
                INSERT INTO acudiente_deportista (id_acudiente, id_deportista, parentesco)
                VALUES (:acudiente, :deportista, :parentesco)""",
                new MapSqlParameterSource("acudiente", acudiente).addValue("deportista", deportista)
                        .addValue("parentesco", parentesco));
    }
}
