package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.FranjaYaCanceladaException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@link ExcepcionHorarioDAOImpl} contra un PostgreSQL real con todas las migraciones: crear, las traducciones de
 * error, listar por rango, la propiedad decidida en SQL y el cruce de un extra contra las franjas.
 */
@ExtendWith(DockerDisponible.class)
class ExcepcionHorarioDAOImplTest {

    /** Un martes (dia = 2). */
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);

    private NamedParameterJdbcTemplate jdbc;
    private ExcepcionHorarioDAOImpl dao;
    private UUID luis;
    private UUID ana;
    private UUID usuarioLuis;
    private UUID usuarioAna;
    private UUID usuarioAjeno;
    private UUID sub12;
    private UUID semillero;
    /** Martes 16:00-17:30, Sub-12, entrenador Luis. */
    private UUID franjaSub12;
    /** Martes 16:00-17:30, Semillero, "por turnos". */
    private UUID franjaPorTurnos;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        dao = new ExcepcionHorarioDAOImpl(jdbc);
        luis = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        ana = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        usuarioLuis = cuenta("excepcion-dao-luis@pradofc.co");
        usuarioAna = cuenta("excepcion-dao-ana@pradofc.co");
        usuarioAjeno = cuenta("excepcion-dao-ajeno@pradofc.co");
        vincular(luis, usuarioLuis);
        vincular(ana, usuarioAna);
        sub12 = PostgresDePrueba.categoria("Sub-12", luis);
        semillero = PostgresDePrueba.categoria("Semillero", ana);
        franjaSub12 = franja(sub12, 2, "16:00", "17:30", luis);
        franjaPorTurnos = franja(semillero, 2, "16:00", "17:30", null);
    }

    @AfterEach
    void limpiarTodo() {
        jdbc.getJdbcOperations().update("DELETE FROM excepcion_horario");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'excepcion-dao-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
        PostgresDePrueba.limpiar();
    }

    // --- Crear y leer ---------------------------------------------------------------------------------------

    @Test
    void creaUnaCancelacionQueTomaLosDatosDeSuFranja() {
        UUID id = dao.crearCancelacion(franjaSub12, FECHA, "Lluvia", usuarioAna);

        ExcepcionHorario leida = dao.buscarPorId(id).orElseThrow();

        assertThat(leida.id()).isEqualTo(id);
        assertThat(leida.esCancelacion()).isTrue();
        assertThat(leida.fecha()).isEqualTo(FECHA);
        assertThat(leida.idFranja()).isEqualTo(franjaSub12);
        assertThat(leida.idCategoria()).isEqualTo(sub12);
        assertThat(leida.categoria()).isEqualTo("Sub-12");
        assertThat(leida.diaDeLaFranja()).isEqualTo(2);
        assertThat(leida.horaInicio()).isEqualTo(LocalTime.of(16, 0));
        assertThat(leida.horaFin()).isEqualTo(LocalTime.of(17, 30));
        assertThat(leida.sede()).isEqualTo("Horizontes");
        assertThat(leida.idEntrenador()).isEqualTo(luis);
        assertThat(leida.entrenador()).isEqualTo("Luis Mora");
        assertThat(leida.entrenadorActivo()).isTrue();
        assertThat(leida.motivo()).isEqualTo("Lluvia");
        assertThat(leida.creadoPor()).isEqualTo(usuarioAna);
    }

    @Test
    void laCancelacionDeUnaFranjaPorTurnosNoTraeEntrenador() {
        ExcepcionHorario leida = dao.buscarPorId(dao.crearCancelacion(franjaPorTurnos, FECHA, null, null)).orElseThrow();

        assertThat(leida.categoria()).isEqualTo("Semillero");
        assertThat(leida.idEntrenador()).isNull();
        assertThat(leida.entrenador()).isNull();
        assertThat(leida.entrenadorActivo()).isNull();
        assertThat(leida.motivo()).isNull();
        assertThat(leida.creadoPor()).isNull();
    }

    @Test
    void creaUnExtraConTodosSusDatos() {
        UUID id = dao.crearExtra(extra(sub12, FECHA, "18:00", "19:30", luis, null, "Partido amistoso", usuarioLuis));

        ExcepcionHorario leido = dao.buscarPorId(id).orElseThrow();

        assertThat(leido.esExtra()).isTrue();
        assertThat(leido.idFranja()).isNull();
        assertThat(leido.diaDeLaFranja()).isNull();
        assertThat(leido.fecha()).isEqualTo(FECHA);
        assertThat(leido.idCategoria()).isEqualTo(sub12);
        assertThat(leido.categoria()).isEqualTo("Sub-12");
        assertThat(leido.horaInicio()).isEqualTo(LocalTime.of(18, 0));
        assertThat(leido.horaFin()).isEqualTo(LocalTime.of(19, 30));
        assertThat(leido.sede()).isEqualTo("Horizontes");
        assertThat(leido.idEntrenador()).isEqualTo(luis);
        assertThat(leido.entrenador()).isEqualTo("Luis Mora");
        assertThat(leido.grupo()).isNull();
        assertThat(leido.motivo()).isEqualTo("Partido amistoso");
        assertThat(leido.creadoPor()).isEqualTo(usuarioLuis);
    }

    @Test
    void unExtraPorTurnosYConGrupoYElEntrenadorInactivoSeLeenBien() {
        UUID porTurnos = dao.crearExtra(extra(semillero, FECHA, "08:00", "09:00", null, "mañana", null, null));
        jdbc.getJdbcOperations().update("UPDATE entrenador SET activo = false WHERE id_entrenador = ?", luis);
        UUID deLuis = dao.crearExtra(extra(sub12, FECHA, "18:00", "19:00", luis, null, null, null));

        ExcepcionHorario a = dao.buscarPorId(porTurnos).orElseThrow();
        assertThat(a.idEntrenador()).isNull();
        assertThat(a.entrenador()).isNull();
        assertThat(a.entrenadorActivo()).isNull();
        assertThat(a.grupo()).isEqualTo("mañana");
        assertThat(dao.buscarPorId(deLuis).orElseThrow().entrenadorActivo()).isFalse();
    }

    @Test
    void buscarYEliminarUnIdQueNoExisteNoHacenNada() {
        UUID id = dao.crearCancelacion(franjaSub12, FECHA, null, null);

        assertThat(dao.buscarPorId(UUID.randomUUID())).isEmpty();
        assertThat(dao.eliminar(UUID.randomUUID())).isFalse();
        assertThat(dao.eliminar(id)).isTrue();
        assertThat(dao.buscarPorId(id)).isEmpty();
        assertThat(dao.eliminar(id)).isFalse();
    }

    // --- Errores traducidos ---------------------------------------------------------------------------------

    @Test
    void cancelarDosVecesLaMismaFranjaElMismoDiaEsFranjaYaCancelada() {
        dao.crearCancelacion(franjaSub12, FECHA, null, null);

        assertThatThrownBy(() -> dao.crearCancelacion(franjaSub12, FECHA, "Otra vez", null))
                .isInstanceOf(FranjaYaCanceladaException.class)
                .hasMessage("Esa franja ya está cancelada en esa fecha");
        // Otra fecha u otra franja sí se puede
        assertThat(dao.crearCancelacion(franjaSub12, FECHA.plusDays(7), null, null)).isNotNull();
        assertThat(dao.crearCancelacion(franjaPorTurnos, FECHA, null, null)).isNotNull();
    }

    @Test
    void deshacerLaCancelacionPermiteCancelarDeNuevo() {
        UUID id = dao.crearCancelacion(franjaSub12, FECHA, null, null);
        dao.eliminar(id);

        assertThat(dao.crearCancelacion(franjaSub12, FECHA, null, null)).isNotNull();
    }

    @Test
    void dosExtrasDelMismoEntrenadorQueSeSolapanEsCruce() {
        dao.crearExtra(extra(sub12, FECHA, "18:00", "19:30", luis, null, null, null));

        for (String[] horas : new String[][] {{"18:00", "19:30"}, {"19:00", "20:00"}, {"17:00", "18:30"}}) {
            assertThatThrownBy(() -> dao.crearExtra(extra(semillero, FECHA, horas[0], horas[1], luis, null, null, null)))
                    .as(horas[0] + "-" + horas[1])
                    .isInstanceOf(CruceDeHorarioException.class);
        }
    }

    @Test
    void losExtrasContiguosDeOtroDiaDeOtroEntrenadorOPorTurnosNoSeCruzan() {
        dao.crearExtra(extra(sub12, FECHA, "18:00", "19:30", luis, null, null, null));

        assertThat(dao.crearExtra(extra(sub12, FECHA, "19:30", "20:30", luis, null, null, null))).isNotNull();
        assertThat(dao.crearExtra(extra(sub12, FECHA.plusDays(1), "18:00", "19:30", luis, null, null, null))).isNotNull();
        assertThat(dao.crearExtra(extra(semillero, FECHA, "18:00", "19:30", ana, null, null, null))).isNotNull();
        assertThat(dao.crearExtra(extra(semillero, FECHA, "18:00", "19:30", null, null, null, null))).isNotNull();
        assertThat(dao.crearExtra(extra(semillero, FECHA, "18:00", "19:30", null, null, null, null))).isNotNull();
    }

    @Test
    void otraViolacionDeLaBaseNoSeTraduce() {
        assertThatThrownBy(() -> dao.crearExtra(extraConSede(sub12, "Cancha 3")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(CruceDeHorarioException.class)
                .isNotInstanceOf(FranjaYaCanceladaException.class);
    }

    // --- Listar por rango -----------------------------------------------------------------------------------

    @Test
    void listaPorRangoInclusivoOrdenadoPorFechaHoraYCategoria() {
        UUID tarde = dao.crearExtra(extra(sub12, FECHA, "18:00", "19:00", luis, null, null, null));
        UUID temprano = dao.crearExtra(extra(semillero, FECHA, "08:00", "09:00", ana, null, null, null));
        UUID cancelacion = dao.crearCancelacion(franjaSub12, FECHA, null, null); // 16:00 de la franja
        UUID primerDia = dao.crearExtra(extra(sub12, FECHA.minusDays(3), "07:00", "08:00", luis, null, null, null));
        UUID ultimoDia = dao.crearCancelacion(franjaPorTurnos, FECHA.plusDays(4), null, null); // tope del rango
        dao.crearExtra(extra(sub12, FECHA.minusDays(4), "07:00", "08:00", luis, null, null, null)); // antes
        dao.crearCancelacion(franjaSub12, FECHA.plusDays(7), null, null); // después

        List<ExcepcionHorario> lista = dao.listar(FECHA.minusDays(3), FECHA.plusDays(4));

        assertThat(lista).extracting(ExcepcionHorario::id)
                .containsExactly(primerDia, temprano, cancelacion, tarde, ultimoDia);
        assertThat(lista).extracting(ExcepcionHorario::tipo)
                .containsExactly("EXTRA", "EXTRA", "CANCELACION", "EXTRA", "CANCELACION");
        // La cancelación sale con los datos de su franja
        assertThat(lista.get(2).categoria()).isEqualTo("Sub-12");
        assertThat(lista.get(2).horaInicio()).isEqualTo(LocalTime.of(16, 0));
        assertThat(dao.listar(FECHA.plusDays(30), FECHA.plusDays(36))).isEmpty();
    }

    // --- Propiedad ------------------------------------------------------------------------------------------

    @Test
    void esDuenoDeFranjaPorSerElDeLaCategoriaOElDeLaFranja() {
        assertThat(dao.esDuenoDeFranja(usuarioLuis, franjaSub12)).as("dueño de la categoría y de la franja").isTrue();
        assertThat(dao.esDuenoDeFranja(usuarioAna, franjaSub12)).as("ajeno a Sub-12").isFalse();
        assertThat(dao.esDuenoDeFranja(usuarioAjeno, franjaSub12)).as("sin fila de entrenador").isFalse();

        // Una franja por turnos solo es del dueño de la categoría
        assertThat(dao.esDuenoDeFranja(usuarioAna, franjaPorTurnos)).isTrue();
        assertThat(dao.esDuenoDeFranja(usuarioLuis, franjaPorTurnos)).isFalse();

        // Luis dirige una franja de Semillero (cuyo dueño es Ana): le toca aunque la categoría no sea suya
        UUID cubre = franja(semillero, 4, "16:00", "17:00", luis);
        assertThat(dao.esDuenoDeFranja(usuarioLuis, cubre)).isTrue();
        assertThat(dao.esDuenoDeFranja(usuarioAna, cubre)).isTrue();
    }

    @Test
    void esDuenoDeFranjaConUnaFranjaOUnUsuarioQueNoExistenEsFalso() {
        assertThat(dao.esDuenoDeFranja(usuarioLuis, UUID.randomUUID())).isFalse();
        assertThat(dao.esDuenoDeFranja(UUID.randomUUID(), franjaSub12)).isFalse();
    }

    @Test
    void esDuenoDeCategoriaSoloEnLasSuyas() {
        assertThat(dao.esDuenoDeCategoria(usuarioLuis, sub12)).isTrue();
        assertThat(dao.esDuenoDeCategoria(usuarioLuis, semillero)).isFalse();
        assertThat(dao.esDuenoDeCategoria(usuarioAna, semillero)).isTrue();
        assertThat(dao.esDuenoDeCategoria(usuarioAjeno, sub12)).isFalse();
        assertThat(dao.esDuenoDeCategoria(usuarioLuis, UUID.randomUUID())).isFalse();
    }

    @Test
    void unaCategoriaSinEntrenadorNoEsDeNadie() {
        jdbc.getJdbcOperations().update("UPDATE categoria SET id_entrenador = NULL WHERE id_categoria = ?", semillero);

        assertThat(dao.esDuenoDeCategoria(usuarioAna, semillero)).isFalse();
        assertThat(dao.esDuenoDeFranja(usuarioAna, franjaPorTurnos)).isFalse();
    }

    @Test
    void esDuenoDeExcepcionDeUnaCancelacionVaPorSuFranja() {
        UUID deSub12 = dao.crearCancelacion(franjaSub12, FECHA, null, usuarioAna);
        UUID porTurnos = dao.crearCancelacion(franjaPorTurnos, FECHA, null, usuarioLuis);

        assertThat(dao.esDuenoDeExcepcion(usuarioLuis, deSub12)).isTrue();
        assertThat(dao.esDuenoDeExcepcion(usuarioAna, deSub12)).as("quien la creó no basta").isFalse();
        assertThat(dao.esDuenoDeExcepcion(usuarioAna, porTurnos)).isTrue();
        assertThat(dao.esDuenoDeExcepcion(usuarioLuis, porTurnos)).as("por turnos: solo el dueño de la categoría").isFalse();
        assertThat(dao.esDuenoDeExcepcion(usuarioAjeno, deSub12)).isFalse();
    }

    @Test
    void esDuenoDeExcepcionDeUnExtraVaPorSuCategoriaNoPorSuEntrenador() {
        // Extra de Semillero (de Ana) dirigido por Luis
        UUID extraDeSemillero = dao.crearExtra(extra(semillero, FECHA, "18:00", "19:00", luis, null, null, null));

        assertThat(dao.esDuenoDeExcepcion(usuarioAna, extraDeSemillero)).isTrue();
        assertThat(dao.esDuenoDeExcepcion(usuarioLuis, extraDeSemillero)).as("dirigirlo no lo hace dueño").isFalse();
        assertThat(dao.esDuenoDeExcepcion(usuarioAjeno, extraDeSemillero)).isFalse();
        assertThat(dao.esDuenoDeExcepcion(usuarioLuis, UUID.randomUUID())).isFalse();
    }

    @Test
    void elAdministradorNoEsEntrenadorYLaConsultaNoLoCuenta() {
        UUID admin = cuenta("excepcion-dao-admin@pradofc.co");

        assertThat(dao.esDuenoDeCategoria(admin, sub12)).isFalse();
        assertThat(dao.esDuenoDeFranja(admin, franjaSub12)).isFalse();
    }

    // --- Cruce de un extra contra las franjas ---------------------------------------------------------------

    @Test
    void hayFranjaQueSeCruzaSiElEntrenadorTieneUnaFranjaSolapadaEseDia() {
        // Luis: martes 16:00-17:30
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA, LocalTime.of(17, 0), LocalTime.of(18, 0))).isTrue();
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA, LocalTime.of(15, 0), LocalTime.of(16, 30))).isTrue();
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA, LocalTime.of(16, 0), LocalTime.of(17, 30))).isTrue();
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA, LocalTime.of(16, 30), LocalTime.of(17, 0))).isTrue();
    }

    @Test
    void tocarseConLaFranjaOEstarEnOtroDiaOConOtroEntrenadorNoCruza() {
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA, LocalTime.of(17, 30), LocalTime.of(19, 0))).isFalse();
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA, LocalTime.of(14, 0), LocalTime.of(16, 0))).isFalse();
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA.plusDays(1), LocalTime.of(16, 0), LocalTime.of(17, 30))).isFalse();
        assertThat(dao.hayFranjaQueSeCruza(ana, FECHA, LocalTime.of(16, 0), LocalTime.of(17, 30)))
                .as("la franja de Semillero es por turnos: no es de Ana").isFalse();
    }

    @Test
    void laFechaDecideElDiaDeLaSemanaDeLaFranja() {
        // 17 de noviembre es otro martes: la misma franja semanal se cruza
        assertThat(dao.hayFranjaQueSeCruza(luis, LocalDate.of(2026, 11, 17), LocalTime.of(16, 0), LocalTime.of(17, 0))).isTrue();
        // 16 de noviembre es lunes
        assertThat(dao.hayFranjaQueSeCruza(luis, LocalDate.of(2026, 11, 16), LocalTime.of(16, 0), LocalTime.of(17, 0))).isFalse();
    }

    @Test
    void unaFranjaCanceladaEsaFechaYaNoCruzaPeroLasDemasFechasSi() {
        dao.crearCancelacion(franjaSub12, FECHA, null, null);

        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA, LocalTime.of(16, 0), LocalTime.of(17, 30))).isFalse();
        assertThat(dao.hayFranjaQueSeCruza(luis, FECHA.plusDays(7), LocalTime.of(16, 0), LocalTime.of(17, 30))).isTrue();
    }

    // --- Datos ----------------------------------------------------------------------------------------------

    private static ExcepcionHorario extra(UUID categoria, LocalDate fecha, String inicio, String fin, UUID entrenador,
                                          String grupo, String motivo, UUID creadoPor) {
        return new ExcepcionHorario(null, ExcepcionHorario.EXTRA, fecha, null, categoria, null, null,
                LocalTime.parse(inicio), LocalTime.parse(fin), "Horizontes", entrenador, null, null, grupo, motivo, creadoPor);
    }

    private static ExcepcionHorario extra(UUID categoria, LocalDate fecha, String inicio, String fin, UUID entrenador,
                                          String grupo, String motivo) {
        return extra(categoria, fecha, inicio, fin, entrenador, grupo, motivo, null);
    }

    private static ExcepcionHorario extraConSede(UUID categoria, String sede) {
        return new ExcepcionHorario(null, ExcepcionHorario.EXTRA, FECHA, null, categoria, null, null,
                LocalTime.of(18, 0), LocalTime.of(19, 0), sede, null, null, null, null, null, null);
    }

    private UUID franja(UUID categoria, int dia, String inicio, String fin, UUID entrenador) {
        return jdbc.queryForObject("""
                INSERT INTO franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, id_entrenador)
                VALUES (:cat, :dia, :inicio, :fin, 'Horizontes', :entrenador) RETURNING id_franja""",
                new MapSqlParameterSource("cat", categoria).addValue("dia", dia)
                        .addValue("inicio", LocalTime.parse(inicio)).addValue("fin", LocalTime.parse(fin))
                        .addValue("entrenador", entrenador),
                UUID.class);
    }

    private UUID cuenta(String correo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", new MapSqlParameterSource("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, 'Cuenta', 'Prueba', :c)",
                new MapSqlParameterSource("id", id).addValue("c", correo));
        return id;
    }

    private void vincular(UUID entrenador, UUID usuario) {
        jdbc.update("UPDATE entrenador SET id_usuario = :u WHERE id_entrenador = :e",
                new MapSqlParameterSource("u", usuario).addValue("e", entrenador));
    }
}
