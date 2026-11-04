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
 * La tabla {@code excepcion_horario} de la migración 010 contra un PostgreSQL real: lo que acepta, lo que rechaza (por el
 * nombre de la restricción, que es lo que usará el backend para traducirlo) y sus cascadas.
 */
@ExtendWith(DockerDisponible.class)
class MigracionExcepcionHorarioTest {

    /** Un martes. */
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);

    private NamedParameterJdbcTemplate jdbc;
    private UUID luis;
    private UUID ana;
    private UUID sub12;
    private UUID semillero;
    private UUID franja;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        luis = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        ana = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        sub12 = PostgresDePrueba.categoria("Sub-12", luis);
        semillero = PostgresDePrueba.categoria("Semillero", ana);
        franja = insertarFranja(sub12, 2, "16:00", "17:30", luis);
    }

    /** Lo que insertan estas pruebas no debe estorbar a las demás, que borran categorías y entrenadores. */
    @AfterEach
    void limpiarTodo() {
        jdbc.getJdbcOperations().update("DELETE FROM excepcion_horario");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'excepcion-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
        PostgresDePrueba.limpiar();
    }

    // --- Lo válido ------------------------------------------------------------------------------------------

    @Test
    void aceptaUnaCancelacionYUnExtraValidos() {
        assertThatCode(() -> cancelar(franja, FECHA, "Lluvia")).doesNotThrowAnyException();
        assertThatCode(() -> extra(sub12, FECHA, "18:00", "19:00", "Horizontes", luis, null, null))
                .doesNotThrowAnyException();

        assertThat(contar("CANCELACION")).isEqualTo(1);
        assertThat(contar("EXTRA")).isEqualTo(1);
    }

    @Test
    void elExtraAceptaPorTurnosGrupoYMotivoOpcionales() {
        assertThatCode(() -> extra(semillero, FECHA, "08:00", "09:00", "UVA El Paraíso", null, "mañana", "Partido amistoso"))
                .doesNotThrowAnyException();
        assertThatCode(() -> extra(semillero, FECHA, "08:00", "09:00", "UVA El Paraíso", null, "tarde", null))
                .doesNotThrowAnyException();

        assertThat(contar("EXTRA")).isEqualTo(2);
    }

    @Test
    void laCancelacionPuedeNoTenerMotivoYQuedaConFechaDeCreacion() {
        cancelar(franja, FECHA, null);

        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT creado_en IS NOT NULL AND motivo IS NULL AND creado_por IS NULL FROM excepcion_horario", Boolean.class))
                .isTrue();
    }

    @Test
    void lasCancelacionesDeOtraFechaOtraFranjaYLosExtrasSeparadosNoChocan() {
        UUID otraFranja = insertarFranja(semillero, 2, "16:00", "17:30", ana);

        assertThatCode(() -> {
            cancelar(franja, FECHA, null);
            cancelar(franja, FECHA.plusDays(7), null);
            cancelar(otraFranja, FECHA, null);
            extra(sub12, FECHA, "18:00", "19:00", "Horizontes", luis, null, null);
            extra(sub12, FECHA, "19:00", "20:00", "Horizontes", luis, null, null); // contiguo: no se cruza
            extra(sub12, FECHA.plusDays(1), "18:30", "19:30", "Horizontes", luis, null, null); // otro día
            extra(semillero, FECHA, "18:30", "19:30", "Horizontes", ana, null, null); // otro entrenador
        }).doesNotThrowAnyException();
    }

    @Test
    void elCreadorSeGuardaYSeBorraSuReferenciaSiSeBorraElUsuario() {
        UUID usuario = usuario("excepcion-creador@pradofc.co");

        jdbc.update("""
                INSERT INTO excepcion_horario (tipo, fecha, id_franja, creado_por)
                VALUES ('CANCELACION', :fecha, :franja, :usuario)""",
                new MapSqlParameterSource("fecha", FECHA).addValue("franja", franja).addValue("usuario", usuario));
        assertThat(creadoPor()).isEqualTo(usuario);

        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo = 'excepcion-creador@pradofc.co'");

        assertThat(contar("CANCELACION")).isEqualTo(1);
        assertThat(creadoPor()).isNull();
    }

    @Test
    void elCreadorDebeSerUnUsuarioQueExiste() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO excepcion_horario (tipo, fecha, id_franja, creado_por)
                VALUES ('CANCELACION', :fecha, :franja, :usuario)""",
                new MapSqlParameterSource("fecha", FECHA).addValue("franja", franja).addValue("usuario", UUID.randomUUID())))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_creado_por_fkey");
    }

    // --- Cancelaciones --------------------------------------------------------------------------------------

    @Test
    void noSeCancelaDosVecesLaMismaFranjaElMismoDia() {
        cancelar(franja, FECHA, null);

        assertThatThrownBy(() -> cancelar(franja, FECHA, "Otra vez"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_cancelacion_unica_idx");
    }

    @Test
    void laCancelacionNoLlevaHorasSedeCategoriaEntrenadorNiGrupo() {
        for (String columna : new String[] {"hora_inicio", "hora_fin", "sede", "id_categoria", "id_entrenador", "grupo"}) {
            assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                    "INSERT INTO excepcion_horario (tipo, fecha, id_franja, " + columna + ") VALUES ('CANCELACION', ?, ?, "
                            + valorDe(columna) + ")", FECHA, franja))
                    .as(columna)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("excepcion_horario_campos_ck");
        }
    }

    @Test
    void laCancelacionNecesitaSuFranja() {
        assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                "INSERT INTO excepcion_horario (tipo, fecha) VALUES ('CANCELACION', ?)", FECHA))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_campos_ck");
    }

    @Test
    void laFranjaDeLaCancelacionDebeExistir() {
        assertThatThrownBy(() -> cancelar(UUID.randomUUID(), FECHA, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_id_franja_fkey");
    }

    // --- Extras ---------------------------------------------------------------------------------------------

    @Test
    void elExtraNecesitaHorasSedeYCategoria() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO excepcion_horario (tipo, fecha, id_categoria, sede)
                VALUES ('EXTRA', :fecha, :cat, 'Horizontes')""",
                new MapSqlParameterSource("fecha", FECHA).addValue("cat", sub12)))
                .as("sin horas")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_campos_ck");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO excepcion_horario (tipo, fecha, id_categoria, hora_inicio, hora_fin)
                VALUES ('EXTRA', :fecha, :cat, '18:00', '19:00')""",
                new MapSqlParameterSource("fecha", FECHA).addValue("cat", sub12)))
                .as("sin sede")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_campos_ck");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO excepcion_horario (tipo, fecha, hora_inicio, hora_fin, sede)
                VALUES ('EXTRA', :fecha, '18:00', '19:00', 'Horizontes')""", new MapSqlParameterSource("fecha", FECHA)))
                .as("sin categoría")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_campos_ck");
    }

    @Test
    void elExtraNoApuntaAUnaFranja() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO excepcion_horario (tipo, fecha, id_franja, id_categoria, hora_inicio, hora_fin, sede)
                VALUES ('EXTRA', :fecha, :franja, :cat, '18:00', '19:00', 'Horizontes')""",
                new MapSqlParameterSource("fecha", FECHA).addValue("franja", franja).addValue("cat", sub12)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_campos_ck");
    }

    @Test
    void laHoraDeFinDebeSerPosteriorALaDeInicio() {
        for (String[] horas : new String[][] {{"18:00", "18:00"}, {"19:00", "18:00"}}) {
            assertThatThrownBy(() -> extra(sub12, FECHA, horas[0], horas[1], "Horizontes", luis, null, null))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("excepcion_horario_horas_ck");
        }
    }

    @Test
    void laSedeDebeSerUnaDeLasDos() {
        assertThatThrownBy(() -> extra(sub12, FECHA, "18:00", "19:00", "Cancha 3", luis, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_sede_ck");
        assertThatCode(() -> extra(sub12, FECHA, "18:00", "19:00", "UVA El Paraíso", luis, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void elGrupoDebeSerMananaOTarde() {
        for (String grupo : new String[] {"noche", "Mañana", ""}) {
            assertThatThrownBy(() -> extra(semillero, FECHA, "18:00", "19:00", "Horizontes", ana, grupo, null))
                    .as(grupo)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("excepcion_horario_grupo_ck");
        }
    }

    @Test
    void elTipoDebeSerCancelacionOExtra() {
        assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                "INSERT INTO excepcion_horario (tipo, fecha, id_franja) VALUES ('MOVIDA', ?, ?)", FECHA, franja))
                .isInstanceOf(DataAccessException.class)
                // Un tipo desconocido viola a la vez tipo_ck y campos_ck; PostgreSQL informa la primera por nombre
                .satisfiesAnyOf(
                        e -> assertThat(e).hasMessageContaining("excepcion_horario_tipo_ck"),
                        e -> assertThat(e).hasMessageContaining("excepcion_horario_campos_ck"));
    }

    @Test
    void elMotivoTieneComoMaximo200Caracteres() {
        assertThatCode(() -> cancelar(franja, FECHA, "x".repeat(200))).doesNotThrowAnyException();
        assertThatThrownBy(() -> cancelar(franja, FECHA.plusDays(7), "x".repeat(201)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void laCategoriaYElEntrenadorDelExtraDebenExistir() {
        assertThatThrownBy(() -> extra(UUID.randomUUID(), FECHA, "18:00", "19:00", "Horizontes", luis, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_id_categoria_fkey");
        assertThatThrownBy(() -> extra(sub12, FECHA, "18:00", "19:00", "Horizontes", UUID.randomUUID(), null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_id_entrenador_fkey");
    }

    // --- Cruce entre extras ---------------------------------------------------------------------------------

    @Test
    void elMismoEntrenadorNoTieneDosExtrasQueSeSolapanElMismoDia() {
        extra(sub12, FECHA, "18:00", "19:30", "Horizontes", luis, null, null);

        for (String[] horas : new String[][] {{"18:00", "19:30"}, {"19:00", "20:00"}, {"17:00", "18:30"}, {"18:15", "18:45"}}) {
            assertThatThrownBy(() -> extra(semillero, FECHA, horas[0], horas[1], "UVA El Paraíso", luis, null, null))
                    .as(horas[0] + "-" + horas[1])
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("excepcion_horario_cruce_entrenador_ex");
        }
    }

    @Test
    void dosExtrasPorTurnosPuedenCoincidir() {
        extra(semillero, FECHA, "18:00", "19:00", "Horizontes", null, null, null);

        assertThatCode(() -> extra(semillero, FECHA, "18:00", "19:00", "Horizontes", null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void laCancelacionNoCuentaParaElCruceDeExtras() {
        // La exclusión solo mira los EXTRA: una cancelación no tiene entrenador ni horas
        cancelar(franja, FECHA, null);

        assertThatCode(() -> extra(sub12, FECHA, "16:00", "17:30", "Horizontes", luis, null, null))
                .doesNotThrowAnyException();
    }

    // --- Cascadas -------------------------------------------------------------------------------------------

    @Test
    void alBorrarUnaFranjaSeBorranSusCancelacionesYSoloEsas() {
        UUID otra = insertarFranja(semillero, 3, "16:00", "17:00", ana);
        cancelar(franja, FECHA, null);
        cancelar(otra, FECHA.plusDays(1), null);
        extra(sub12, FECHA, "18:00", "19:00", "Horizontes", luis, null, null);

        jdbc.update("DELETE FROM franja_horario WHERE id_franja = :id", new MapSqlParameterSource("id", franja));

        assertThat(contar("CANCELACION")).isEqualTo(1);
        assertThat(contar("EXTRA")).isEqualTo(1);
    }

    @Test
    void alBorrarUnaCategoriaSeBorranSusExtrasSusFranjasYLasCancelacionesDeEsasFranjas() {
        cancelar(franja, FECHA, null);
        extra(sub12, FECHA, "18:00", "19:00", "Horizontes", luis, null, null);
        extra(semillero, FECHA, "18:00", "19:00", "Horizontes", ana, null, null);

        jdbc.update("DELETE FROM categoria WHERE id_categoria = :id", new MapSqlParameterSource("id", sub12));

        assertThat(contar("CANCELACION")).isZero();
        assertThat(contar("EXTRA")).isEqualTo(1);
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM franja_horario", Integer.class)).isZero();
    }

    @Test
    void unEntrenadorConExtrasNoSePuedeBorrar() {
        extra(sub12, FECHA, "18:00", "19:00", "Horizontes", luis, null, null);
        // Sin la categoría ni la franja que lo retienen, solo queda el extra
        jdbc.getJdbcOperations().update("UPDATE categoria SET id_entrenador = NULL");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM entrenador WHERE id_entrenador = :id",
                new MapSqlParameterSource("id", luis)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("excepcion_horario_id_entrenador_fkey");
    }

    // --- Datos ----------------------------------------------------------------------------------------------

    private UUID insertarFranja(UUID categoria, int dia, String inicio, String fin, UUID entrenador) {
        return jdbc.queryForObject("""
                INSERT INTO franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, id_entrenador)
                VALUES (:cat, :dia, :inicio, :fin, 'Horizontes', :entrenador) RETURNING id_franja""",
                new MapSqlParameterSource("cat", categoria).addValue("dia", dia)
                        .addValue("inicio", LocalTime.parse(inicio)).addValue("fin", LocalTime.parse(fin))
                        .addValue("entrenador", entrenador),
                UUID.class);
    }

    private void cancelar(UUID idFranja, LocalDate fecha, String motivo) {
        jdbc.update("INSERT INTO excepcion_horario (tipo, fecha, id_franja, motivo) VALUES ('CANCELACION', :fecha, :franja, :motivo)",
                new MapSqlParameterSource("fecha", fecha).addValue("franja", idFranja).addValue("motivo", motivo));
    }

    private void extra(UUID categoria, LocalDate fecha, String inicio, String fin, String sede, UUID entrenador,
                       String grupo, String motivo) {
        jdbc.update("""
                INSERT INTO excepcion_horario (tipo, fecha, id_categoria, hora_inicio, hora_fin, sede, id_entrenador, grupo, motivo)
                VALUES ('EXTRA', :fecha, :cat, :inicio, :fin, :sede, :entrenador, :grupo, :motivo)""",
                new MapSqlParameterSource("fecha", fecha).addValue("cat", categoria)
                        .addValue("inicio", LocalTime.parse(inicio)).addValue("fin", LocalTime.parse(fin))
                        .addValue("sede", sede).addValue("entrenador", entrenador).addValue("grupo", grupo)
                        .addValue("motivo", motivo));
    }

    private UUID usuario(String correo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", new MapSqlParameterSource("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, 'Ana', 'Prueba', :c)",
                new MapSqlParameterSource("id", id).addValue("c", correo));
        return id;
    }

    private int contar(String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM excepcion_horario WHERE tipo = :tipo",
                new MapSqlParameterSource("tipo", tipo), Integer.class);
    }

    private UUID creadoPor() {
        return jdbc.getJdbcOperations().queryForObject("SELECT creado_por FROM excepcion_horario", UUID.class);
    }

    /** Un valor SQL válido (literal) para la columna, para probar que una cancelación no puede llevarla. */
    private String valorDe(String columna) {
        return switch (columna) {
            case "hora_inicio", "hora_fin" -> "'18:00'";
            case "sede" -> "'Horizontes'";
            case "grupo" -> "'tarde'";
            case "id_categoria" -> "'" + sub12 + "'";
            default -> "'" + luis + "'";
        };
    }
}
