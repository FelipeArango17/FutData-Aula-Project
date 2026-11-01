package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Las reglas que deja la migración 007 (las 9 categorías del club, registro civil, tipos de documento del club,
 * sedes y horario fijo) y el seed {@code supabase/seeds/franjas_club.sql}, contra un PostgreSQL real. Cada rechazo
 * se comprueba por el nombre de la restricción, que es lo que usará el backend para
 * traducirlo a un mensaje.
 */
@ExtendWith(DockerDisponible.class)
class MigracionCategoriasYHorarioTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);

    /** Los 7 tipos de documento que pide el club (v4 de la entrega de backend). */
    private static final List<String> TIPOS_DE_DOCUMENTO = List.of("DOCUMENTO_IDENTIDAD", "CERTIFICADO_ESTUDIO",
            "FORMULARIO_INSCRIPCION", "AUTORIZACION_HABEAS_DATA", "PAZ_Y_SALVO", "CARTA_RENUNCIA", "TRASPASO_NACIONAL");

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
        jdbc.getJdbcOperations().update("DELETE FROM documento");
        jdbc.getJdbcOperations().update("DELETE FROM deportista");
        PostgresDePrueba.limpiar();
    }

    // --- Categorías -----------------------------------------------------------------------------------------

    @Test
    void soloExistenLasCategoriasDelClubPorAnioDeNacimiento() {
        assertThatCode(() -> PostgresDePrueba.categoria("Párvulos", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> PostgresDePrueba.categoria("Cachorros", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> PostgresDePrueba.categoria("Semillero", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> PostgresDePrueba.categoria("Sub-11", diaz)).doesNotThrowAnyException();
        assertThatCode(() -> PostgresDePrueba.categoria("Sub-16", diaz)).doesNotThrowAnyException();
    }

    @Test
    void semilleroEsDe9A10Anios() {
        assertThatCode(() -> insertarCategoria("Semillero", 9, 10)).doesNotThrowAnyException();
    }

    @Test
    void semilleroConOtroRangoSeRechaza() {
        assertThatThrownBy(() -> insertarCategoria("Semillero", 9, 9))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("categoria_nombre_rango_ck");
    }

    @Test
    void sub9YSub10YaNoSonCategoriasNiConSuRangoDeUnAnio() {
        // Antes de la v4 eran válidas con un solo año; ahora esas edades son de Semillero
        for (Object[] c : new Object[][] {{"Sub-9", 9, 9}, {"Sub-10", 10, 10}, {"Sub-9", 9, 10}, {"Sub-10", 9, 10}}) {
            assertThatThrownBy(() -> insertarCategoria((String) c[0], (int) c[1], (int) c[2]))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("categoria_nombre_rango_ck");
        }
    }

    @Test
    void sub8YSub17YaNoSonCategorias() {
        for (String nombre : new String[] {"Sub-8", "Sub-17"}) {
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

    // --- Documentos que se cargan ---------------------------------------------------------------------------

    @Test
    void unDocumentoAceptaLosSieteTiposDelClub() {
        UUID deportista = insertarDeportista("TI", "1098765432");

        for (String tipo : TIPOS_DE_DOCUMENTO) {
            assertThatCode(() -> insertarDocumento(deportista, tipo)).as(tipo).doesNotThrowAnyException();
        }
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM documento", Integer.class))
                .isEqualTo(7);
    }

    @Test
    void laFichaMedicaYLosTiposViejosYaNoSonDocumentos() {
        UUID deportista = insertarDeportista("TI", "1098765432");

        for (String tipo : new String[] {"FICHA_MEDICA", "PERMISO", "CONTRATO", "OTRO"}) {
            assertThatThrownBy(() -> insertarDocumento(deportista, tipo))
                    .as(tipo)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("documento_tipo_ck");
        }
    }

    // --- Sedes y entrenamientos -----------------------------------------------------------------------------

    @Test
    void unEntrenamientoSoloPuedeSerEnUnaDeLasDosSedes() {
        UUID semillero = PostgresDePrueba.categoria("Semillero", diaz);

        assertThatCode(() -> insertarEntrenamiento(semillero, diaz, "Horizontes", "18:00", "19:00"))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertarEntrenamiento(semillero, diaz, "UVA El Paraíso", "20:00", "21:00"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insertarEntrenamiento(semillero, diaz, "Cancha 1", "16:00", "17:00"))
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
        UUID cachorros = PostgresDePrueba.categoria("Cachorros", diaz);

        assertThatThrownBy(() -> insertarFranja(cachorros, 3, "10:00", "11:00", "Cancha 1", null))
                .hasMessageContaining("franja_sede_ck");
        assertThatThrownBy(() -> insertarFranja(cachorros, 3, "11:00", "10:00", "Horizontes", null))
                .hasMessageContaining("franja_horas_ck");
        assertThatThrownBy(() -> insertarFranja(cachorros, 3, "10:00", "11:00", "Horizontes", null, "noche"))
                .hasMessageContaining("franja_grupo_ck");
        assertThatCode(() -> insertarFranja(cachorros, 2, "07:30", "09:00", "UVA El Paraíso", null, "mañana"))
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

    // --- Seed del horario del club --------------------------------------------------------------------------

    @Test
    void elSeedDelClubCargaLas30FranjasConGrupoSoloEnCachorrosYSemilleroMartesYJueves() {
        // limpiar() borra las categorías de la migración: se recrean las 9, sin entrenador, como en una base nueva
        PostgresDePrueba.RANGOS.forEach((nombre, rango) -> insertarCategoria(nombre, rango[0], rango[1]));

        PostgresDePrueba.ejecutarScript(PostgresDePrueba.SEEDS.resolve("franjas_club.sql"));

        var sql = jdbc.getJdbcOperations();
        assertThat(sql.queryForObject("SELECT count(*) FROM franja_horario", Integer.class)).isEqualTo(30);
        assertThat(sql.queryForObject("SELECT count(*) FROM franja_horario WHERE id_entrenador IS NULL", Integer.class))
                .isEqualTo(30);
        assertThat(sql.queryForList("""
                SELECT f.grupo || ' ' || count(*) FROM franja_horario f
                 WHERE f.grupo IS NOT NULL GROUP BY f.grupo ORDER BY f.grupo""", String.class))
                .containsExactly("mañana 4", "tarde 4");
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM franja_horario f JOIN categoria c ON c.id_categoria = f.id_categoria
                 WHERE f.grupo IS NOT NULL AND c.nombre IN ('Cachorros', 'Semillero') AND f.dia IN (2, 4)""",
                Integer.class)).isEqualTo(8);
        assertThat(sql.queryForList("""
                SELECT c.nombre || ' ' || count(*) FROM franja_horario f JOIN categoria c ON c.id_categoria = f.id_categoria
                 GROUP BY c.nombre, c.edad_minima ORDER BY c.edad_minima""", String.class))
                .containsExactly("Párvulos 2", "Cachorros 5", "Semillero 5", "Sub-11 3", "Sub-12 3", "Sub-13 3",
                        "Sub-14 3", "Sub-15 3", "Sub-16 3");
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------

    private void insertarCategoria(String nombre, int minima, int maxima) {
        jdbc.update("INSERT INTO categoria (nombre, edad_minima, edad_maxima) VALUES (:n, :min, :max)",
                new MapSqlParameterSource("n", nombre).addValue("min", minima).addValue("max", maxima));
    }

    private UUID insertarDeportista(String tipo, String documento) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento)
                VALUES ('Pedro', 'Gómez', :tipo, :documento, :nacimiento)
                RETURNING id_deportista""",
                new MapSqlParameterSource("tipo", tipo).addValue("documento", documento)
                        .addValue("nacimiento", LocalDate.of(2016, 3, 3)), UUID.class);
    }

    private void insertarDocumento(UUID deportista, String tipo) {
        jdbc.update("""
                INSERT INTO documento (id_deportista, tipo, ruta, nombre_original, tipo_contenido, tamano_bytes)
                VALUES (:deportista, :tipo, :ruta, 'archivo.pdf', 'application/pdf', 1024)""",
                new MapSqlParameterSource("deportista", deportista).addValue("tipo", tipo)
                        .addValue("ruta", "documentos/" + deportista + "/" + tipo + ".pdf"));
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
