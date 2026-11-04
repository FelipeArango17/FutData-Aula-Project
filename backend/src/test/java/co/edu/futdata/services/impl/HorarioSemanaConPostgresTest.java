package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.edu.futdata.api.dto.AccionSemanaResponseDTO;
import co.edu.futdata.api.dto.CancelacionRequestDTO;
import co.edu.futdata.api.dto.ExtraRequestDTO;
import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import co.edu.futdata.api.dto.SesionSemanaResponseDTO;
import co.edu.futdata.persistence.dao.FranjaYaCanceladaException;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.persistence.impl.CategoriaDAOImpl;
import co.edu.futdata.persistence.impl.DestinatariosDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.EntrenadorDAOImpl;
import co.edu.futdata.persistence.impl.ExcepcionHorarioDAOImpl;
import co.edu.futdata.persistence.impl.FranjaHorarioDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.SinPermisoHorarioException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancelar y deshacer de punta a punta con los DAO y el servicio reales sobre PostgreSQL: cancelar, ver la semana con la
 * franja CANCELADA, deshacer y verla otra vez ACTIVA. La propiedad se decide en el SQL, así que aquí se prueba de verdad.
 */
@ExtendWith(DockerDisponible.class)
class HorarioSemanaConPostgresTest {

    /** Miércoles 4 de noviembre de 2026: la semana en curso va del 2 al 7. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final String MARTES = "2026-11-10";

    private NamedParameterJdbcTemplate jdbc;
    private HorarioSemanaServiceImpl servicio;
    private UUID luis;
    private UUID ana;
    private Solicitante admin;
    private Solicitante entrenadorLuis;
    private Solicitante entrenadorAna;
    private Solicitante acudiente;
    private UUID sub12;
    private UUID semillero;
    /** Martes 16:00-17:30, Sub-12, lo dirige Luis. */
    private UUID franjaSub12;
    /** Martes 16:00-17:30, Semillero (de Ana), "por turnos". */
    private UUID franjaPorTurnos;
    private DataSourceTransactionManager administrador;
    private INotificador notificador;

