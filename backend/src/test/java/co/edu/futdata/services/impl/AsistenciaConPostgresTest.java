package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import co.edu.futdata.api.dto.AsistenciaRequestDTO;
import co.edu.futdata.api.dto.DeportistaAsistenciaDTO;
import co.edu.futdata.api.dto.RegistroAsistenciaRequestDTO;
import co.edu.futdata.api.dto.SesionAsistenciaResponseDTO;
import co.edu.futdata.domain.rules.ReglasAsistencia;
import co.edu.futdata.persistence.impl.AsistenciaDAOImpl;
import co.edu.futdata.persistence.impl.CategoriaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.EntrenadorDAOImpl;
import co.edu.futdata.persistence.impl.ExcepcionHorarioDAOImpl;
import co.edu.futdata.persistence.impl.FranjaHorarioDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.SinPermisoAsistenciaException;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La asistencia de punta a punta con los DAO y el servicio reales sobre PostgreSQL: lo que queda en {@code entrenamiento} y
 * {@code asistencia} (entrenador asignado y quien pasó lista), el upsert, el todo o nada con la transacción real y que el
 * GET no crea nada.
 */
@ExtendWith(DockerDisponible.class)
class AsistenciaConPostgresTest {

    /** Miércoles 4 de noviembre de 2026; el martes anterior es el 3. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final String MARTES = "2026-11-03";

    private NamedParameterJdbcTemplate jdbc;
    private AsistenciaServiceImpl servicio;
    private AsistenciaDAOImpl asistencias;
    private UUID luis;
    private UUID ana;
    private Solicitante admin;
    private Solicitante entrenadorLuis;
    private Solicitante entrenadorAna;
    private Solicitante tesorero;
    private Solicitante acudiente;
    private UUID sub12;
    private UUID semillero;
    /** Martes 16:00-17:30, Sub-12, la dirige Luis. */
    private UUID franjaSub12;
    /** Martes 16:00-17:30, Semillero (de Ana), "por turnos". */
    private UUID franjaPorTurnos;
    private UUID alfa;
    private UUID beta;
    private int contador;

