package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.persistence.dao.ConsultaDeportistas;
import co.edu.futdata.persistence.dao.DeportistaDeSesion;
import co.edu.futdata.persistence.dao.OrdenDeportista;
import co.edu.futdata.persistence.dao.RegistroAsistencia;
import co.edu.futdata.persistence.dao.SolapeDeEntrenamientoException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link AsistenciaDAOImpl} contra un PostgreSQL real con todas las migraciones: franja cancelada, la lista de la sesión
 * (categoría calculada, activos, grupo, estado guardado), buscar o crear el entrenamiento (carrera y solape real) y el
 * upsert todo o nada.
 */
@ExtendWith(DockerDisponible.class)
class AsistenciaDAOImplTest {

    /** Un martes. */
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 3);
    private static final int ANIO = 2026;
    private static final LocalTime INICIO = LocalTime.of(16, 0);
    private static final LocalTime FIN = LocalTime.of(17, 30);
    private static final String SEDE = "Horizontes";

    private NamedParameterJdbcTemplate jdbc;
    private AsistenciaDAOImpl dao;
    private UUID luis;
    private UUID ana;
    private UUID sub12;
    private UUID sub13;
    private UUID semillero;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        dao = new AsistenciaDAOImpl(jdbc);
        luis = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        ana = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        sub12 = PostgresDePrueba.categoria("Sub-12", luis);
        sub13 = PostgresDePrueba.categoria("Sub-13", ana);
        semillero = PostgresDePrueba.categoria("Semillero", ana);
    }

    @AfterEach
    void limpiarTodo() {
        jdbc.getJdbcOperations().update("DELETE FROM asistencia");
        jdbc.getJdbcOperations().update("DELETE FROM entrenamiento");
        jdbc.getJdbcOperations().update("DELETE FROM excepcion_horario");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");
        jdbc.getJdbcOperations().update("DELETE FROM deportista WHERE documento_identidad LIKE '1098%'");
        PostgresDePrueba.limpiar();
    }

    // --- Franja cancelada -----------------------------------------------------------------------------------

    @Test
    void laFranjaSoloEstaCanceladaEnLaFechaCancelada() {
        UUID franja = franja(sub12, 2, luis);
        assertThat(dao.franjaCancelada(franja, FECHA)).isFalse();

        jdbc.update("INSERT INTO excepcion_horario (tipo, fecha, id_franja) VALUES ('CANCELACION', :f, :franja)",
                new MapSqlParameterSource("f", FECHA).addValue("franja", franja));

        assertThat(dao.franjaCancelada(franja, FECHA)).isTrue();
        assertThat(dao.franjaCancelada(franja, FECHA.plusDays(7))).isFalse();
        assertThat(dao.franjaCancelada(franja(sub13, 2, ana), FECHA)).isFalse();
    }

    // --- Lista de la sesión ---------------------------------------------------------------------------------

    @Test
    void laListaTraeSoloLosActivosDeLaCategoriaCalculadaOrdenadosPorApellidoYNombre() {
        UUID zapata = deportista("Zoe", "Zapata", 2014, null, true, null);
        UUID alvarez = deportista("Beto", "Alvarez", 2014, null, true, null);
        UUID alvarezA = deportista("Ana", "Alvarez", 2014, null, true, null);
        deportista("Inactivo", "Pérez", 2014, null, false, null);
        deportista("Otro", "Año", 2012, null, true, null); // Sub-14: otra categoría por año de nacimiento
        deportista("Fuera", "Rango", 2000, null, true, null); // sin categoría

        List<DeportistaDeSesion> lista = dao.listarDeportistas(sub12, null, ANIO, null);

        assertThat(lista).extracting(DeportistaDeSesion::idDeportista).containsExactly(alvarezA, alvarez, zapata);
        assertThat(lista).allSatisfy(d -> {
            assertThat(d.estado()).isNull();
            assertThat(d.observaciones()).isNull();
        });
    }

    @Test
    void laCategoriaSalePorAnioDeNacimientoNoPorLaColumnaHeredada() {
        // heredada = Sub-12 pero nació en 2012 (Sub-14): no sale; sin heredada pero nacido en 2014: sale
        UUID heredadaVieja = deportista("Viejo", "Heredado", 2012, null, true, sub12);
        UUID sinHeredada = deportista("Nuevo", "SinColumna", 2014, null, true, null);

        assertThat(dao.listarDeportistas(sub12, null, ANIO, null)).extracting(DeportistaDeSesion::idDeportista)
                .containsExactly(sinHeredada).doesNotContain(heredadaVieja);
    }

    @Test
    void laListaCoincideConLaDelFiltroDeCategoriaDeDeportistaDAO() {
        deportista("A", "Alfa", 2014, null, true, null);
        deportista("B", "Beta", 2014, null, true, sub13);
        deportista("C", "Gama", 2013, null, true, null);
        deportista("D", "Cuatro", 2014, null, false, null);
        deportista("E", "Cinco", 2000, null, true, null);
        var deportistas = new DeportistaDAOImpl(jdbc);

        for (String categoria : List.of("Sub-12", "Sub-13")) {
            UUID id = categoria.equals("Sub-12") ? sub12 : sub13;
            List<UUID> delFiltro = deportistas.listar(new ConsultaDeportistas(true, List.of(categoria), null, ANIO,
                    OrdenDeportista.POR_DEFECTO, false, 0, 100), false).stream().map(Deportista::getId).toList();

            assertThat(dao.listarDeportistas(id, null, ANIO, null)).extracting(DeportistaDeSesion::idDeportista)
                    .containsExactlyElementsOf(delFiltro);
        }
    }

    @Test
    void conGrupoSoloSalenLosDeEseGrupoYSinGrupoSalenTodos() {
        UUID manana = deportista("Ma", "Ñana", 2016, "mañana", true, null); // Semillero 9-10: 2016 -> 10
        UUID tarde = deportista("Ta", "Rde", 2016, "tarde", true, null);
        UUID sinGrupo = deportista("Sin", "Grupo", 2016, null, true, null);

        assertThat(dao.listarDeportistas(semillero, "mañana", ANIO, null)).extracting(DeportistaDeSesion::idDeportista)
                .containsExactly(manana);
        assertThat(dao.listarDeportistas(semillero, "tarde", ANIO, null)).extracting(DeportistaDeSesion::idDeportista)
                .containsExactly(tarde);
        assertThat(dao.listarDeportistas(semillero, null, ANIO, null)).extracting(DeportistaDeSesion::idDeportista)
                .containsExactlyInAnyOrder(manana, tarde, sinGrupo);
    }

    @Test
    void laListaTraeElEstadoYLasObservacionesYaGuardadosDeEsaSesionYNoDeOtra() {
        UUID uno = deportista("Ana", "Alfa", 2014, null, true, null);
        UUID dos = deportista("Beto", "Beta", 2014, null, true, null);
        UUID sesion = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
        UUID otraSesion = dao.buscarOCrearEntrenamiento(sub12, FECHA.plusDays(1), INICIO, FIN, SEDE, luis);
        dao.guardar(sesion, luis, List.of(new RegistroAsistencia(uno, "JUSTIFICADO", "Cita médica")));
        dao.guardar(otraSesion, luis, List.of(new RegistroAsistencia(dos, "AUSENTE", null)));

        List<DeportistaDeSesion> lista = dao.listarDeportistas(sub12, null, ANIO, sesion);

        assertThat(lista).extracting(DeportistaDeSesion::idDeportista, DeportistaDeSesion::estado,
                DeportistaDeSesion::observaciones)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(uno, "JUSTIFICADO", "Cita médica"),
                        org.assertj.core.groups.Tuple.tuple(dos, null, null));
    }

    // --- Buscar o crear el entrenamiento --------------------------------------------------------------------

    @Test
    void creaElEntrenamientoLaPrimeraVezYLoDevuelveLaSiguiente() {
        UUID primero = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
        UUID segundo = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);

        assertThat(segundo).isEqualTo(primero);
        assertThat(dao.buscarEntrenamiento(sub12, FECHA, INICIO, SEDE)).contains(primero);
        assertThat(dao.buscarEntrenamiento(sub12, FECHA, INICIO, "UVA El Paraíso")).isEmpty();
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT count(*) FROM entrenamiento WHERE estado = 'PROGRAMADO' AND hora_fin = time '17:30'
                   AND lugar = 'Horizontes' AND id_entrenador = ?""", Integer.class, luis)).isEqualTo(1);
    }

    @Test
    void unaSesionPorTurnosQuedaConEntrenadorNulo() {
        UUID id = dao.buscarOCrearEntrenamiento(semillero, FECHA, INICIO, FIN, SEDE, null);

        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT id_entrenador IS NULL FROM entrenamiento WHERE id_entrenamiento = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void dosCategoriasALaMismaHoraYSedeTienenCadaUnaSuEntrenamiento() {
        UUID a = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
        UUID b = dao.buscarOCrearEntrenamiento(sub13, FECHA, INICIO, FIN, SEDE, ana);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void unSolapeRealDeLaMismaCategoriaLanzaSolapeYNoDejaLaTransaccionAbortada() {
        dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
        var transaccion = new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource()));

        Integer despues = transaccion.execute(estado -> {
            // 17:00-18:30 se cruza con 16:00-17:30 de la misma categoría
            assertThatThrownBy(() -> dao.buscarOCrearEntrenamiento(sub12, FECHA, LocalTime.of(17, 0),
                    LocalTime.of(18, 30), SEDE, luis)).isInstanceOf(SolapeDeEntrenamientoException.class);
            // la transacción sigue usable: no quedó abortada por la violación 23P01
            return jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM entrenamiento", Integer.class);
        });

        assertThat(despues).isEqualTo(1);
    }

    @Test
    void unSolapeDelEntrenadorConOtraCategoriaTambienLanzaSolape() {
        dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);

        assertThatThrownBy(() -> dao.buscarOCrearEntrenamiento(sub13, FECHA, LocalTime.of(17, 0), LocalTime.of(18, 30),
                "UVA El Paraíso", luis)).isInstanceOf(SolapeDeEntrenamientoException.class);
    }

    @Test
    void sinEntrenadorNoHaySolapeDeEntrenadorPeroLaCategoriaSiSeCruza() {
        dao.buscarOCrearEntrenamiento(semillero, FECHA, INICIO, FIN, SEDE, null);

        assertThat(dao.buscarOCrearEntrenamiento(sub13, FECHA, INICIO, FIN, SEDE, null)).isNotNull();
        assertThatThrownBy(() -> dao.buscarOCrearEntrenamiento(semillero, FECHA, LocalTime.of(17, 0),
                LocalTime.of(18, 30), SEDE, null)).isInstanceOf(SolapeDeEntrenamientoException.class);
    }

    @Test
    void siOtraPeticionCreaLaMismaSesionAlMismoTiempoSeSigueConLaMismaFilaSinError() throws Exception {
        var transaccion = new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource()));
        CompletableFuture<UUID> segunda = new CompletableFuture<>();

        UUID primera = transaccion.execute(estado -> {
            UUID creada = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
            // La otra petición no ve la fila (sin confirmar): intenta crearla y espera a esta transacción
            CompletableFuture.runAsync(() -> segunda.complete(dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN,
                    SEDE, luis))).exceptionally(e -> {
                        segunda.completeExceptionally(e);
                        return null;
                    });
            esperar(600);
            assertThat(segunda).isNotDone(); // sigue bloqueada por la exclusión
            return creada;
        });

        assertThat(segunda.get(10, TimeUnit.SECONDS)).isEqualTo(primera);
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM entrenamiento", Integer.class)).isEqualTo(1);
    }

    // --- Guardar (upsert) -----------------------------------------------------------------------------------

    @Test
    void guardarInsertaTodosLosRegistrosConQuienPasoLista() {
        UUID uno = deportista("Ana", "Alfa", 2014, null, true, null);
        UUID dos = deportista("Beto", "Beta", 2014, null, true, null);
        UUID sesion = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);

        dao.guardar(sesion, luis, List.of(new RegistroAsistencia(uno, "PRESENTE", null),
                new RegistroAsistencia(dos, "AUSENTE", "Sin aviso")));

        assertThat(dao.listarDeportistas(sub12, null, ANIO, sesion)).extracting(DeportistaDeSesion::estado,
                DeportistaDeSesion::observaciones).containsExactly(
                org.assertj.core.groups.Tuple.tuple("PRESENTE", null), org.assertj.core.groups.Tuple.tuple("AUSENTE", "Sin aviso"));
        assertThat(entrenadoresDeLista(sesion)).containsExactly(luis, luis);
    }

    @Test
    void guardarDeNuevoCorrigeSinDuplicarYActualizaEntrenadorYHora() {
        UUID uno = deportista("Ana", "Alfa", 2014, null, true, null);
        UUID sesion = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
        dao.guardar(sesion, luis, List.of(new RegistroAsistencia(uno, "AUSENTE", "Sin aviso")));
        Instant primera = registradoEn(uno, sesion);

        dao.guardar(sesion, ana, List.of(new RegistroAsistencia(uno, "JUSTIFICADO", null)));

        assertThat(contar(sesion)).isEqualTo(1);
        DeportistaDeSesion d = dao.listarDeportistas(sub12, null, ANIO, sesion).get(0);
        assertThat(d.estado()).isEqualTo("JUSTIFICADO");
        assertThat(d.observaciones()).isNull();
        assertThat(entrenadoresDeLista(sesion)).containsExactly(ana);
        assertThat(registradoEn(uno, sesion)).isAfter(primera);
    }

    @Test
    void elAdministradorPasaListaConEntrenadorNulo() {
        UUID uno = deportista("Ana", "Alfa", 2016, null, true, null);
        UUID sesion = dao.buscarOCrearEntrenamiento(semillero, FECHA, INICIO, FIN, SEDE, null);

        dao.guardar(sesion, null, List.of(new RegistroAsistencia(uno, "PRESENTE", null)));

        assertThat(entrenadoresDeLista(sesion)).containsExactly((UUID) null);
    }

    @Test
    void guardarSoloPartedeLaListaDejaLosDemasSinTocar() {
        UUID uno = deportista("Ana", "Alfa", 2014, null, true, null);
        UUID dos = deportista("Beto", "Beta", 2014, null, true, null);
        UUID sesion = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
        dao.guardar(sesion, luis, List.of(new RegistroAsistencia(uno, "PRESENTE", null),
                new RegistroAsistencia(dos, "PRESENTE", null)));

        dao.guardar(sesion, luis, List.of(new RegistroAsistencia(dos, "AUSENTE", null)));

        assertThat(dao.listarDeportistas(sub12, null, ANIO, sesion)).extracting(DeportistaDeSesion::estado)
                .containsExactly("PRESENTE", "AUSENTE");
    }

    @Test
    void unRegistroInvalidoAMitadDelLoteNoDejaNingunoNiCambiaLoYaGuardado() {
        UUID uno = deportista("Ana", "Alfa", 2014, null, true, null);
        UUID dos = deportista("Beto", "Beta", 2014, null, true, null);
        UUID tres = deportista("Caro", "Gama", 2014, null, true, null);
        UUID sesion = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);
        dao.guardar(sesion, luis, List.of(new RegistroAsistencia(uno, "PRESENTE", "Original")));

        assertThatThrownBy(() -> dao.guardar(sesion, ana, List.of(
                new RegistroAsistencia(uno, "AUSENTE", "Cambio"),
                new RegistroAsistencia(dos, "TARDE", null), // inválido a mitad del lote
                new RegistroAsistencia(tres, "PRESENTE", null)))).isInstanceOf(DataAccessException.class);

        assertThat(contar(sesion)).isEqualTo(1);
        DeportistaDeSesion d = dao.listarDeportistas(sub12, null, ANIO, sesion).get(0);
        assertThat(d.estado()).isEqualTo("PRESENTE");
        assertThat(d.observaciones()).isEqualTo("Original");
        assertThat(entrenadoresDeLista(sesion)).containsExactly(luis);
    }

    @Test
    void guardarUnDeportistaInexistenteFallaYNoDejaNada() {
        UUID uno = deportista("Ana", "Alfa", 2014, null, true, null);
        UUID sesion = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);

        assertThatThrownBy(() -> dao.guardar(sesion, luis, List.of(new RegistroAsistencia(uno, "PRESENTE", null),
                new RegistroAsistencia(UUID.randomUUID(), "PRESENTE", null)))).isInstanceOf(DataAccessException.class);

        assertThat(contar(sesion)).isZero();
    }

    @Test
    void guardarUnaListaVaciaNoHaceNada() {
        UUID sesion = dao.buscarOCrearEntrenamiento(sub12, FECHA, INICIO, FIN, SEDE, luis);

        dao.guardar(sesion, luis, List.of());

        assertThat(contar(sesion)).isZero();
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------

    private UUID franja(UUID categoria, int dia, UUID entrenador) {
        return jdbc.queryForObject("""
                INSERT INTO franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, id_entrenador)
                VALUES (:cat, :dia, time '16:00', time '17:30', 'Horizontes', :entrenador) RETURNING id_franja""",
                new MapSqlParameterSource("cat", categoria).addValue("dia", dia).addValue("entrenador", entrenador),
                UUID.class);
    }

    private int contador;

    private UUID deportista(String nombre, String apellido, int anioNacimiento, String grupo, boolean activo, UUID heredada) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, grupo,
                                        activo, id_categoria)
                VALUES (:nombre, :apellido, 'TI', :doc, :nacimiento, :grupo, :activo, :heredada)
                RETURNING id_deportista""",
                new MapSqlParameterSource("nombre", nombre).addValue("apellido", apellido)
                        .addValue("doc", "1098" + (++contador)).addValue("nacimiento", LocalDate.of(anioNacimiento, 6, 15))
                        .addValue("grupo", grupo).addValue("activo", activo).addValue("heredada", heredada),
                UUID.class);
    }

    private int contar(UUID sesion) {
        return jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM asistencia WHERE id_entrenamiento = ?",
                Integer.class, sesion);
    }

    /** Quién pasó lista en cada fila, en el orden de la lista (apellido y nombre). */
    private List<UUID> entrenadoresDeLista(UUID sesion) {
        return jdbc.getJdbcOperations().query("""
                SELECT a.id_entrenador FROM asistencia a JOIN deportista d ON d.id_deportista = a.id_deportista
                 WHERE a.id_entrenamiento = ? ORDER BY d.apellido, d.nombre""",
                (fila, n) -> fila.getObject(1, UUID.class), sesion);
    }

    private Instant registradoEn(UUID deportista, UUID sesion) {
        return jdbc.getJdbcOperations().queryForObject(
                "SELECT registrado_en FROM asistencia WHERE id_deportista = ? AND id_entrenamiento = ?",
                (fila, n) -> fila.getTimestamp(1).toInstant(), deportista, sesion);
    }

    private static void esperar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