    private HorarioSemanaServiceImpl servicioCon(ExcepcionHorarioDAOImpl excepciones) {
        return new HorarioSemanaServiceImpl(new FranjaHorarioDAOImpl(jdbc), excepciones, new EntrenadorDAOImpl(jdbc),
                new CategoriaDAOImpl(jdbc), RELOJ, new TransactionTemplate(administrador), new DestinatariosDAOImpl(jdbc),
                notificador);
    }

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        administrador = new DataSourceTransactionManager(PostgresDePrueba.dataSource());
        notificador = mock(INotificador.class);
        servicio = servicioCon(new ExcepcionHorarioDAOImpl(jdbc));
        luis = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        ana = PostgresDePrueba.entrenador("Ana", "Díaz", "1000000001", "ana@pradofc.co");
        UUID usuarioAdmin = cuenta("semana-pg-admin@pradofc.co");
        UUID usuarioLuis = cuenta("semana-pg-luis@pradofc.co");
        UUID usuarioAna = cuenta("semana-pg-ana@pradofc.co");
        vincular(luis, usuarioLuis);
        vincular(ana, usuarioAna);
        admin = new Solicitante(usuarioAdmin, Solicitante.ADMINISTRADOR);
        entrenadorLuis = new Solicitante(usuarioLuis, Solicitante.ENTRENADOR);
        entrenadorAna = new Solicitante(usuarioAna, Solicitante.ENTRENADOR);
        acudiente = new Solicitante(cuenta("semana-pg-acudiente@pradofc.co"), Solicitante.ACUDIENTE);
        sub12 = PostgresDePrueba.categoria("Sub-12", luis);
        semillero = PostgresDePrueba.categoria("Semillero", ana);
        franjaSub12 = franja(sub12, 2, "16:00", "17:30", luis);
        franjaPorTurnos = franja(semillero, 2, "16:00", "17:30", null);
    }

    @AfterEach
    void limpiarTodo() {
        for (String tabla : java.util.List.of("auditoria", "notificacion_pago", "pago", "acudiente_deportista", "acudiente", "documento", "deportista")) {
            jdbc.getJdbcOperations().update("DELETE FROM " + tabla);
        }
        jdbc.getJdbcOperations().update("DELETE FROM excepcion_horario");
        jdbc.getJdbcOperations().update("DELETE FROM franja_horario");
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'semana-pg-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
        PostgresDePrueba.limpiar();
    }

    private SesionSemanaResponseDTO sesionDe(HorarioSemanaResponseDTO semana, UUID idFranja) {
        return semana.sesiones().stream().filter(s -> idFranja.equals(s.idFranja())).findFirst().orElseThrow();
    }

    private static CancelacionRequestDTO pedir(UUID franja, String fecha, String motivo) {
        return new CancelacionRequestDTO(fecha, franja.toString(), motivo);
    }

    @Test
    void cancelarVerLaSemanaCanceladaYDeshacer() {
        String lunesSiguiente = "2026-11-09";
        assertThat(sesionDe(servicio.consultar(lunesSiguiente, admin), franjaSub12).estado()).isEqualTo("ACTIVO");

        // 1. Cancelar
        AccionSemanaResponseDTO cancelada = servicio.cancelar(pedir(franjaSub12, MARTES, "  Cancha   mojada "), admin);

        assertThat(cancelada.sesion().estado()).isEqualTo("CANCELADO");
        assertThat(cancelada.sesion().idExcepcion()).isNotNull();
        assertThat(cancelada.sesion().motivo()).isEqualTo("Cancha mojada");
        assertThat(cancelada.sesion().categoria()).isEqualTo("Sub-12");
        assertThat(cancelada.sesion().entrenador()).isEqualTo("Luis Mora");
        assertThat(cancelada.avisoEnviado()).isNull();
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM excepcion_horario", Integer.class)).isOne();
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT creado_por FROM excepcion_horario", UUID.class))
                .isEqualTo(admin.idUsuario());

        // 2. La semana la muestra CANCELADA con su idExcepcion; la otra franja sigue ACTIVA
        HorarioSemanaResponseDTO semana = servicio.consultar(lunesSiguiente, admin);
        SesionSemanaResponseDTO vista = sesionDe(semana, franjaSub12);
        assertThat(vista.estado()).isEqualTo("CANCELADO");
        assertThat(vista.tipo()).isEqualTo("FRANJA");
        assertThat(vista.idExcepcion()).isEqualTo(cancelada.sesion().idExcepcion());
        assertThat(vista.motivo()).isEqualTo("Cancha mojada");
        assertThat(vista.fecha()).isEqualTo(MARTES);
        assertThat(sesionDe(semana, franjaPorTurnos).estado()).isEqualTo("ACTIVO");
        // ...y la semana en curso (otra fecha) no se ve afectada
        assertThat(sesionDe(servicio.consultar(null, admin), franjaSub12).estado()).isEqualTo("ACTIVO");

        // 3. Una segunda cancelación el mismo día es un conflicto y no deja nada nuevo
        assertThatThrownBy(() -> servicio.cancelar(pedir(franjaSub12, MARTES, null), admin))
                .isInstanceOf(FranjaYaCanceladaException.class);
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM excepcion_horario", Integer.class)).isOne();

        // 4. Deshacer: la franja vuelve a estar ACTIVA y la fila desaparece
        AccionSemanaResponseDTO deshecha = servicio.deshacerCancelacion(cancelada.sesion().idExcepcion(), admin);

        assertThat(deshecha.sesion().estado()).isEqualTo("ACTIVO");
        assertThat(deshecha.sesion().idExcepcion()).isNull();
        assertThat(deshecha.sesion().fecha()).isEqualTo(MARTES);
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM excepcion_horario", Integer.class)).isZero();
        SesionSemanaResponseDTO denuevo = sesionDe(servicio.consultar(lunesSiguiente, admin), franjaSub12);
        assertThat(denuevo.estado()).isEqualTo("ACTIVO");
        assertThat(denuevo.idExcepcion()).isNull();
        assertThat(denuevo.motivo()).isNull();

        // 5. Y se puede volver a cancelar
        assertThat(servicio.cancelar(pedir(franjaSub12, MARTES, null), admin).sesion().estado()).isEqualTo("CANCELADO");
    }

    @Test
    void laPropiedadSeDecideEnSqlEntrenadorDuenoAjenoYPorTurnos() {
        // Luis, dueño de Sub-12 y quien dirige la franja
        assertThat(servicio.cancelar(pedir(franjaSub12, MARTES, null), entrenadorLuis).sesion().estado()).isEqualTo("CANCELADO");

        // Ana no es de Sub-12; Luis no es de Semillero y la franja es por turnos
        assertThatThrownBy(() -> servicio.cancelar(pedir(franjaSub12, "2026-11-17", null), entrenadorAna))
                .isInstanceOf(SinPermisoHorarioException.class);
        assertThatThrownBy(() -> servicio.cancelar(pedir(franjaPorTurnos, MARTES, null), entrenadorLuis))
                .isInstanceOf(SinPermisoHorarioException.class);
        // La dueña de Semillero sí cancela su franja por turnos
        assertThat(servicio.cancelar(pedir(franjaPorTurnos, MARTES, "Torneo"), entrenadorAna).sesion().estado())
                .isEqualTo("CANCELADO");
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM excepcion_horario", Integer.class)).isEqualTo(2);

        // Deshacer: la cancelación de Sub-12 no es de Ana; la de Semillero no es de Luis
        UUID deSub12 = idCancelacionDe(franjaSub12);
        UUID deSemillero = idCancelacionDe(franjaPorTurnos);
        assertThatThrownBy(() -> servicio.deshacerCancelacion(deSub12, entrenadorAna)).isInstanceOf(SinPermisoHorarioException.class);
        assertThatThrownBy(() -> servicio.deshacerCancelacion(deSemillero, entrenadorLuis)).isInstanceOf(SinPermisoHorarioException.class);
        assertThat(servicio.deshacerCancelacion(deSub12, entrenadorLuis).sesion().estado()).isEqualTo("ACTIVO");
        assertThat(servicio.deshacerCancelacion(deSemillero, admin).sesion().estado()).isEqualTo("ACTIVO");
    }

    @Test
    void puedeGestionarSeCalculaConLasCategoriasYLasFranjasDelEntrenador() {
        // Una franja de Semillero (de Ana) que dirige Luis
        UUID cubre = franja(semillero, 3, "16:00", "17:00", luis);

        // La semana siguiente (10 y 11 de noviembre): ninguna de sus sesiones es de una fecha pasada
        String semana = "2026-11-09";
        HorarioSemanaResponseDTO deLuis = servicio.consultar(semana, entrenadorLuis);
        assertThat(sesionDe(deLuis, franjaSub12).puedeGestionar()).isTrue();
        assertThat(sesionDe(deLuis, cubre).puedeGestionar()).as("la dirige él aunque la categoría sea de Ana").isTrue();
        assertThat(sesionDe(deLuis, franjaPorTurnos).puedeGestionar()).isFalse();

        HorarioSemanaResponseDTO deAna = servicio.consultar(semana, entrenadorAna);
        assertThat(sesionDe(deAna, franjaPorTurnos).puedeGestionar()).isTrue();
        assertThat(sesionDe(deAna, cubre).puedeGestionar()).isTrue();
        assertThat(sesionDe(deAna, franjaSub12).puedeGestionar()).isFalse();

        assertThat(servicio.consultar(semana, admin).sesiones()).allMatch(SesionSemanaResponseDTO::puedeGestionar);
        assertThat(servicio.consultar(null, acudiente).sesiones()).noneMatch(SesionSemanaResponseDTO::puedeGestionar);
        // En la semana en curso (miércoles 4), las franjas del martes ya pasaron: nadie las gestiona, ni el administrador
        assertThat(servicio.consultar(null, admin).sesiones()).filteredOn(x -> x.fecha().equals("2026-11-03"))
                .isNotEmpty().noneMatch(SesionSemanaResponseDTO::puedeGestionar);
    }

    @Test
    void lasValidacionesNoDejanNadaEnLaBase() {
        UUID inexistente = UUID.randomUUID();
        assertThatThrownBy(() -> servicio.cancelar(pedir(inexistente, MARTES, null), admin))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.cancelar(pedir(franjaSub12, "2026-11-03", null), admin)) // martes ya pasado
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.cancelar(pedir(franjaSub12, "2026-11-11", null), admin)) // miércoles
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.deshacerCancelacion(UUID.randomUUID(), admin))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM excepcion_horario", Integer.class)).isZero();
    }

    @Test
    void siSeCambiaElDiaDeLaFranjaLaCancelacionViejaYaNoSeAplica() {
        servicio.cancelar(pedir(franjaSub12, MARTES, "Vieja"), admin);
        // El 4A pasa la franja a los miércoles: la cancelación queda en un martes que ya no es de la franja
        jdbc.update("UPDATE franja_horario SET dia = 3 WHERE id_franja = :id", new MapSqlParameterSource("id", franjaSub12));

        SesionSemanaResponseDTO s = sesionDe(servicio.consultar("2026-11-09", admin), franjaSub12);

        assertThat(s.fecha()).isEqualTo("2026-11-11");
        assertThat(s.estado()).isEqualTo("ACTIVO");
        assertThat(s.idExcepcion()).isNull();
    }

    // --- Datos ----------------------------------------------------------------------------------------------

    private UUID idCancelacionDe(UUID idFranja) {
        return jdbc.queryForObject("SELECT id_excepcion FROM excepcion_horario WHERE id_franja = :f",
                new MapSqlParameterSource("f", idFranja), UUID.class);
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

    // --- Extras ---------------------------------------------------------------------------------------------

    private static ExtraRequestDTO extra(UUID categoria, String fecha, String inicio, String fin, UUID entrenador) {
        return new ExtraRequestDTO(categoria.toString(), fecha, inicio, fin, "Horizontes",
                entrenador == null ? null : entrenador.toString(), null, null);
    }

    private long extras() {
        return jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM excepcion_horario WHERE tipo = 'EXTRA'", Long.class);
    }

    @Test
    void crearUnExtraVerloEnLaSemanaChocarConOtroExtraYBorrarlo() {
        String semanaSiguiente = "2026-11-09";

        // 1. Crear
        AccionSemanaResponseDTO creado = servicio.crearExtra(new ExtraRequestDTO(sub12.toString(), "2026-11-11", "18:00", "19:30",
                "UVA El Paraíso", luis.toString(), null, "  Partido  amistoso "), admin);

        SesionSemanaResponseDTO s = creado.sesion();
        assertThat(s.tipo()).isEqualTo("EXTRA");
        assertThat(s.estado()).isEqualTo("ACTIVO");
        assertThat(s.idFranja()).isNull();
        assertThat(s.idExcepcion()).isNotNull();
        assertThat(s.dia()).isEqualTo(3);
        assertThat(s.categoria()).isEqualTo("Sub-12");
        assertThat(s.sede()).isEqualTo("UVA El Paraíso");
        assertThat(s.entrenador()).isEqualTo("Luis Mora");
        assertThat(s.motivo()).isEqualTo("Partido amistoso");
        assertThat(s.puedeGestionar()).isTrue();
        assertThat(creado.avisoEnviado()).isNull();
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT creado_por FROM excepcion_horario", UUID.class))
                .isEqualTo(admin.idUsuario());

        // 2. Aparece en la semana, entre las sesiones, y no en la anterior
        HorarioSemanaResponseDTO semana = servicio.consultar(semanaSiguiente, admin);
        assertThat(semana.sesiones()).filteredOn(x -> x.tipo().equals("EXTRA")).singleElement().satisfies(x -> {
            assertThat(x.idExcepcion()).isEqualTo(s.idExcepcion());
            assertThat(x.fecha()).isEqualTo("2026-11-11");
            assertThat(x.horaInicio()).isEqualTo("18:00");
        });
        assertThat(servicio.consultar(null, admin).sesiones()).noneMatch(x -> x.tipo().equals("EXTRA"));

        // 3. Choca con otro extra del mismo entrenador (se solapa) y no deja nada nuevo
        assertThatThrownBy(() -> servicio.crearExtra(extra(semillero, "2026-11-11", "19:00", "20:00", luis), admin))
                .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCampos()).containsExactly("idEntrenador"))
                .hasMessage("Ese entrenador ya tiene otra franja que se cruza con este horario");
        assertThat(extras()).isOne();
        // contiguo y por turnos, sí
        assertThat(servicio.crearExtra(extra(semillero, "2026-11-11", "19:30", "20:30", luis), admin).sesion()).isNotNull();
        assertThat(extras()).isEqualTo(2);

        // 4. Borrar: desaparece
        AccionSemanaResponseDTO borrado = servicio.eliminarExtra(s.idExcepcion(), admin);
        assertThat(borrado.sesion()).isNull();
        assertThat(borrado.avisoEnviado()).isNull();
        assertThat(servicio.consultar(semanaSiguiente, admin).sesiones())
                .noneMatch(x -> s.idExcepcion().equals(x.idExcepcion()));
        assertThat(extras()).isOne();
        // ya no existe: 404, y una cancelación tampoco es un extra
        assertThatThrownBy(() -> servicio.eliminarExtra(s.idExcepcion(), admin)).isInstanceOf(RecursoNoEncontradoException.class);
        UUID cancelacion = servicio.cancelar(pedir(franjaSub12, MARTES, null), admin).sesion().idExcepcion();
        assertThatThrownBy(() -> servicio.eliminarExtra(cancelacion, admin)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unExtraQueSeCruzaConUnaFranjaNoCanceladaEs409PeroSiLaFranjaEstaCanceladaEseDiaSePermite() {
        // Luis tiene la franja del martes 16:00-17:30
        assertThatThrownBy(() -> servicio.crearExtra(extra(sub12, MARTES, "17:00", "18:00", luis), admin))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("Ese entrenador ya tiene otra franja que se cruza con este horario");
        assertThat(extras()).isZero();
        // tocarse no es cruzarse
        assertThat(servicio.crearExtra(extra(sub12, MARTES, "17:30", "18:30", luis), admin).sesion()).isNotNull();

        // Se cancela la franja ese martes: ya no estorba
        servicio.cancelar(pedir(franjaSub12, MARTES, "Lluvia"), admin);
        assertThat(servicio.crearExtra(extra(sub12, MARTES, "16:30", "17:00", luis), admin).sesion().estado()).isEqualTo("ACTIVO");
        // El martes siguiente la franja sigue ahí
        assertThatThrownBy(() -> servicio.crearExtra(extra(sub12, "2026-11-17", "16:30", "17:00", luis), admin))
                .isInstanceOf(ConflictoException.class);
        // Si se deshace la cancelación el extra queda dentro del horario de la franja (la base no lo impide: las franjas
        // no se comparan con los extras al deshacer), pero se ve en la semana
        assertThat(extras()).isEqualTo(2);
    }

    @Test
    void dosExtrasPorTurnosPuedenCoincidirYUnoSinEntrenadorNoChocaConLasFranjas() {
        assertThat(servicio.crearExtra(extra(semillero, MARTES, "16:00", "17:30", null), admin).sesion()).isNotNull();
        assertThat(servicio.crearExtra(extra(semillero, MARTES, "16:30", "17:00", null), admin).sesion()).isNotNull();
        assertThat(extras()).isEqualTo(2);
    }

    @Test
    void elExtraEsDeLaCategoriaLaPropiedadSeDecideEnSqlYDirigirloNoDaPermiso() {
        // Ana (Semillero) lo crea dirigido por Luis; Luis no es dueño de Semillero
        AccionSemanaResponseDTO deAna = servicio.crearExtra(extra(semillero, MARTES, "18:00", "19:00", luis), entrenadorAna);
        UUID idExtra = deAna.sesion().idExcepcion();
        assertThat(deAna.sesion().puedeGestionar()).isTrue();

        assertThatThrownBy(() -> servicio.crearExtra(extra(sub12, "2026-11-11", "18:00", "19:00", null), entrenadorAna))
                .isInstanceOf(SinPermisoHorarioException.class);
        assertThatThrownBy(() -> servicio.crearExtra(extra(semillero, "2026-11-11", "18:00", "19:00", null), entrenadorLuis))
                .isInstanceOf(SinPermisoHorarioException.class);
        assertThatThrownBy(() -> servicio.eliminarExtra(idExtra, entrenadorLuis)).isInstanceOf(SinPermisoHorarioException.class);
        assertThat(extras()).isOne();

        // En la semana, el extra lo ve gestionable Ana, no Luis (aunque lo dirige), y sí el administrador
        String semana = "2026-11-09";
        assertThat(servicio.consultar(semana, entrenadorAna).sesiones()).filteredOn(x -> x.tipo().equals("EXTRA"))
                .singleElement().satisfies(x -> assertThat(x.puedeGestionar()).isTrue());
        assertThat(servicio.consultar(semana, entrenadorLuis).sesiones()).filteredOn(x -> x.tipo().equals("EXTRA"))
                .singleElement().satisfies(x -> assertThat(x.puedeGestionar()).isFalse());

        assertThat(servicio.eliminarExtra(idExtra, entrenadorAna).sesion()).isNull();
        assertThat(extras()).isZero();
    }

    @Test
    void unEntrenadorInactivoNoSePuedeAsignarYUnoDesactivadoDespuesSaleConEntrenadorActivoFalso() {
        UUID inactivo = PostgresDePrueba.entrenador("Pedro", "Inactivo", "1000000009", "pedro@pradofc.co");
        jdbc.update("UPDATE entrenador SET activo = false WHERE id_entrenador = :e", new MapSqlParameterSource("e", inactivo));
        assertThatThrownBy(() -> servicio.crearExtra(extra(sub12, MARTES, "18:00", "19:00", inactivo), admin))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("idEntrenador"));
        assertThatThrownBy(() -> servicio.crearExtra(extra(sub12, MARTES, "18:00", "19:00", UUID.randomUUID()), admin))
                .isInstanceOf(DatosInvalidosException.class);
        assertThat(extras()).isZero();

        servicio.crearExtra(extra(sub12, MARTES, "18:00", "19:00", ana), admin);
        jdbc.update("UPDATE entrenador SET activo = false WHERE id_entrenador = :e", new MapSqlParameterSource("e", ana));

        assertThat(servicio.consultar("2026-11-09", admin).sesiones()).filteredOn(x -> x.tipo().equals("EXTRA"))
                .singleElement().satisfies(x -> {
                    assertThat(x.entrenador()).isEqualTo("Ana Díaz");
                    assertThat(x.entrenadorActivo()).isFalse();
                });
    }

    /** Anota en qué transacción (la conexión que Spring ató al hilo) corre cada paso. */
    private static final class DaoQueAnotaLaTransaccion extends ExcepcionHorarioDAOImpl {
        final java.util.List<String> pasos = new java.util.ArrayList<>();
        final java.util.List<Object> conexiones = new java.util.ArrayList<>();

        DaoQueAnotaLaTransaccion(NamedParameterJdbcTemplate jdbc) {
            super(jdbc);
        }

        private void anotar(String paso) {
            pasos.add(paso + ":" + TransactionSynchronizationManager.isActualTransactionActive());
            conexiones.add(TransactionSynchronizationManager.getResource(PostgresDePrueba.dataSource()));
        }

        @Override
        public boolean hayFranjaQueSeCruza(UUID idEntrenador, java.time.LocalDate fecha, LocalTime inicio, LocalTime fin) {
            anotar("bloqueo");
            return super.hayFranjaQueSeCruza(idEntrenador, fecha, inicio, fin);
        }

        @Override
        public UUID crearExtra(co.edu.futdata.domain.entities.ExcepcionHorario extra) {
            anotar("insert");
            return super.crearExtra(extra);
        }
    }

    @Test
    void elBloqueoDelEntrenadorYElInsertCorrenEnLaMismaTransaccion() {
        DaoQueAnotaLaTransaccion dao = new DaoQueAnotaLaTransaccion(jdbc);

        servicioCon(dao).crearExtra(extra(sub12, "2026-11-11", "18:00", "19:00", luis), admin);

        assertThat(dao.pasos).containsExactly("bloqueo:true", "insert:true");
        assertThat(dao.conexiones).doesNotContainNull();
        assertThat(dao.conexiones.get(0)).as("la misma conexión, es decir la misma transacción").isSameAs(dao.conexiones.get(1));
        assertThat(extras()).isOne();
        // Y fuera del servicio no queda ninguna transacción abierta
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void elBloqueoDeLaFilaDelEntrenadorSerializaAQuienCreaExtrasDelMismoEntrenador() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        try {
            // A: una transacción que bloquea la fila de Luis y la mantiene abierta
            Future<?> a = hilos.submit(() -> new TransactionTemplate(administrador).executeWithoutResult(estado -> {
                new ExcepcionHorarioDAOImpl(jdbc).hayFranjaQueSeCruza(luis, java.time.LocalDate.parse("2026-11-11"),
                        LocalTime.of(18, 0), LocalTime.of(19, 0));
                bloqueado.countDown();
                try {
                    soltar.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();

            // B: crear un extra de Luis tiene que esperar a que A termine
            Future<AccionSemanaResponseDTO> b = hilos.submit(
                    () -> servicio.crearExtra(extra(sub12, "2026-11-11", "18:30", "19:30", luis), admin));
            Thread.sleep(1500);
            assertThat(b.isDone()).as("B espera el bloqueo de la fila del entrenador").isFalse();
            assertThat(extras()).isZero();

            soltar.countDown();
            a.get(10, TimeUnit.SECONDS);
            assertThat(b.get(15, TimeUnit.SECONDS).sesion()).isNotNull();
            assertThat(extras()).isOne();
        } finally {
            soltar.countDown();
            hilos.shutdownNow();
        }
    }

    // --- Avisos a los acudientes ----------------------------------------------------------------------------

    private int contador;

    private UUID acudiente(String correo, String nombre, String apellido) {
        return jdbc.queryForObject("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo)
                VALUES (:n, :a, 'CC', :doc, '1985-05-05', :correo) RETURNING id_acudiente""",
                new MapSqlParameterSource("n", nombre).addValue("a", apellido).addValue("correo", correo)
                        .addValue("doc", "9" + (++contador) + System.nanoTime() % 100000), UUID.class);
    }

    private void hijo(UUID idAcudiente, String nacimiento, boolean activo) {
        UUID deportista = jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, activo)
                VALUES ('Hijo', 'Prueba', 'TI', :doc, CAST(:nac AS date), :activo) RETURNING id_deportista""",
                new MapSqlParameterSource("nac", nacimiento).addValue("activo", activo)
                        .addValue("doc", "10" + (++contador) + System.nanoTime() % 100000), UUID.class);
        jdbc.update("INSERT INTO acudiente_deportista (id_acudiente, id_deportista) VALUES (:a, :d)",
                new MapSqlParameterSource("a", idAcudiente).addValue("d", deportista));
    }

    @SuppressWarnings("unchecked")
    @Test
    void elAvisoLlegaSoloALosAcudientesDeLaCategoriaDeLaSesionConElPayloadDelContrato() {
        hijo(acudiente("maria@correo.co", "María", "Torres"), "2014-03-03", true);      // Sub-12 en 2026
        hijo(acudiente("pedro@correo.co", "Pedro", "Alvarez"), "2014-07-07", true);     // Sub-12, otro acudiente
        hijo(acudiente("semillero@correo.co", "Sara", "Gómez"), "2016-01-01", true);    // Semillero
        hijo(acudiente("inactivo@correo.co", "Iván", "Retirado"), "2014-01-01", false); // Sub-12 pero inactivo
        hijo(acudiente("sincorreo@correo.co", "Sin", "Hijos"), "2005-01-01", true);     // fuera de rango

        AccionSemanaResponseDTO respuesta = servicio.cancelar(pedir(franjaSub12, MARTES, "Cancha mojada"), admin);

        assertThat(respuesta.avisoEnviado()).isTrue();
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> captura = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(notificador).notificar(org.mockito.ArgumentMatchers.eq("CAMBIO_ENTRENAMIENTO"), captura.capture());
        java.util.Map<String, Object> datos = captura.getValue();
        assertThat(datos.get("tipoCambio")).isEqualTo("CANCELADO");
        assertThat(datos.get("motivo")).isEqualTo("Cancha mojada");
        java.util.Map<String, Object> entrenamiento = (java.util.Map<String, Object>) datos.get("entrenamiento");
        assertThat(entrenamiento).containsExactly(
                java.util.Map.entry("categoria", "Sub-12"), java.util.Map.entry("fecha", MARTES),
                java.util.Map.entry("horaInicio", "16:00"), java.util.Map.entry("lugar", "Horizontes"),
                java.util.Map.entry("entrenador", "Luis Mora"));
        assertThat((java.util.List<java.util.Map<String, Object>>) datos.get("destinatarios"))
                .extracting(d -> d.get("correo"), d -> d.get("nombre"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("pedro@correo.co", "Pedro Alvarez"),
                        org.assertj.core.groups.Tuple.tuple("maria@correo.co", "María Torres"));
    }

    @Test
    void sinAcudientesNoSeLlamaANotificarYElAvisoEsNull() {
        AccionSemanaResponseDTO respuesta = servicio.cancelar(pedir(franjaSub12, MARTES, null), admin);

        assertThat(respuesta.avisoEnviado()).isNull();
        assertThat(respuesta.sesion().estado()).isEqualTo("CANCELADO");
        verify(notificador, never()).notificar(any(), any());
    }

    @Test
    void siElNotificadorLanzaCadaOperacionQuedaPersistidaYLaRespuestaEsCorrecta() {
        hijo(acudiente("maria@correo.co", "María", "Torres"), "2014-03-03", true);
        hijo(acudiente("ana@correo.co", "Ana", "Pérez"), "2016-03-03", true);
        doThrow(new NotificacionNoEnviadaException("tiempo agotado", "CAMBIO_ENTRENAMIENTO", null, null))
                .when(notificador).notificar(any(), any());

        // Cancelar una franja
        AccionSemanaResponseDTO cancelada = servicio.cancelar(pedir(franjaSub12, MARTES, "Lluvia"), admin);
        assertThat(cancelada.avisoEnviado()).isFalse();
        assertThat(cancelada.sesion().estado()).isEqualTo("CANCELADO");
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT count(*) FROM excepcion_horario WHERE tipo = 'CANCELACION'", Long.class)).isOne();
        assertThat(sesionDe(servicio.consultar("2026-11-09", admin), franjaSub12).estado()).isEqualTo("CANCELADO");

        // Crear un extra
        AccionSemanaResponseDTO creado = servicio.crearExtra(extra(sub12, "2026-11-11", "18:00", "19:00", luis), admin);
        assertThat(creado.avisoEnviado()).isFalse();
        assertThat(creado.sesion().tipo()).isEqualTo("EXTRA");
        assertThat(extras()).isOne();

        // Eliminar el extra
        AccionSemanaResponseDTO eliminado = servicio.eliminarExtra(creado.sesion().idExcepcion(), admin);
        assertThat(eliminado.avisoEnviado()).isFalse();
        assertThat(eliminado.sesion()).isNull();
        assertThat(extras()).isZero();

        // Deshacer la cancelación
        AccionSemanaResponseDTO deshecha = servicio.deshacerCancelacion(cancelada.sesion().idExcepcion(), admin);
        assertThat(deshecha.avisoEnviado()).isFalse();
        assertThat(deshecha.sesion().estado()).isEqualTo("ACTIVO");
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM excepcion_horario", Long.class)).isZero();
    }

    @Test
    void unaOperacionQueFallaNoAvisaANadie() {
        hijo(acudiente("maria@correo.co", "María", "Torres"), "2014-03-03", true);
        servicio.cancelar(pedir(franjaSub12, MARTES, null), admin);
        org.mockito.Mockito.reset(notificador);

        assertThatThrownBy(() -> servicio.cancelar(pedir(franjaSub12, MARTES, null), admin))
                .isInstanceOf(FranjaYaCanceladaException.class);
        assertThatThrownBy(() -> servicio.crearExtra(extra(sub12, "2026-11-17", "17:00", "18:00", luis), admin))
                .isInstanceOf(ConflictoException.class);

        verify(notificador, never()).notificar(any(), any());
    }
}
