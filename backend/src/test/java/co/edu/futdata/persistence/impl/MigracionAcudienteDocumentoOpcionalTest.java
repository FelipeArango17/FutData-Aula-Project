package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Lo que deja la migración 011 (documento y fecha de nacimiento del acudiente opcionales) contra un PostgreSQL real.
 * Cada rechazo se comprueba por el nombre de la restricción.
 */
@ExtendWith(DockerDisponible.class)
class MigracionAcudienteDocumentoOpcionalTest {

    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
    }

    @AfterEach
    void limpiarTodo() {
        for (String tabla : List.of("auditoria", "notificacion_pago", "pago", "documento", "acudiente_deportista",
                "acudiente", "deportista")) {
            jdbc.getJdbcOperations().update("DELETE FROM " + tabla);
        }
    }

    @Test
    void lasTresColumnasYaNoExigenValor() {
        List<String> obligatorias = jdbc.getJdbcOperations().queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'acudiente' AND is_nullable = 'NO'
                 ORDER BY column_name""", String.class);

        assertThat(obligatorias).doesNotContain("tipo_documento", "documento_identidad", "fecha_nacimiento")
                .contains("id_acudiente", "nombre", "apellido");
    }

    @Test
    void seGuardaUnAcudienteSinDocumentoNiFechaDeNacimiento() {
        assertThatCode(() -> insertar("a@prueba.co", null, null, null)).doesNotThrowAnyException();

        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT count(*) FROM acudiente
                 WHERE tipo_documento IS NULL AND documento_identidad IS NULL AND fecha_nacimiento IS NULL""",
                Long.class)).isEqualTo(1);
    }

    @Test
    void variosAcudientesSinDocumentoNoChocanEntreSi() {
        insertar("a@prueba.co", null, null, null);

        assertThatCode(() -> insertar("b@prueba.co", null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void elUnicoDeTipoYDocumentoSigueValiendoCuandoVienenLosDos() {
        insertar("a@prueba.co", "CC", "123456", null);

        assertThatThrownBy(() -> insertar("b@prueba.co", "CC", "123456", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("acudiente_tipo_documento_documento_identidad_key");
        // el mismo número con otro tipo sí es otra persona
        assertThatCode(() -> insertar("c@prueba.co", "CE", "123456", null)).doesNotThrowAnyException();
    }

    @Test
    void elTipoDeDocumentoQueNoEsDeLaListaSigueRechazado() {
        assertThatThrownBy(() -> insertar("a@prueba.co", "RC", "123456", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("acudiente_tipo_documento_ck");
    }

    @Test
    void elCorreoSigueSiendoUnicoSinDistinguirMayusculas() {
        insertar("Mama@Prueba.co", null, null, null);

        assertThatThrownBy(() -> insertar("mama@prueba.co", null, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("acudiente_correo_key");
    }

    @Test
    void correrLaMigracionOtraVezNoFallaNiCambiaNada() throws IOException {
        String sql = Files.readString(PostgresDePrueba.MIGRACIONES.resolve("011_acudiente_documento_opcional.sql"),
                StandardCharsets.UTF_8);
        insertar("a@prueba.co", null, null, null);

        assertThatCode(() -> jdbc.getJdbcOperations().execute(sql)).doesNotThrowAnyException();

        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM acudiente", Long.class)).isEqualTo(1);
        assertThatCode(() -> insertar("b@prueba.co", null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void laMigracionEstaEnElDirectorio() {
        assertThat(Path.of(PostgresDePrueba.MIGRACIONES.toString(), "011_acudiente_documento_opcional.sql")).exists();
    }

    private void insertar(String correo, String tipo, String documento, String fechaNacimiento) {
        jdbc.update("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo)
                VALUES ('Acu', 'Diente', :tipo, :documento, CAST(:fecha AS date), :correo)""",
                new MapSqlParameterSource("tipo", tipo).addValue("documento", documento)
                        .addValue("fecha", fechaNacimiento).addValue("correo", correo));
    }
}
