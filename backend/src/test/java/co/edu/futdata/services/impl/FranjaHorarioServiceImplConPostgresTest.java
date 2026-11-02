package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.api.dto.FranjaHorarioRequestDTO;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import co.edu.futdata.domain.rules.ReglasDeportista;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.EntrenadorDAOImpl;
import co.edu.futdata.persistence.impl.FranjaHorarioDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El servicio del horario con los DAO reales sobre PostgreSQL: el {@code 409} del cruce sale de la restricción
 * {@code franja_cruce_entrenador_ex} de verdad (si PostgreSQL dejara de nombrarla en su mensaje, estas pruebas
 * fallan), el entrenador inactivo y el seed del club.
 */
@ExtendWith(DockerDisponible.class)
class FranjaHorarioServiceImplConPostgresTest {

    private static final String CRUCE = "Ese entrenador ya tiene otra franja que se cruza con este horario";

    private NamedParameterJdbcTemplate jdbc;
    private FranjaHorarioServiceImpl servicio;
    private TransactionTemplate transaccion;
    private UUID diaz;
    private UUID mora;
    private Map<String, UUID> categorias;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        servicio = new FranjaHorarioServiceImpl(new FranjaHorarioDAOImpl(jdbc), new EntrenadorDAOImpl(jdbc));
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource()));
        diaz = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        mora = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        // Las 9 categorías del club, sin entrenador, como en una base nueva
        ReglasDeportista.CATEGORIAS.forEach(c -> jdbc.update(
                "INSERT INTO categoria (nombre, edad_minima, edad_maxima) VALUES (:n, :min, :max)",
                new MapSqlParameterSource("n", c.nombre()).addValue("min", c.edadMinima()).addValue("max", c.edadMaxima())));
        categorias = jdbc.getJdbcOperations().query("SELECT nombre, id_categoria FROM categoria",
                (fila, n) -> Map.entry(fila.getString(1), fila.getObject(2, UUID.class)))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @AfterEach
    void limpiarTodo() {
        PostgresDePrueba.jdbc().getJdbcOperations().update("DELETE FROM franja_horario");
        PostgresDePrueba.limpiar();
    }

    private <T> T enTransaccion(Supplier<T> accion) {
        return transaccion.execute(estado -> accion.get());
    }

    private FranjaHorarioRequestDTO pedido(String categoria, int dia, String inicio, String fin, UUID entrenador) {
        return new FranjaHorarioRequestDTO(categorias.get(categoria), dia, inicio, fin, "Horizontes", entrenador, null);
    }

    private void desactivar(UUID entrenador) {
        jdbc.update("UPDATE entrenador SET activo = false WHERE id_entrenador = :id",
                new MapSqlParameterSource("id", entrenador));
    }

    // --- Cruce ----------------------------------------------------------------------------------------------

    @Test
    void crearUnaFranjaQueSeCruzaConOtraDelMismoEntrenadorDa409ConElMensajeExacto() {
        enTransaccion(() -> servicio.crear(pedido("Sub-12", 1, "18:00", "19:30", diaz)));

        assertThatThrownBy(() -> enTransaccion(() -> servicio.crear(pedido("Sub-16", 1, "19:00", "20:00", diaz))))
                .isInstanceOf(ConflictoException.class)
                .hasMessage(CRUCE)
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("idEntrenador"));
        assertThat(enTransaccion(() -> servicio.listar(null, null, null))).hasSize(1);
    }

    @Test
    void actualizarHaciaUnCruceDa409YNoCambiaNada() {
        enTransaccion(() -> servicio.crear(pedido("Sub-12", 1, "18:00", "19:00", diaz)));
        FranjaHorarioResponseDTO otra = enTransaccion(() -> servicio.crear(pedido("Sub-16", 1, "19:00", "20:00", diaz)));

        assertThatThrownBy(() -> enTransaccion(() ->
                servicio.actualizar(otra.idFranja(), pedido("Sub-16", 1, "18:30", "20:00", diaz))))
                .isInstanceOf(ConflictoException.class)
                .hasMessage(CRUCE);
        assertThat(enTransaccion(() -> servicio.consultar(otra.idFranja())).horaInicio()).isEqualTo("19:00");
    }

    @Test
    void unPutQueSoloMueveLaFranjaNoChocaConsigoMisma() {
        FranjaHorarioResponseDTO franja = enTransaccion(() -> servicio.crear(pedido("Sub-12", 1, "18:00", "19:00", diaz)));
        FranjaHorarioResponseDTO movida = enTransaccion(() ->
                servicio.actualizar(franja.idFranja(), pedido("Sub-12", 1, "18:30", "19:30", diaz)));
        assertThat(movida.horaInicio()).isEqualTo("18:30");
        assertThat(movida.horaFin()).isEqualTo("19:30");
    }

    @Test
    void lasFranjasContiguasDelMismoEntrenadorSeAceptan() {
        enTransaccion(() -> servicio.crear(pedido("Sub-12", 1, "18:00", "19:00", diaz)));
        FranjaHorarioResponseDTO contigua = enTransaccion(() -> servicio.crear(pedido("Sub-16", 1, "19:00", "20:00", diaz)));
        assertThat(contigua.horaInicio()).isEqualTo("19:00");
    }

    @Test
    void categoriasDistintasCoincidenEnHoraYSede() {
        enTransaccion(() -> servicio.crear(pedido("Sub-14", 1, "18:00", "19:00", diaz)));
        enTransaccion(() -> servicio.crear(pedido("Sub-15", 1, "18:00", "19:00", mora)));
        enTransaccion(() -> servicio.crear(pedido("Sub-16", 1, "18:00", "19:00", null)));
        assertThat(enTransaccion(() -> servicio.listar(null, null, "Horizontes")))
                .extracting(FranjaHorarioResponseDTO::categoria).containsExactly("Sub-14", "Sub-15", "Sub-16");
    }

    // --- Entrenador inactivo --------------------------------------------------------------------------------

    @Test
    void unPutQueConservaAlEntrenadorQueQuedoInactivoSeAcepta() {
        FranjaHorarioResponseDTO franja = enTransaccion(() -> servicio.crear(pedido("Sub-12", 1, "18:00", "19:00", diaz)));
        desactivar(diaz);

        FranjaHorarioResponseDTO corregida = enTransaccion(() ->
                servicio.actualizar(franja.idFranja(), pedido("Sub-12", 1, "19:00", "20:00", diaz)));

        assertThat(corregida.horaInicio()).isEqualTo("19:00");
        assertThat(corregida.idEntrenador()).isEqualTo(diaz);
        assertThat(corregida.entrenador()).isEqualTo("Ana Díaz");
        assertThat(corregida.entrenadorActivo()).isFalse();
    }

    @Test
    void unPutQueCambiaAUnEntrenadorInactivoDa400() {
        FranjaHorarioResponseDTO franja = enTransaccion(() -> servicio.crear(pedido("Sub-12", 1, "18:00", "19:00", diaz)));
        desactivar(mora);

        assertThatThrownBy(() -> enTransaccion(() ->
                servicio.actualizar(franja.idFranja(), pedido("Sub-12", 1, "18:00", "19:00", mora))))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("idEntrenador: el entrenador está inactivo; actívalo o elige otro");
        assertThat(enTransaccion(() -> servicio.consultar(franja.idFranja())).idEntrenador()).isEqualTo(diaz);
    }

    @Test
    void crearConUnEntrenadorInactivoDa400() {
        desactivar(mora);
        assertThatThrownBy(() -> enTransaccion(() -> servicio.crear(pedido("Sub-12", 1, "18:00", "19:00", mora))))
                .isInstanceOf(DatosInvalidosException.class)
                .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos()).containsExactly("idEntrenador"));
    }

    @Test
    void categoriaOEntrenadorQueNoExistenDan400ConSuCampo() {
        assertThatThrownBy(() -> enTransaccion(() -> servicio.crear(
                new FranjaHorarioRequestDTO(UUID.randomUUID(), 1, "18:00", "19:00", "Horizontes", UUID.randomUUID(), null))))
                .isInstanceOf(DatosInvalidosException.class)
                .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos())
                        .containsExactly("idCategoria", "idEntrenador"));
    }

    // --- Seed del club --------------------------------------------------------------------------------------

    @Test
    void elSeedDelClubSeListaCon30FranjasY8ConGrupoEnHHmm() throws Exception {
        // Desde backend/ (Maven) o desde la raíz del repositorio, como PostgresDePrueba
        Path raiz = Files.exists(Path.of("..", "supabase")) ? Path.of("..") : Path.of(".");
        Path seed = raiz.resolve("supabase").resolve("seeds").resolve("franjas_club.sql");
        jdbc.getJdbcOperations().execute(Files.readString(seed, StandardCharsets.UTF_8));

        List<FranjaHorarioResponseDTO> franjas = enTransaccion(() -> servicio.listar(null, null, null));

        assertThat(franjas).hasSize(30);
        assertThat(franjas).filteredOn(f -> f.grupo() != null).hasSize(8)
                .allSatisfy(f -> {
                    assertThat(f.categoria()).isIn("Cachorros", "Semillero");
                    assertThat(f.dia()).isIn(2, 4);
                });
        assertThat(franjas).allSatisfy(f -> {
            assertThat(f.horaInicio()).matches("([01]\\d|2[0-3]):[0-5]\\d");
            assertThat(f.horaFin()).matches("([01]\\d|2[0-3]):[0-5]\\d");
            assertThat(f.idEntrenador()).isNull();
            assertThat(f.entrenador()).isNull();
            assertThat(f.entrenadorActivo()).isNull();
        });
        // La primera del lunes es Sub-14 a las 18:00 (antes que Sub-15 por edad); la última, Párvulos el sábado 09:00
        assertThat(franjas.get(0)).extracting(FranjaHorarioResponseDTO::dia, FranjaHorarioResponseDTO::horaInicio,
                FranjaHorarioResponseDTO::horaFin, FranjaHorarioResponseDTO::categoria)
                .containsExactly(1, "18:00", "19:00", "Sub-14");
        assertThat(franjas.getLast()).extracting(FranjaHorarioResponseDTO::dia, FranjaHorarioResponseDTO::horaInicio,
                FranjaHorarioResponseDTO::categoria)
                .containsExactly(6, "09:00", "Párvulos");
        assertThat(enTransaccion(() -> servicio.listar("Semillero", null, "UVA El Paraíso"))).hasSize(4);
    }
}
