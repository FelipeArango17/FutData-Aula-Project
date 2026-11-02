package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** {@link FranjaHorarioDAOImpl} contra PostgreSQL con las migraciones reales (Testcontainers o base local). */
@ExtendWith(DockerDisponible.class)
class FranjaHorarioDAOImplTest {

    private NamedParameterJdbcTemplate jdbc;
    private FranjaHorarioDAOImpl dao;
    private UUID diaz;
    private UUID mora;
    private UUID parvulos;
    private UUID semillero;
    private UUID sub11;
    private UUID sub12;
    private UUID sub16;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        dao = new FranjaHorarioDAOImpl(jdbc);
        diaz = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        mora = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        parvulos = categoria("Párvulos");
        semillero = categoria("Semillero");
        sub11 = categoria("Sub-11");
        sub12 = categoria("Sub-12");
        sub16 = categoria("Sub-16");
    }

    @AfterEach
    void limpiarTodo() {
        PostgresDePrueba.jdbc().getJdbcOperations().update("DELETE FROM franja_horario");
        PostgresDePrueba.limpiar();
    }

    // --- Crear, leer, actualizar y eliminar ---------------------------------------------------------------------

    @Test
    void creaYLeeLaFranjaConElNombreDeLaCategoriaYDelEntrenador() {
        UUID id = dao.crear(franja(semillero, 2, "07:30", "09:00", "UVA El Paraíso", diaz, "mañana"));

        FranjaHorario leida = dao.buscarPorId(id).orElseThrow();
        assertThat(leida.getId()).isEqualTo(id);
        assertThat(leida.getIdCategoria()).isEqualTo(semillero);
        assertThat(leida.getCategoria()).isEqualTo("Semillero");
        assertThat(leida.getDia()).isEqualTo(2);
        assertThat(leida.getHoraInicio()).isEqualTo(LocalTime.of(7, 30));
        assertThat(leida.getHoraFin()).isEqualTo(LocalTime.of(9, 0));
        assertThat(leida.getSede()).isEqualTo("UVA El Paraíso");
        assertThat(leida.getIdEntrenador()).isEqualTo(diaz);
        assertThat(leida.getEntrenador()).isEqualTo("Ana Díaz");
        assertThat(leida.getEntrenadorActivo()).isTrue();
        assertThat(leida.getGrupo()).isEqualTo("mañana");
    }

    @Test
    void sinEntrenadorEsPorTurnosYNoTraeNombreNiEstado() {
        UUID id = dao.crear(franja(parvulos, 6, "09:00", "10:00", "Horizontes", null, null));
        FranjaHorario leida = dao.buscarPorId(id).orElseThrow();
        assertThat(leida.getIdEntrenador()).isNull();
        assertThat(leida.getEntrenador()).isNull();
        assertThat(leida.getEntrenadorActivo()).isNull();
        assertThat(leida.getGrupo()).isNull();
    }

    @Test
    void elEntrenadorDesactivadoSaleConEntrenadorActivoFalse() {
        UUID id = dao.crear(franja(sub12, 1, "19:00", "20:00", "Horizontes", mora, null));
        jdbc.update("UPDATE entrenador SET activo = false WHERE id_entrenador = :id", new MapSqlParameterSource("id", mora));
        assertThat(dao.buscarPorId(id).orElseThrow().getEntrenadorActivo()).isFalse();
    }

    @Test
    void actualizarReemplazaTodosLosCampos() {
        UUID id = dao.crear(franja(semillero, 2, "07:30", "09:00", "UVA El Paraíso", diaz, "mañana"));

        FranjaHorario cambios = franja(sub16, 5, "18:00", "19:00", "Horizontes", null, null);
        cambios.setId(id);
        assertThat(dao.actualizar(cambios)).isTrue();

        FranjaHorario leida = dao.buscarPorId(id).orElseThrow();
        assertThat(leida.getCategoria()).isEqualTo("Sub-16");
        assertThat(leida.getDia()).isEqualTo(5);
        assertThat(leida.getHoraInicio()).isEqualTo(LocalTime.of(18, 0));
        assertThat(leida.getHoraFin()).isEqualTo(LocalTime.of(19, 0));
        assertThat(leida.getSede()).isEqualTo("Horizontes");
        assertThat(leida.getIdEntrenador()).isNull();
        assertThat(leida.getGrupo()).isNull();
    }

    @Test
    void actualizarUnaFranjaQueNoExisteDevuelveFalse() {
        FranjaHorario cambios = franja(sub16, 5, "18:00", "19:00", "Horizontes", null, null);
        cambios.setId(UUID.randomUUID());
        assertThat(dao.actualizar(cambios)).isFalse();
    }

    @Test
    void eliminaYDevuelveFalseSiYaNoExiste() {
        UUID id = dao.crear(franja(sub11, 3, "19:00", "20:00", "Horizontes", null, null));
        assertThat(dao.eliminar(id)).isTrue();
        assertThat(dao.buscarPorId(id)).isEmpty();
        assertThat(dao.eliminar(id)).isFalse();
    }

    @Test
    void buscarUnIdQueNoExisteDevuelveVacio() {
        assertThat(dao.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void nombreDeCategoriaDevuelveElNombreOVacio() {
        assertThat(dao.nombreDeCategoria(sub12)).contains("Sub-12");
        assertThat(dao.nombreDeCategoria(UUID.randomUUID())).isEmpty();
    }

    // --- Listado: orden y filtros -------------------------------------------------------------------------------

    @Test
    void elListadoSeOrdenaPorDiaHoraDeInicioEdadMinimaDeLaCategoriaEId() {
        // Insertadas en desorden. Las dos de Párvulos empatan en día, hora y categoría: las desempata el id
        UUID martes = dao.crear(franja(sub12, 2, "08:00", "09:00", "Horizontes", null, null));
        UUID lunes19 = dao.crear(franja(sub11, 1, "19:00", "20:00", "Horizontes", null, null));
        UUID lunes18Sub16 = dao.crear(franja(sub16, 1, "18:00", "19:00", "Horizontes", null, null));
        UUID lunes18ParvulosA = dao.crear(franja(parvulos, 1, "18:00", "19:00", "UVA El Paraíso", null, null));
        UUID lunes18ParvulosB = dao.crear(franja(parvulos, 1, "18:00", "18:30", "Horizontes", null, null));
        UUID lunes18Semillero = dao.crear(franja(semillero, 1, "18:00", "19:00", "Horizontes", null, null));

        List<UUID> parvulosPorId = List.of(lunes18ParvulosA, lunes18ParvulosB).stream()
                .sorted(Comparator.comparing(UUID::toString)) // PostgreSQL ordena uuid como su texto hexadecimal
                .toList();

        assertThat(dao.listar(null, null, null)).extracting(FranjaHorario::getId).containsExactly(
                parvulosPorId.get(0), parvulosPorId.get(1), lunes18Semillero, lunes18Sub16, lunes19, martes);
    }

    @Test
    void filtraPorCategoriaEntrenadorYSede() {
        UUID a = dao.crear(franja(semillero, 2, "07:30", "09:00", "UVA El Paraíso", diaz, "mañana"));
        UUID b = dao.crear(franja(semillero, 6, "08:30", "10:00", "Horizontes", null, null));
        UUID c = dao.crear(franja(sub12, 1, "19:00", "20:00", "Horizontes", diaz, null));
        UUID d = dao.crear(franja(sub16, 3, "18:00", "19:00", "Horizontes", mora, null));

        assertThat(ids(dao.listar("Semillero", null, null))).containsExactly(a, b);
        assertThat(ids(dao.listar(null, diaz, null))).containsExactly(c, a);
        assertThat(ids(dao.listar(null, null, "Horizontes"))).containsExactly(c, d, b);
        assertThat(ids(dao.listar(null, null, "UVA El Paraíso"))).containsExactly(a);
        assertThat(ids(dao.listar("Semillero", diaz, "UVA El Paraíso"))).containsExactly(a);
        assertThat(ids(dao.listar("Semillero", mora, null))).isEmpty();
        assertThat(ids(dao.listar(null, UUID.randomUUID(), null))).isEmpty();
        assertThat(ids(dao.listar("Sub-13", null, null))).isEmpty();
    }

    // --- Cruce del mismo entrenador (restricción franja_cruce_entrenador_ex) -----------------------------------

    @Test
    void elMismoEntrenadorNoPuedeTenerDosFranjasQueSeCrucenElMismoDia() {
        dao.crear(franja(sub12, 1, "18:00", "19:30", "Horizontes", diaz, null));

        assertThatThrownBy(() -> dao.crear(franja(sub16, 1, "19:00", "20:00", "UVA El Paraíso", diaz, null)))
                .isInstanceOf(CruceDeHorarioException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class)
                .cause().hasMessageContaining(FranjaHorarioDAOImpl.CRUCE_ENTRENADOR);
        // Una contenida dentro de la otra también se cruza
        assertThatThrownBy(() -> dao.crear(franja(sub11, 1, "18:15", "18:45", "Horizontes", diaz, null)))
                .isInstanceOf(CruceDeHorarioException.class);
    }

    @Test
    void actualizarHaciaUnCruceTambienSeRechaza() {
        dao.crear(franja(sub12, 1, "18:00", "19:00", "Horizontes", diaz, null));
        UUID otra = dao.crear(franja(sub16, 1, "19:00", "20:00", "Horizontes", diaz, null));

        FranjaHorario cambios = franja(sub16, 1, "18:30", "19:30", "Horizontes", diaz, null);
        cambios.setId(otra);
        assertThatThrownBy(() -> dao.actualizar(cambios)).isInstanceOf(CruceDeHorarioException.class);
        assertThat(dao.buscarPorId(otra).orElseThrow().getHoraInicio()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void unaFranjaNoChocaConsigoMismaAlActualizar() {
        UUID id = dao.crear(franja(sub12, 1, "18:00", "19:00", "Horizontes", diaz, null));
        FranjaHorario cambios = franja(sub12, 1, "18:30", "19:30", "Horizontes", diaz, null);
        cambios.setId(id);
        assertThat(dao.actualizar(cambios)).isTrue();
    }

    @Test
    void lasFranjasContiguasDelMismoEntrenadorNoSeCruzan() {
        dao.crear(franja(sub12, 1, "18:00", "19:00", "Horizontes", diaz, null));
        assertThatCode(() -> dao.crear(franja(sub16, 1, "19:00", "20:00", "Horizontes", diaz, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> dao.crear(franja(sub11, 1, "17:00", "18:00", "Horizontes", diaz, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void elMismoEntrenadorOtroDiaOOtroEntrenadorALaMismaHoraNoSeCruzan() {
        dao.crear(franja(sub12, 1, "18:00", "19:00", "Horizontes", diaz, null));
        assertThatCode(() -> dao.crear(franja(sub12, 3, "18:00", "19:00", "Horizontes", diaz, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> dao.crear(franja(sub16, 1, "18:00", "19:00", "Horizontes", mora, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void categoriasDistintasPuedenCoincidirEnHoraYSede() {
        // Como en el horario real: Cachorros y Semillero, martes 07:30 en la UVA (por turnos), y Sub-14 y Sub-15
        dao.crear(franja(semillero, 2, "07:30", "09:00", "UVA El Paraíso", null, "mañana"));
        assertThatCode(() -> dao.crear(franja(parvulos, 2, "07:30", "09:00", "UVA El Paraíso", null, null)))
                .doesNotThrowAnyException();
        dao.crear(franja(sub12, 1, "18:00", "19:00", "Horizontes", diaz, null));
        assertThatCode(() -> dao.crear(franja(sub16, 1, "18:00", "19:00", "Horizontes", mora, null)))
                .doesNotThrowAnyException();
        assertThat(dao.listar(null, null, null)).hasSize(4);
    }

    @Test
    void otraViolacionDeIntegridadNoSeConfundeConElCruce() {
        // Una categoría que no existe: llave foránea, no cruce. Sigue al manejador global (409 genérico)
        assertThatThrownBy(() -> dao.crear(franja(UUID.randomUUID(), 1, "18:00", "19:00", "Horizontes", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(CruceDeHorarioException.class);
    }

    // --- Ayudas -------------------------------------------------------------------------------------------------

    private UUID categoria(String nombre) {
        int[] rango = PostgresDePrueba.RANGOS.get(nombre);
        return jdbc.queryForObject("""
                INSERT INTO categoria (nombre, edad_minima, edad_maxima) VALUES (:n, :min, :max)
                RETURNING id_categoria""",
                new MapSqlParameterSource("n", nombre).addValue("min", rango[0]).addValue("max", rango[1]), UUID.class);
    }

    static FranjaHorario franja(UUID categoria, int dia, String inicio, String fin, String sede, UUID entrenador,
                                String grupo) {
        FranjaHorario f = new FranjaHorario();
        f.setIdCategoria(categoria);
        f.setDia(dia);
        f.setHoraInicio(LocalTime.parse(inicio));
        f.setHoraFin(LocalTime.parse(fin));
        f.setSede(sede);
        f.setIdEntrenador(entrenador);
        f.setGrupo(grupo);
        return f;
    }

    private static List<UUID> ids(List<FranjaHorario> franjas) {
        return franjas.stream().map(FranjaHorario::getId).toList();
    }
}
