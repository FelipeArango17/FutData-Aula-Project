package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Las reglas que deja la migración 007 (categorías del club, registro civil, sedes y horario fijo) contra un
 * PostgreSQL real. Cada rechazo se comprueba por el nombre de la restricción, que es lo que usará el backend para
 * traducirlo a un mensaje.
 */
@ExtendWith(DockerDisponible.class)
class MigracionCategoriasYHorarioTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);

    private NamedParameterJdbcTemplate jdbc;
    private UUID diaz;
    private UUID mora;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        diaz = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        mora = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
    }

    /** Lo que insertan estas pruebas no debe estorbar a las demás, que borran categorías y entrenadores. */
    @AfterEach
    void limpiarTodo() {
        jdbc.getJdbcOperations().update("DELETE FROM entrenamiento");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");
        jdbc.getJdbcOperations().update("DELETE FROM deportista");
        PostgresDePrueba.limpiar();
    }

    // --- Categorías -----------------------------------------------------------------------------------------

    @Test
    void soloExistenLasCategoriasDelClubPorAnioDeNacimiento() {
        assertThatCode(() -> PostgresDePrueba.categoria("Párvulos", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> PostgresDePrueba.categoria("Cachorros", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> PostgresDePrueba.categoria("Sub-9", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> PostgresDePrueba.categoria("Sub-16", diaz)).doesNotThrowAnyException();
    }

    @Test
    void sub8YSub17YaNoSonCategorias() {
        for (String nombre : new String[] {"Sub-8", "Sub-17", "Semillero"}) {
            assertThatThrownBy(() -> insertarCategoria(nombre, 0, 7))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("categoria_nombre_rango_ck");
        }
    }

    @Test
    void elRangoDeUnaCategoriaDebeSerElOficial() {
        assertThatThrownBy(() -> insertarCategoria("Sub-12", 10, 11))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("categoria_nombre_rango_ck");
    }

    // --- Documento del deportista ---------------------------------------------------------------------------

    @Test
    void elRegistroCivilEsUnTipoDeDocumentoDelDeportista() {
        assertThatCode(() -> insertarDeportista("RC", "1234567890")).doesNotThrowAnyException();
        assertThatCode(() -> insertarDeportista("TI", "1098765432")).doesNotThrowAnyException();
        assertThatCode(() -> insertarDeportista("CE", "AB123456")).doesNotThrowAnyException();
        assertThatCode(() -> insertarDeportista("PASAPORTE", "XY987654")).doesNotThrowAnyException();
    }

    @Test
    void laCedulaYaNoSirveParaUnDeportistaPeroSiParaUnEntrenador() {
        assertThatThrownBy(() -> insertarDeportista("CC", "55555555"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("deportista_tipo_documento_ck");
        // PostgresDePrueba.entrenador (en prepararBase) ya crea entrenadores con CC
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT count(*) FROM entrenador WHERE tipo_documento = 'CC'", Integer.class)).isEqualTo(2);
    }

    // --- Sedes y entrenamientos -----------------------------------------------------------------------------

    @Test
    void unEntrenamientoSoloPuedeSerEnUnaDeLasDosSedes() {
        UUID sub10 = PostgresDePrueba.categoria("Sub-10", diaz);

        assertThatCode(() -> insertarEntrenamiento(sub10, diaz, "Horizontes", "18:00", "19:00"))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertarEntrenamiento(sub10, diaz, "UVA El Paraíso", "20:00", "21:00"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insertarEntrenamiento(sub10, diaz, "Cancha 1", "16:00", "17:00"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("entrenamiento_sede_ck");
    }

    @Test
    void variasCategoriasPuedenEntrenarALaMismaHoraEnLaMismaSede() {
        UUID sub12 = PostgresDePrueba.categoria("Sub-12", diaz);
        UUID sub13 = PostgresDePrueba.categoria("Sub-13", mora);

        insertarEntrenamiento(sub12, diaz, "Horizontes", "19:00", "20:00");

        assertThatCode(() -> insertarEntrenamiento(sub13, mora, "Horizontes", "19:00", "20:00"))
                .doesNotThrowAnyException();
    }

    @Test
    void unaCategoriaNoPuedeTenerDosEntrenamientosQueSeCruzan() {
        UUID sub12 = PostgresDePrueba.categoria("Sub-12", diaz);
        insertarEntrenamiento(sub12, diaz, "Horizontes", "19:00", "20:00");

        assertThatThrownBy(() -> insertarEntrenamiento(sub12, mora, "UVA El Paraíso", "19:30", "20:30"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("entrenamiento_cruce_categoria_ex");
    }

    @Test
    void unEntrenadorNoPuedeEstarEnDosEntrenamientosQueSeCruzan() {
        UUID sub12 = PostgresDePrueba.categoria("Sub-12", diaz);
        UUID sub14 = PostgresDePrueba.categoria("Sub-14", mora);
        insertarEntrenamiento(sub12, diaz, "Horizontes", "19:00", "20:00");

        assertThatThrownBy(() -> insertarEntrenamiento(sub14, diaz, "UVA El Paraíso", "19:30", "20:30"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("entrenamiento_cruce_entrenador_ex");
    }

    // --- Horario fijo del club ------------------------------------------------------------------------------

    @Test
    void elHorarioFijoVaDeLunesASabado() {
        UUID sub12 = PostgresDePrueba.categoria("Sub-12", diaz);

        assertThatCode(() -> insertarFranja(sub12, 1, "19:00", "20:00", "Horizontes", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> insertarFranja(sub12, 6, "08:30", "10:00", "Horizontes", diaz)).doesNotThrowAnyException();
        for (int dia : new int[] {0, 7}) {
            assertThatThrownBy(() -> insertarFranja(sub12, dia, "10:00", "11:00", "Horizontes", null))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("franja_dia_ck");
        }
    }

    @Test
    void unaFranjaTieneSedeHorasYGrupoValidos() {
        UUID sub9 = PostgresDePrueba.categoria("Sub-9", diaz);

        assertThatThrownBy(() -> insertarFranja(sub9, 3, "10:00", "11:00", "Cancha 1", null))
                .hasMessageContaining("franja_sede_ck");
        assertThatThrownBy(() -> insertarFranja(sub9, 3, "11:00", "10:00", "Horizontes", null))
                .hasMessageContaining("franja_horas_ck");
        assertThatThrownBy(() -> insertarFranja(sub9, 3, "10:00", "11:00", "Horizontes", null, "noche"))
                .hasMessageContaining("franja_grupo_ck");
        assertThatCode(() -> insertarFranja(sub9, 3, "07:00", "08:00", "UVA El Paraíso", null, "mañana"))
                .doesNotThrowAnyException();
    }

    @Test
    void sinEntrenadorFijoVariasCategoriasPuedenCoincidirEnHoraYSede() {
        UUID parvulos = PostgresDePrueba.categoria("Párvulos", diaz);
        UUID cachorros = PostgresDePrueba.categoria("Cachorros", diaz);

        insertarFranja(parvulos, 2, "08:30", "09:30", "UVA El Paraíso", null);

        assertThatCode(() -> insertarFranja(cachorros, 2, "08:30", "09:30", "UVA El Paraíso", null))
                .doesNotThrowAnyException();
    }

    @Test
    void unMismoEntrenadorNoPuedeTenerDosFranjasQueSeCruzanElMismoDia() {
        UUID sub12 = PostgresDePrueba.categoria("Sub-12", diaz);
        UUID sub13 = PostgresDePrueba.categoria("Sub-13", mora);
        insertarFranja(sub12, 1, "19:00", "20:00", "Horizontes", diaz);

        assertThatThrownBy(() -> insertarFranja(sub13, 1, "19:30", "20:30", "Horizontes", diaz))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("franja_cruce_entrenador_ex");
        // Franja pegada (termina cuando empieza la otra) y otro día: sí se permiten
        assertThatCode(() -> insertarFranja(sub13, 1, "20:00", "21:00", "Horizontes", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> insertarFranja(sub13, 2, "19:30", "20:30", "Horizontes", diaz)).doesNotThrowAnyException();
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------

    private void insertarCategoria(String nombre, int minima, int maxima) {
        jdbc.update("INSERT INTO categoria (nombre, edad_minima, edad_maxima) VALUES (:n, :min, :max)",
                new MapSqlParameterSource("n", nombre).addValue("min", minima).addValue("max", maxima));
    }

    private void insertarDeportista(String tipo, String documento) {
        jdbc.update("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento)
                VALUES ('Pedro', 'Gómez', :tipo, :documento, :nacimiento)""",
                new MapSqlParameterSource("tipo", tipo).addValue("documento", documento)
                        .addValue("nacimiento", LocalDate.of(2016, 3, 3)));
    }

    private void insertarEntrenamiento(UUID categoria, UUID entrenador, String lugar, String inicio, String fin) {
        jdbc.update("""
                INSERT INTO entrenamiento (id_categoria, id_entrenador, fecha, hora_inicio, hora_fin, lugar)
                VALUES (:categoria, :entrenador, :fecha, :inicio, :fin, :lugar)""",
                new MapSqlParameterSource("categoria", categoria).addValue("entrenador", entrenador)
                        .addValue("fecha", FECHA).addValue("inicio", LocalTime.parse(inicio))
                        .addValue("fin", LocalTime.parse(fin)).addValue("lugar", lugar));
    }

    private void insertarFranja(UUID categoria, int dia, String inicio, String fin, String sede, UUID entrenador) {
        insertarFranja(categoria, dia, inicio, fin, sede, entrenador, null);
    }

    private void insertarFranja(UUID categoria, int dia, String inicio, String fin, String sede, UUID entrenador,
            String grupo) {
        jdbc.update("""
                INSERT INTO franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, id_entrenador, grupo)
                VALUES (:categoria, :dia, :inicio, :fin, :sede, CAST(:entrenador AS uuid), :grupo)""",
                new MapSqlParameterSource("categoria", categoria).addValue("dia", dia)
                        .addValue("inicio", LocalTime.parse(inicio)).addValue("fin", LocalTime.parse(fin))
                        .addValue("sede", sede).addValue("entrenador", entrenador == null ? null : entrenador.toString())
                        .addValue("grupo", grupo));
    }
}