    private AsistenciaServiceImpl servicioCon(AsistenciaDAOImpl dao) {
        return new AsistenciaServiceImpl(dao, new FranjaHorarioDAOImpl(jdbc), new ExcepcionHorarioDAOImpl(jdbc),
                new EntrenadorDAOImpl(jdbc), new CategoriaDAOImpl(jdbc), RELOJ,
                new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource())));
    }

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        asistencias = new AsistenciaDAOImpl(jdbc);
        servicio = servicioCon(asistencias);
        luis = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        ana = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        UUID usuarioLuis = cuenta("asistencia-pg-luis@pradofc.co");
        UUID usuarioAna = cuenta("asistencia-pg-ana@pradofc.co");
        vincular(luis, usuarioLuis);
        vincular(ana, usuarioAna);
        admin = new Solicitante(cuenta("asistencia-pg-admin@pradofc.co"), Solicitante.ADMINISTRADOR);
        entrenadorLuis = new Solicitante(usuarioLuis, Solicitante.ENTRENADOR);
        entrenadorAna = new Solicitante(usuarioAna, Solicitante.ENTRENADOR);
        tesorero = new Solicitante(cuenta("asistencia-pg-tesorero@pradofc.co"), "TESORERO");
        acudiente = new Solicitante(cuenta("asistencia-pg-acudiente@pradofc.co"), Solicitante.ACUDIENTE);
        sub12 = PostgresDePrueba.categoria("Sub-12", luis);
        semillero = PostgresDePrueba.categoria("Semillero", ana);
        franjaSub12 = franja(sub12, 2, "16:00", "17:30", luis, null);
        franjaPorTurnos = franja(semillero, 2, "16:00", "17:30", null, null);
        alfa = deportista("Ana", "Alfa", 2014, null);
        beta = deportista("Beto", "Beta", 2014, null);
    }

    @AfterEach
    void limpiarTodo() {
        jdbc.getJdbcOperations().update("DELETE FROM asistencia");
        jdbc.getJdbcOperations().update("DELETE FROM entrenamiento");
        jdbc.getJdbcOperations().update("DELETE FROM excepcion_horario");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");
        jdbc.getJdbcOperations().update("DELETE FROM deportista WHERE documento_identidad LIKE '1097%'");
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'asistencia-pg-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
        PostgresDePrueba.limpiar();
    }

    // --- Quién queda en cada tabla --------------------------------------------------------------------------

    @Test
    void elAdministradorEnUnaFranjaConEntrenadorDejaAlAsignadoEnElEntrenamientoYNuloEnLaAsistencia() {
        SesionAsistenciaResponseDTO r = servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null),
                registro(beta, "AUSENTE", "Sin aviso")), admin);

        assertThat(entrenadorDelEntrenamiento(sub12)).isEqualTo(luis);
        assertThat(entrenadoresDeAsistencia()).containsExactly((UUID) null, null);
        assertThat(r.sesion().idEntrenador()).isEqualTo(luis);
        assertThat(r.sesion().entrenador()).isEqualTo("Luis Mora");
        assertThat(r.deportistas()).extracting(DeportistaAsistenciaDTO::estado, DeportistaAsistenciaDTO::observaciones)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PRESENTE", null),
                        org.assertj.core.groups.Tuple.tuple("AUSENTE", "Sin aviso"));
    }

    @Test
    void elAdministradorEnUnaFranjaPorTurnosDejaAmbosEntrenadoresEnNull() {
        UUID nino = deportista("Zoe", "Zeta", 2016, null);

        servicio.registrar(pedido(franjaPorTurnos, null, registro(nino, "PRESENTE", null)), admin);

        assertThat(entrenadorDelEntrenamiento(semillero)).isNull();
        assertThat(entrenadoresDeAsistencia()).containsExactly((UUID) null);
    }

    @Test
    void elEntrenadorDeLaCategoriaDejaSuFichaComoQuienPasoLista() {
        servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null)), entrenadorLuis);

        assertThat(entrenadoresDeAsistencia()).containsExactly(luis);
        assertThat(entrenadorDelEntrenamiento(sub12)).isEqualTo(luis);
    }

    @Test
    void unEntrenadorEnUnaSesionPorTurnosDeSuCategoriaPasaListaYElAsignadoSigueNulo() {
        UUID nino = deportista("Zoe", "Zeta", 2016, null);

        servicio.registrar(pedido(franjaPorTurnos, null, registro(nino, "JUSTIFICADO", null)), entrenadorAna);

        assertThat(entrenadorDelEntrenamiento(semillero)).isNull();
        assertThat(entrenadoresDeAsistencia()).containsExactly(ana);
    }

    @Test
    void elTesoreroYElAcudienteRecibenPermisoDenegadoYNoSeEscribeNada() {
        for (Solicitante s : List.of(tesorero, acudiente)) {
            assertThatThrownBy(() -> servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null)), s))
                    .isInstanceOf(SinPermisoException.class);
        }
        assertThat(contar("entrenamiento")).isZero();
        assertThat(contar("asistencia")).isZero();
    }

    @Test
    void elEntrenadorDeOtraCategoriaRecibe403YNoSeEscribeNada() {
        assertThatThrownBy(() -> servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null)),
                entrenadorAna)).isInstanceOf(SinPermisoAsistenciaException.class);

        assertThat(contar("entrenamiento")).isZero();
    }

    @Test
    void elEntrenadorQueDirigeLaFranjaDeOtraCategoriaPuedeComoEnElHorario() {
        UUID sub13 = PostgresDePrueba.categoria("Sub-13", ana);
        UUID franjaAjena = franja(sub13, 2, "18:00", "19:00", luis, null); // Sub-13 es de Ana, la dirige Luis
        UUID nino = deportista("Nico", "Nuevo", 2013, null);

        servicio.registrar(pedido(franjaAjena, null, registro(nino, "PRESENTE", null)), entrenadorLuis);

        assertThat(entrenadoresDeAsistencia()).containsExactly(luis);
    }

    // --- Upsert ---------------------------------------------------------------------------------------------

    @Test
    void volverAGuardarCorrigeSinDuplicarYActualizaEntrenadorYHora() {
        servicio.registrar(pedido(franjaSub12, null, registro(alfa, "AUSENTE", "Sin aviso")), admin);
        Instant primera = registradoEn(alfa);
        assertThat(entrenadoresDeAsistencia()).containsExactly((UUID) null);

        SesionAsistenciaResponseDTO r = servicio.registrar(pedido(franjaSub12, null,
                registro(alfa, "JUSTIFICADO", null)), entrenadorLuis);

        assertThat(contar("entrenamiento")).isEqualTo(1);
        assertThat(contar("asistencia")).isEqualTo(1);
        assertThat(entrenadoresDeAsistencia()).containsExactly(luis);
        assertThat(registradoEn(alfa)).isAfter(primera);
        assertThat(r.deportistas().get(0).estado()).isEqualTo("JUSTIFICADO");
        assertThat(r.deportistas().get(0).observaciones()).isNull();
    }

    @Test
    void elMismoEntrenadorQueVuelveAGuardarActualizaLaHoraYSigueSiendoUnaSolaFila() {
        servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null)), entrenadorLuis);
        Instant primera = registradoEn(alfa);

        servicio.registrar(pedido(franjaSub12, null, registro(alfa, "AUSENTE", "Se fue temprano"),
                registro(beta, "PRESENTE", null)), entrenadorLuis);

        assertThat(contar("asistencia")).isEqualTo(2);
        assertThat(contar("entrenamiento")).isEqualTo(1);
        assertThat(entrenadoresDeAsistencia()).containsExactly(luis, luis);
        assertThat(registradoEn(alfa)).isAfter(primera);
    }

    // --- El GET no crea nada --------------------------------------------------------------------------------

    @Test
    void elGetNoCreaElEntrenamientoYDespuesDelPutTraeLasMarcas() {
        SesionAsistenciaResponseDTO antes = servicio.consultar(MARTES, franjaSub12.toString(), null, entrenadorLuis);

        assertThat(contar("entrenamiento")).isZero();
        assertThat(antes.deportistas()).extracting(DeportistaAsistenciaDTO::idDeportista).containsExactly(alfa, beta);
        assertThat(antes.deportistas()).extracting(DeportistaAsistenciaDTO::estado).containsOnlyNulls();

        servicio.registrar(pedido(franjaSub12, null, registro(beta, "AUSENTE", "Sin aviso")), entrenadorLuis);
        SesionAsistenciaResponseDTO despues = servicio.consultar(MARTES, franjaSub12.toString(), null, entrenadorLuis);

        assertThat(despues.deportistas()).extracting(DeportistaAsistenciaDTO::estado).containsExactly(null, "AUSENTE");
        assertThat(contar("entrenamiento")).isEqualTo(1);
    }

    // --- Todo o nada ----------------------------------------------------------------------------------------

    @Test
    void unDeportistaFueraDeLaListaNoDejaNiElEntrenamientoNiNingunaAsistencia() {
        UUID otraCategoria = deportista("Otro", "Año", 2012, null); // Sub-14

        assertThatThrownBy(() -> servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null),
                registro(otraCategoria, "PRESENTE", null)), admin)).isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsExactly("registros"));

        assertThat(contar("entrenamiento")).isZero();
        assertThat(contar("asistencia")).isZero();
    }

    @Test
    void unFalloAlGuardarDeshaceTambienLaCreacionDelEntrenamiento() {
        AsistenciaDAOImpl fallaAlGuardar = spy(asistencias);
        doThrow(new DataIntegrityViolationException("falla simulada")).when(fallaAlGuardar).guardar(any(), any(), any());

        assertThatThrownBy(() -> servicioCon(fallaAlGuardar).registrar(pedido(franjaSub12, null,
                registro(alfa, "PRESENTE", null)), admin)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(contar("entrenamiento")).isZero();
        assertThat(contar("asistencia")).isZero();
    }

    @Test
    void unFalloEnUnaCorreccionNoCambiaLoQueYaEstabaGuardado() {
        servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", "Original")), entrenadorLuis);
        AsistenciaDAOImpl fallaAlGuardar = spy(asistencias);
        doThrow(new DataIntegrityViolationException("falla simulada")).when(fallaAlGuardar).guardar(any(), any(), any());

        assertThatThrownBy(() -> servicioCon(fallaAlGuardar).registrar(pedido(franjaSub12, null,
                registro(alfa, "AUSENTE", "Cambio")), admin)).isInstanceOf(DataIntegrityViolationException.class);

        SesionAsistenciaResponseDTO r = servicio.consultar(MARTES, franjaSub12.toString(), null, admin);
        assertThat(r.deportistas().get(0).estado()).isEqualTo("PRESENTE");
        assertThat(r.deportistas().get(0).observaciones()).isEqualTo("Original");
        assertThat(entrenadoresDeAsistencia()).containsExactly(luis);
    }

    // --- Grupo, extra, cancelada y solape -------------------------------------------------------------------

    @Test
    void unaSesionConGrupoSoloAceptaYMuestraALosDeEseGrupo() {
        UUID franjaTarde = franja(semillero, 4, "15:00", "16:30", null, "tarde"); // jueves
        UUID manana = deportista("Ma", "Nana", 2016, "mañana");
        UUID tarde = deportista("Ta", "Rde", 2016, "tarde");

        SesionAsistenciaResponseDTO lista = servicio.consultar("2026-10-29", franjaTarde.toString(), null, admin);
        assertThat(lista.deportistas()).extracting(DeportistaAsistenciaDTO::idDeportista).containsExactly(tarde);
        assertThat(lista.sesion().grupo()).isEqualTo("tarde");

        assertThatThrownBy(() -> servicio.registrar(new AsistenciaRequestDTO("2026-10-29", franjaTarde.toString(), null,
                List.of(registro(manana, "PRESENTE", null))), admin)).isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsExactly("registros"));
        servicio.registrar(new AsistenciaRequestDTO("2026-10-29", franjaTarde.toString(), null,
                List.of(registro(tarde, "PRESENTE", null))), admin);
        assertThat(contar("asistencia")).isEqualTo(1);
    }

    @Test
    void unExtraPasaListaConSuHorarioYSuEntrenador() {
        UUID extra = extra(sub12, MARTES, "18:00", "19:00", luis);

        SesionAsistenciaResponseDTO r = servicio.registrar(new AsistenciaRequestDTO(MARTES, null, extra.toString(),
                List.of(registro(alfa, "PRESENTE", null))), entrenadorLuis);

        assertThat(r.sesion().horaInicio()).isEqualTo("18:00");
        assertThat(r.sesion().horaFin()).isEqualTo("19:00");
        assertThat(entrenadorDelEntrenamiento(sub12)).isEqualTo(luis);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT hora_inicio FROM entrenamiento", java.sql.Time.class).toLocalTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void unaFranjaCanceladaEseDiaEs409YNoSeCreaNada() {
        jdbc.update("INSERT INTO excepcion_horario (tipo, fecha, id_franja) VALUES ('CANCELACION', :f, :franja)",
                new MapSqlParameterSource("f", LocalDate.parse(MARTES)).addValue("franja", franjaSub12));

        assertThatThrownBy(() -> servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null)), admin))
                .isInstanceOf(ConflictoException.class).hasMessage(ReglasAsistencia.SESION_CANCELADA);
        assertThatThrownBy(() -> servicio.consultar(MARTES, franjaSub12.toString(), null, admin))
                .isInstanceOf(ConflictoException.class);

        assertThat(contar("entrenamiento")).isZero();
    }

    @Test
    void unSolapeConOtroEntrenamientoDeLaCategoriaEs409SinCamposYNoDejaNada() {
        jdbc.update("""
                INSERT INTO entrenamiento (id_categoria, id_entrenador, fecha, hora_inicio, hora_fin, lugar)
                VALUES (:categoria, NULL, :fecha, time '17:00', time '18:30', 'Horizontes')""",
                new MapSqlParameterSource("categoria", sub12).addValue("fecha", LocalDate.parse(MARTES)));

        assertThatThrownBy(() -> servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null)), admin))
                .isInstanceOfSatisfying(ConflictoException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(ReglasAsistencia.SOLAPE);
                    assertThat(e.getCampos()).isEmpty();
                });

        assertThat(contar("entrenamiento")).isEqualTo(1); // solo el que ya estaba
        assertThat(contar("asistencia")).isZero();
    }

    @Test
    void dosSesionesALaMismaHoraYSedeDeCategoriasDistintasConviven() {
        UUID nino = deportista("Zoe", "Zeta", 2016, null);

        servicio.registrar(pedido(franjaSub12, null, registro(alfa, "PRESENTE", null)), admin);
        servicio.registrar(pedido(franjaPorTurnos, null, registro(nino, "PRESENTE", null)), admin);

        assertThat(contar("entrenamiento")).isEqualTo(2);
        assertThat(contar("asistencia")).isEqualTo(2);
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------

    private static AsistenciaRequestDTO pedido(UUID franja, UUID extra, RegistroAsistenciaRequestDTO... registros) {
        return new AsistenciaRequestDTO(MARTES, franja == null ? null : franja.toString(),
                extra == null ? null : extra.toString(), List.of(registros));
    }

    private static RegistroAsistenciaRequestDTO registro(UUID deportista, String estado, String observaciones) {
        return new RegistroAsistenciaRequestDTO(deportista.toString(), estado, observaciones);
    }

    private UUID franja(UUID categoria, int dia, String inicio, String fin, UUID entrenador, String grupo) {
        return jdbc.queryForObject("""
                INSERT INTO franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, id_entrenador, grupo)
                VALUES (:cat, :dia, :inicio, :fin, 'Horizontes', :entrenador, :grupo) RETURNING id_franja""",
                new MapSqlParameterSource("cat", categoria).addValue("dia", dia).addValue("inicio", LocalTime.parse(inicio))
                        .addValue("fin", LocalTime.parse(fin)).addValue("entrenador", entrenador).addValue("grupo", grupo),
                UUID.class);
    }

    private UUID extra(UUID categoria, String fecha, String inicio, String fin, UUID entrenador) {
        return jdbc.queryForObject("""
                INSERT INTO excepcion_horario (tipo, fecha, id_categoria, hora_inicio, hora_fin, sede, id_entrenador)
                VALUES ('EXTRA', :fecha, :cat, :inicio, :fin, 'Horizontes', :entrenador) RETURNING id_excepcion""",
                new MapSqlParameterSource("fecha", LocalDate.parse(fecha)).addValue("cat", categoria)
                        .addValue("inicio", LocalTime.parse(inicio)).addValue("fin", LocalTime.parse(fin))
                        .addValue("entrenador", entrenador),
                UUID.class);
    }

    private UUID deportista(String nombre, String apellido, int anioNacimiento, String grupo) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, grupo)
                VALUES (:nombre, :apellido, 'TI', :doc, :nacimiento, :grupo) RETURNING id_deportista""",
                new MapSqlParameterSource("nombre", nombre).addValue("apellido", apellido)
                        .addValue("doc", "1097" + (++contador)).addValue("nacimiento", LocalDate.of(anioNacimiento, 6, 15))
                        .addValue("grupo", grupo),
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

    private int contar(String tabla) {
        return jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private UUID entrenadorDelEntrenamiento(UUID categoria) {
        return jdbc.getJdbcOperations().queryForObject(
                "SELECT id_entrenador FROM entrenamiento WHERE id_categoria = ?", (fila, n) -> fila.getObject(1, UUID.class),
                categoria);
    }

    /** Quién pasó lista en cada fila, en el orden de la lista (apellido y nombre). */
    private List<UUID> entrenadoresDeAsistencia() {
        return jdbc.getJdbcOperations().query("""
                SELECT a.id_entrenador FROM asistencia a JOIN deportista d ON d.id_deportista = a.id_deportista
                 ORDER BY d.apellido, d.nombre""", (fila, n) -> fila.getObject(1, UUID.class));
    }

    private Instant registradoEn(UUID deportista) {
        return jdbc.getJdbcOperations().queryForObject("SELECT registrado_en FROM asistencia WHERE id_deportista = ?",
                (fila, n) -> fila.getTimestamp(1).toInstant(), deportista);
    }
}
