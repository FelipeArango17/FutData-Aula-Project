package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.AsistenciaRequestDTO;
import co.edu.futdata.api.dto.RegistroAsistenciaRequestDTO;
import co.edu.futdata.api.dto.SesionAsistenciaResponseDTO;
import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.domain.rules.ReglasAsistencia;
import co.edu.futdata.domain.rules.ReglasExcepcion;
import co.edu.futdata.persistence.dao.DeportistaDeSesion;
import co.edu.futdata.persistence.dao.IAsistenciaDAO;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IExcepcionHorarioDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.persistence.dao.RegistroAsistencia;
import co.edu.futdata.persistence.dao.SolapeDeEntrenamientoException;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.SinPermisoAsistenciaException;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/** {@link AsistenciaServiceImpl} con los DAO simulados. El recorrido contra PostgreSQL está en {@code AsistenciaConPostgresTest}. */
class AsistenciaServiceImplTest {

    /** Miércoles 4 de noviembre de 2026 (10:00 en Bogotá). */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final String MARTES_PASADO = "2026-11-03";
    private static final String MARTES_FUTURO = "2026-11-10";
    private static final String HOY = "2026-11-04";

    private static final UUID SUB12 = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID SUB13 = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6d");
    private static final UUID SEMILLERO = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6c");
    private static final UUID LUIS = UUID.fromString("e0000000-0000-0000-0000-00000000000a");
    private static final UUID ANA = UUID.fromString("e0000000-0000-0000-0000-00000000000b");
    private static final UUID USUARIO_LUIS = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID USUARIO_SIN_FICHA = UUID.fromString("00000000-0000-0000-0000-0000000000ee");
    private static final UUID ENTRENAMIENTO = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID NINO_1 = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID NINO_2 = UUID.fromString("d0000000-0000-0000-0000-000000000002");

    private static final Solicitante ADMIN = new Solicitante(UUID.randomUUID(), Solicitante.ADMINISTRADOR);
    private static final Solicitante LUIS_ENTRENADOR = new Solicitante(USUARIO_LUIS, Solicitante.ENTRENADOR);
    private static final Solicitante SIN_FICHA = new Solicitante(USUARIO_SIN_FICHA, Solicitante.ENTRENADOR);
    private static final Solicitante TESORERO = new Solicitante(UUID.randomUUID(), "TESORERO");
    private static final Solicitante ACUDIENTE = new Solicitante(UUID.randomUUID(), Solicitante.ACUDIENTE);

    private IAsistenciaDAO asistencias;
    private IFranjaHorarioDAO franjas;
    private IExcepcionHorarioDAO excepciones;
    private IEntrenadorDAO entrenadores;
    private ICategoriaDAO categorias;
    private TransactionOperations transaccion;
    private AsistenciaServiceImpl servicio;

    private FranjaHorario sub12Luis;        // martes, Sub-12, la dirige Luis (dueño de Sub-12)
    private FranjaHorario semilleroTurnos;  // martes, Semillero, "por turnos"
    private FranjaHorario semilleroTarde;   // martes, Semillero grupo tarde
    private FranjaHorario sub13Ana;         // martes, Sub-13 (de Ana), la dirige Ana
    private FranjaHorario sub13DirigeLuis;  // martes, Sub-13 (de Ana), la dirige Luis
    private FranjaHorario sub12Miercoles;   // miércoles (hoy), Sub-12, la dirige Luis
    private ExcepcionHorario extraSub12;    // extra Sub-12 el martes pasado
    private ExcepcionHorario extraSub13;    // extra Sub-13 el martes pasado, lo dirige Luis

    @BeforeEach
    void preparar() {
        asistencias = mock(IAsistenciaDAO.class);
        franjas = mock(IFranjaHorarioDAO.class);
        excepciones = mock(IExcepcionHorarioDAO.class);
        entrenadores = mock(IEntrenadorDAO.class);
        categorias = mock(ICategoriaDAO.class);
        transaccion = spy(TransactionOperations.withoutTransaction());
        servicio = new AsistenciaServiceImpl(asistencias, franjas, excepciones, entrenadores, categorias, RELOJ, transaccion);

        sub12Luis = franja("Sub-12", SUB12, 2, "16:00", "17:30", LUIS, "Luis Pérez", null);
        semilleroTurnos = franja("Semillero", SEMILLERO, 2, "16:00", "17:30", null, null, null);
        semilleroTarde = franja("Semillero", SEMILLERO, 2, "16:00", "17:30", null, null, "tarde");
        sub13Ana = franja("Sub-13", SUB13, 2, "18:00", "19:00", ANA, "Ana Díaz", null);
        sub13DirigeLuis = franja("Sub-13", SUB13, 2, "19:00", "20:00", LUIS, "Luis Pérez", null);
        sub12Miercoles = franja("Sub-12", SUB12, 3, "16:00", "17:30", LUIS, "Luis Pérez", null);
        for (FranjaHorario f : List.of(sub12Luis, semilleroTurnos, semilleroTarde, sub13Ana, sub13DirigeLuis, sub12Miercoles)) {
            when(franjas.buscarPorId(f.getId())).thenReturn(Optional.of(f));
        }
        extraSub12 = extra("Sub-12", SUB12, "2026-11-03", "18:00", "19:00", LUIS, "Luis Pérez", null);
        extraSub13 = extra("Sub-13", SUB13, "2026-11-03", "18:00", "19:00", LUIS, "Luis Pérez", null);
        for (ExcepcionHorario x : List.of(extraSub12, extraSub13)) {
            when(excepciones.buscarPorId(x.id())).thenReturn(Optional.of(x));
        }
        Entrenador luis = new Entrenador();
        luis.setId(LUIS);
        when(entrenadores.buscarPorIdUsuario(USUARIO_LUIS)).thenReturn(Optional.of(luis));
        when(entrenadores.buscarPorIdUsuario(USUARIO_SIN_FICHA)).thenReturn(Optional.empty());
        when(categorias.listarPorEntrenador(LUIS)).thenReturn(List.of(new Categoria(SUB12, "Sub-12", 12, 12, LUIS)));
        when(asistencias.listarDeportistas(any(), any(), anyInt(), any())).thenReturn(List.of(
                new DeportistaDeSesion(NINO_1, "Ana", "Alfa", null, null),
                new DeportistaDeSesion(NINO_2, "Beto", "Beta", null, null)));
        when(asistencias.buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any())).thenReturn(ENTRENAMIENTO);
    }

    // --- Rol ------------------------------------------------------------------------------------------------

    @Test
    void elTesoreroYElAcudienteReciben403SinTocarNingunDAO() {
        for (Solicitante s : List.of(TESORERO, ACUDIENTE, new Solicitante(UUID.randomUUID(), ""))) {
            assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, sub12Luis.getId().toString(), null, s))
                    .isInstanceOf(SinPermisoException.class);
            assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE")), s))
                    .isInstanceOf(SinPermisoException.class);
        }
        verifyNoInteractions(asistencias, franjas, excepciones, entrenadores);
    }

    @Test
    void sinSolicitanteOSinIdentificarFallaCerrado() {
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, sub12Luis.getId().toString(), null, null))
                .isInstanceOf(SinPermisoException.class);
        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE")),
                new Solicitante(null, Solicitante.ADMINISTRADOR))).isInstanceOf(SinPermisoException.class);
    }

    @Test
    void elRolSeVerificaAntesQueLaFormaDeLaEntrada() {
        assertThatThrownBy(() -> servicio.registrar(new AsistenciaRequestDTO("no es fecha", null, null, null), ACUDIENTE))
                .isInstanceOf(SinPermisoException.class);
    }

    // --- Forma de la entrada --------------------------------------------------------------------------------

    @Test
    void ningunaSesionOLasDosDanDatosInvalidosEnAmbosCampos() {
        String franja = sub12Luis.getId().toString();
        String extra = extraSub12.id().toString();

        for (String[] ids : new String[][] {{null, null}, {franja, extra}, {" ", ""}}) {
            assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, ids[0], ids[1], ADMIN))
                    .isInstanceOfSatisfying(DatosInvalidosException.class,
                            e -> assertThat(e.getCampos()).containsExactly("idExcepcion", "idFranja"));
        }
    }

    @Test
    void laFechaInvalidaOAusenteYElUuidMalFormadoDan400() {
        for (String fecha : new String[] {null, "", "2026-13-40", "03/11/2026", "mañana"}) {
            assertThatThrownBy(() -> servicio.consultar(fecha, sub12Luis.getId().toString(), null, ADMIN))
                    .isInstanceOfSatisfying(DatosInvalidosException.class,
                            e -> assertThat(e.getCampos()).containsExactly("fecha"));
        }
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, "no-es-uuid", null, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsExactly("idFranja"));
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, null, "no-es-uuid", ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsExactly("idExcepcion"));
    }

    @Test
    void losErroresDeFormaVanJuntosYSeIdentificanConCamposRegistros() {
        var cuerpo = new AsistenciaRequestDTO("xx", null, null, List.of(nino(NINO_1, "TARDE")));

        assertThatThrownBy(() -> servicio.registrar(cuerpo, ADMIN)).isInstanceOfSatisfying(DatosInvalidosException.class,
                e -> assertThat(e.getCampos()).containsExactly("fecha", "idExcepcion", "idFranja", "registros"));
    }

    @Test
    void sinRegistrosOVaciosDa400EnRegistros() {
        for (List<RegistroAsistenciaRequestDTO> registros : java.util.Arrays.asList(null, List.<RegistroAsistenciaRequestDTO>of())) {
            assertThatThrownBy(() -> servicio.registrar(new AsistenciaRequestDTO(MARTES_PASADO,
                    sub12Luis.getId().toString(), null, registros), ADMIN))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e -> {
                        assertThat(e.getCampos()).containsExactly("registros");
                        assertThat(e.getMessage()).contains(ReglasAsistencia.SIN_REGISTROS);
                    });
        }
        assertThatThrownBy(() -> servicio.registrar(null, ADMIN)).isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void estadoInvalidoObservacionesLargasIdMalFormadoYRepetidosDan400EnRegistros() {
        List<List<RegistroAsistenciaRequestDTO>> casos = List.of(
                List.of(nino(NINO_1, "TARDE")),
                List.of(nino(NINO_1, null)),
                List.of(new RegistroAsistenciaRequestDTO(NINO_1.toString(), "PRESENTE", "a".repeat(301))),
                List.of(new RegistroAsistenciaRequestDTO("no-uuid", "PRESENTE", null)),
                List.of(new RegistroAsistenciaRequestDTO(null, "PRESENTE", null)),
                List.of(nino(NINO_1, "PRESENTE"), nino(NINO_1, "AUSENTE")),
                java.util.Arrays.asList(nino(NINO_1, "PRESENTE"), null));

        for (var registros : casos) {
            assertThatThrownBy(() -> servicio.registrar(new AsistenciaRequestDTO(MARTES_PASADO,
                    sub12Luis.getId().toString(), null, registros), ADMIN))
                    .isInstanceOfSatisfying(DatosInvalidosException.class,
                            e -> assertThat(e.getCampos()).containsExactly("registros"));
        }
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void trescientosCaracteresDeObservacionesSonValidos() {
        servicio.registrar(pedido(MARTES_PASADO, sub12Luis,
                new RegistroAsistenciaRequestDTO(NINO_1.toString(), "PRESENTE", "a".repeat(300))), ADMIN);

        verify(asistencias).guardar(eq(ENTRENAMIENTO), any(), any());
    }

    @Test
    void laFormaSeValidaAntesQueLaExistenciaYElPermiso() {
        // un entrenador ajeno sobre una franja inexistente con un estado inválido: 400, sin filtrar nada de la sesión
        assertThatThrownBy(() -> servicio.registrar(new AsistenciaRequestDTO(MARTES_PASADO, UUID.randomUUID().toString(),
                null, List.of(nino(NINO_1, "TARDE"))), LUIS_ENTRENADOR)).isInstanceOf(DatosInvalidosException.class);
        verify(franjas, never()).buscarPorId(any());
    }

    // --- Resolver la sesión ---------------------------------------------------------------------------------

    @Test
    void unaFranjaOUnExtraInexistentesDan404() {
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, UUID.randomUUID().toString(), null, ADMIN))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, null, UUID.randomUUID().toString(), ADMIN))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, UUID.randomUUID(), null,
                nino(NINO_1, "PRESENTE")), ADMIN)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unaCancelacionNoEsUnExtra() {
        ExcepcionHorario cancelacion = new ExcepcionHorario(UUID.randomUUID(), ExcepcionHorario.CANCELACION,
                LocalDate.parse(MARTES_PASADO), sub12Luis.getId(), SUB12, "Sub-12", 2, LocalTime.of(16, 0),
                LocalTime.of(17, 30), "Horizontes", LUIS, "Luis Pérez", true, null, null, null);
        when(excepciones.buscarPorId(cancelacion.id())).thenReturn(Optional.of(cancelacion));

        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, null, cancelacion.id().toString(), ADMIN))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unaFechaQueNoCaeEnElDiaDeLaFranjaEs400EnFecha() {
        // 2026-11-04 es miércoles y la franja es del martes
        assertThatThrownBy(() -> servicio.consultar(HOY, sub12Luis.getId().toString(), null, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> {
                    assertThat(e.getCampos()).containsExactly("fecha");
                    assertThat(e.getMessage()).contains(ReglasExcepcion.DIA_NO_COINCIDE);
                });
        assertThatThrownBy(() -> servicio.registrar(pedido(HOY, sub12Luis, nino(NINO_1, "PRESENTE")), ADMIN))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void unaFechaQueNoEsLaDelExtraEs400EnFecha() {
        assertThatThrownBy(() -> servicio.consultar("2026-11-02", null, extraSub12.id().toString(), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> {
                    assertThat(e.getCampos()).containsExactly("fecha");
                    assertThat(e.getMessage()).contains(ReglasAsistencia.FECHA_NO_ES_DEL_EXTRA);
                });
    }

    @Test
    void laExistenciaYElDiaSeResuelvenAntesQueElPermiso() {
        // un entrenador ajeno: franja inexistente -> 404 y día equivocado -> 400, no 403
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, UUID.randomUUID().toString(), null, LUIS_ENTRENADOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.consultar(HOY, sub13Ana.getId().toString(), null, LUIS_ENTRENADOR))
                .isInstanceOf(DatosInvalidosException.class);
    }

    // --- Permisos del entrenador ----------------------------------------------------------------------------

    @Test
    void elEntrenadorDeLaCategoriaPuedeYPasaListaConSuFicha() {
        servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE")), LUIS_ENTRENADOR);

        verify(asistencias).guardar(eq(ENTRENAMIENTO), eq(LUIS), any());
    }

    @Test
    void elEntrenadorDeOtraCategoriaRecibe403() {
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, sub13Ana.getId().toString(), null, LUIS_ENTRENADOR))
                .isInstanceOf(SinPermisoAsistenciaException.class).hasMessage(ReglasAsistencia.SIN_PERMISO);
        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, sub13Ana, nino(NINO_1, "PRESENTE")),
                LUIS_ENTRENADOR)).isInstanceOf(SinPermisoAsistenciaException.class);
        verify(asistencias, never()).buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any());
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void elEntrenadorQueDirigeLaFranjaDeOtraCategoriaPuedeComoEnElHorario() {
        servicio.registrar(pedido(MARTES_PASADO, sub13DirigeLuis, nino(NINO_1, "PRESENTE")), LUIS_ENTRENADOR);

        verify(asistencias).guardar(eq(ENTRENAMIENTO), eq(LUIS), any());
        assertThat(servicio.consultar(MARTES_PASADO, sub13DirigeLuis.getId().toString(), null, LUIS_ENTRENADOR)
                .puedeRegistrar()).isTrue();
    }

    @Test
    void dirigirUnExtraDeOtraCategoriaNoDaPermisoPeroSerDuenoDeLaCategoriaSi() {
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, null, extraSub13.id().toString(), LUIS_ENTRENADOR))
                .isInstanceOf(SinPermisoAsistenciaException.class);

        assertThat(servicio.consultar(MARTES_PASADO, null, extraSub12.id().toString(), LUIS_ENTRENADOR).puedeRegistrar())
                .isTrue();
    }

    @Test
    void unEntrenadorSinFichaRecibe403() {
        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, sub12Luis.getId().toString(), null, SIN_FICHA))
                .isInstanceOf(SinPermisoAsistenciaException.class);
    }

    @Test
    void elPermisoSeVerificaAntesQueLaFechaFuturaYLaCancelacion() {
        when(asistencias.franjaCancelada(sub13Ana.getId(), LocalDate.parse(MARTES_FUTURO))).thenReturn(true);

        assertThatThrownBy(() -> servicio.consultar(MARTES_FUTURO, sub13Ana.getId().toString(), null, LUIS_ENTRENADOR))
                .isInstanceOf(SinPermisoAsistenciaException.class);
        verify(asistencias, never()).franjaCancelada(any(), any());
    }

    // --- Fecha futura y cancelada ---------------------------------------------------------------------------

    @Test
    void unaFechaFuturaEs400EnFechaEnElGetYEnElPut() {
        assertThatThrownBy(() -> servicio.consultar(MARTES_FUTURO, sub12Luis.getId().toString(), null, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> {
                    assertThat(e.getCampos()).containsExactly("fecha");
                    assertThat(e.getMessage()).contains(ReglasAsistencia.FECHA_FUTURA);
                });
        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_FUTURO, sub12Luis, nino(NINO_1, "PRESENTE")), ADMIN))
                .isInstanceOf(DatosInvalidosException.class);
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void hoyEsValido() {
        SesionAsistenciaResponseDTO r = servicio.registrar(pedido(HOY, sub12Miercoles, nino(NINO_1, "PRESENTE")), ADMIN);

        assertThat(r.sesion().fecha()).isEqualTo(HOY);
        assertThat(r.puedeRegistrar()).isTrue();
    }

    @Test
    void unaFranjaCanceladaEseDiaEs409EnElGetYEnElPut() {
        when(asistencias.franjaCancelada(sub12Luis.getId(), LocalDate.parse(MARTES_PASADO))).thenReturn(true);

        assertThatThrownBy(() -> servicio.consultar(MARTES_PASADO, sub12Luis.getId().toString(), null, ADMIN))
                .isInstanceOfSatisfying(ConflictoException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(ReglasAsistencia.SESION_CANCELADA);
                    assertThat(e.getCampos()).containsExactly("fecha");
                });
        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE")), ADMIN))
                .isInstanceOf(ConflictoException.class);
        verify(asistencias, never()).buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any());
    }

    @Test
    void laCancelacionDeUnaFranjaNoAfectaAOtraFranjaNiAUnExtra() {
        when(asistencias.franjaCancelada(sub12Luis.getId(), LocalDate.parse(MARTES_PASADO))).thenReturn(true);

        assertThat(servicio.consultar(MARTES_PASADO, semilleroTurnos.getId().toString(), null, ADMIN)).isNotNull();
        assertThat(servicio.consultar(MARTES_PASADO, null, extraSub12.id().toString(), ADMIN)).isNotNull();
    }

    // --- Lista y grupo --------------------------------------------------------------------------------------

    @Test
    void laListaSePideConLaCategoriaElGrupoYElAnioDelReloj() {
        servicio.consultar(MARTES_PASADO, semilleroTarde.getId().toString(), null, ADMIN);
        servicio.consultar(MARTES_PASADO, semilleroTurnos.getId().toString(), null, ADMIN);
        servicio.consultar(MARTES_PASADO, sub12Luis.getId().toString(), null, ADMIN);

        verify(asistencias).listarDeportistas(SEMILLERO, "tarde", 2026, null);
        verify(asistencias).listarDeportistas(SEMILLERO, null, 2026, null);
        verify(asistencias).listarDeportistas(SUB12, null, 2026, null);
    }

    @Test
    void unaSesionDeGrupoManianaSoloComparaConLaListaDeEseGrupo() {
        FranjaHorario manana = franja("Semillero", SEMILLERO, 2, "08:00", "09:30", null, null, "mañana");
        when(franjas.buscarPorId(manana.getId())).thenReturn(Optional.of(manana));

        servicio.consultar(MARTES_PASADO, manana.getId().toString(), null, ADMIN);

        verify(asistencias).listarDeportistas(SEMILLERO, "mañana", 2026, null);
    }

    @Test
    void unDeportistaFueraDeLaListaDeLaSesionEs400EnRegistrosYNoSeCreaNiSeGuardaNada() {
        UUID ajeno = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE"),
                nino(ajeno, "AUSENTE")), ADMIN)).isInstanceOfSatisfying(DatosInvalidosException.class, e -> {
                    assertThat(e.getCampos()).containsExactly("registros");
                    assertThat(e.getMessage()).contains(ReglasAsistencia.DEPORTISTA_FUERA_DE_LISTA);
                });
        verify(asistencias, never()).buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any());
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void registrosConSoloUnaParteDeLaListaSonValidos() {
        servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_2, "JUSTIFICADO")), ADMIN);

        ArgumentCaptor<List<RegistroAsistencia>> guardados = ArgumentCaptor.captor();
        verify(asistencias).guardar(eq(ENTRENAMIENTO), any(), guardados.capture());
        assertThat(guardados.getValue()).containsExactly(new RegistroAsistencia(NINO_2, "JUSTIFICADO", null));
    }

    // --- Entrenador asignado y quien pasa lista -------------------------------------------------------------

    @Test
    void elAdministradorEnUnaFranjaConEntrenadorDejaElAsignadoYQuienPasoListaNulo() {
        servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE")), ADMIN);

        verify(asistencias).buscarOCrearEntrenamiento(SUB12, LocalDate.parse(MARTES_PASADO), LocalTime.of(16, 0),
                LocalTime.of(17, 30), "Horizontes", LUIS);
        verify(asistencias).guardar(eq(ENTRENAMIENTO), eq(null), any());
    }

    @Test
    void elAdministradorEnUnaFranjaPorTurnosDejaAmbosEntrenadoresNulos() {
        servicio.registrar(pedido(MARTES_PASADO, semilleroTurnos, nino(NINO_1, "PRESENTE")), ADMIN);

        verify(asistencias).buscarOCrearEntrenamiento(SEMILLERO, LocalDate.parse(MARTES_PASADO), LocalTime.of(16, 0),
                LocalTime.of(17, 30), "Horizontes", null);
        verify(asistencias).guardar(eq(ENTRENAMIENTO), eq(null), any());
    }

    @Test
    void unExtraUsaSuPropioHorarioYSuEntrenadorAsignado() {
        servicio.registrar(pedido(MARTES_PASADO, null, extraSub12.id(), nino(NINO_1, "PRESENTE")), LUIS_ENTRENADOR);

        verify(asistencias).buscarOCrearEntrenamiento(SUB12, LocalDate.parse(MARTES_PASADO), LocalTime.of(18, 0),
                LocalTime.of(19, 0), "Horizontes", LUIS);
        verify(asistencias).guardar(eq(ENTRENAMIENTO), eq(LUIS), any());
    }

    @Test
    void elEstadoSeNormalizaYLasObservacionesSeLimpianAntesDeGuardar() {
        servicio.registrar(pedido(MARTES_PASADO, sub12Luis,
                new RegistroAsistenciaRequestDTO(" " + NINO_1 + " ", " presente ", "  Llegó \n tarde "),
                new RegistroAsistenciaRequestDTO(NINO_2.toString(), "AUSENTE", "   ")), ADMIN);

        ArgumentCaptor<List<RegistroAsistencia>> guardados = ArgumentCaptor.captor();
        verify(asistencias).guardar(eq(ENTRENAMIENTO), any(), guardados.capture());
        assertThat(guardados.getValue()).containsExactly(new RegistroAsistencia(NINO_1, "PRESENTE", "Llegó tarde"),
                new RegistroAsistencia(NINO_2, "AUSENTE", null));
    }

    // --- Solape y transacción -------------------------------------------------------------------------------

    @Test
    void unSolapeRealSeTraduceAConflictoSinCamposYNoGuarda() {
        when(asistencias.buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any()))
                .thenThrow(new SolapeDeEntrenamientoException());

        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE")), ADMIN))
                .isInstanceOfSatisfying(ConflictoException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(ReglasAsistencia.SOLAPE);
                    assertThat(e.getCampos()).isEmpty();
                });
        verify(asistencias, never()).guardar(any(), any(), any());
    }

    @Test
    void elPutCorreEnUnaSolaTransaccionYUnFalloDelGuardadoSePropaga() {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("estado"))
                .when(asistencias).guardar(any(), any(), any());

        assertThatThrownBy(() -> servicio.registrar(pedido(MARTES_PASADO, sub12Luis, nino(NINO_1, "PRESENTE")), ADMIN))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        verify(transaccion, times(1)).execute(any());
    }

    // --- Consultar: solo lectura y respuesta ----------------------------------------------------------------

    @Test
    void elGetNoCreaNadaYSiLaSesionNoTieneEntrenamientoLaListaSaleSinMarcas() {
        when(asistencias.buscarEntrenamiento(any(), any(), any(), any())).thenReturn(Optional.empty());

        SesionAsistenciaResponseDTO r = servicio.consultar(MARTES_PASADO, sub12Luis.getId().toString(), null, ADMIN);

        verify(asistencias, never()).buscarOCrearEntrenamiento(any(), any(), any(), any(), any(), any());
        verify(asistencias, never()).guardar(any(), any(), any());
        verify(asistencias).listarDeportistas(SUB12, null, 2026, null);
        assertThat(r.deportistas()).extracting("estado").containsOnlyNulls();
    }

    @Test
    void elGetUsaElEntrenamientoYaCreadoParaTraerLasMarcas() {
        when(asistencias.buscarEntrenamiento(SUB12, LocalDate.parse(MARTES_PASADO), LocalTime.of(16, 0), "Horizontes"))
                .thenReturn(Optional.of(ENTRENAMIENTO));
        when(asistencias.listarDeportistas(SUB12, null, 2026, ENTRENAMIENTO)).thenReturn(List.of(
                new DeportistaDeSesion(NINO_1, "Ana", "Alfa", "AUSENTE", "Sin aviso")));

        SesionAsistenciaResponseDTO r = servicio.consultar(MARTES_PASADO, sub12Luis.getId().toString(), null, ADMIN);

        assertThat(r.deportistas()).singleElement().satisfies(d -> {
            assertThat(d.idDeportista()).isEqualTo(NINO_1);
            assertThat(d.estado()).isEqualTo("AUSENTE");
            assertThat(d.observaciones()).isEqualTo("Sin aviso");
        });
    }

    @Test
    void laRespuestaTraeLosDatosDeLaSesionDeLaFranja() {
        SesionAsistenciaResponseDTO r = servicio.consultar(MARTES_PASADO, semilleroTarde.getId().toString(), null, ADMIN);

        assertThat(r.sesion().fecha()).isEqualTo(MARTES_PASADO);
        assertThat(r.sesion().horaInicio()).isEqualTo("16:00");
        assertThat(r.sesion().horaFin()).isEqualTo("17:30");
        assertThat(r.sesion().idCategoria()).isEqualTo(SEMILLERO);
        assertThat(r.sesion().categoria()).isEqualTo("Semillero");
        assertThat(r.sesion().sede()).isEqualTo("Horizontes");
        assertThat(r.sesion().grupo()).isEqualTo("tarde");
        assertThat(r.sesion().idEntrenador()).isNull();
        assertThat(r.sesion().entrenador()).isNull();
        assertThat(r.puedeRegistrar()).isTrue();
        assertThat(r.deportistas()).hasSize(2);
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------

    private static FranjaHorario franja(String categoria, UUID idCategoria, int dia, String inicio, String fin,
                                        UUID idEntrenador, String entrenador, String grupo) {
        FranjaHorario f = new FranjaHorario();
        f.setId(UUID.randomUUID());
        f.setIdCategoria(idCategoria);
        f.setCategoria(categoria);
        f.setDia(dia);
        f.setHoraInicio(LocalTime.parse(inicio));
        f.setHoraFin(LocalTime.parse(fin));
        f.setSede("Horizontes");
        f.setIdEntrenador(idEntrenador);
        f.setEntrenador(entrenador);
        f.setGrupo(grupo);
        return f;
    }

    private static ExcepcionHorario extra(String categoria, UUID idCategoria, String fecha, String inicio, String fin,
                                          UUID idEntrenador, String entrenador, String grupo) {
        return new ExcepcionHorario(UUID.randomUUID(), ExcepcionHorario.EXTRA, LocalDate.parse(fecha), null, idCategoria,
                categoria, null, LocalTime.parse(inicio), LocalTime.parse(fin), "Horizontes", idEntrenador, entrenador,
                true, grupo, null, null);
    }

    private static RegistroAsistenciaRequestDTO nino(UUID id, String estado) {
        return new RegistroAsistenciaRequestDTO(id.toString(), estado, null);
    }

    private static AsistenciaRequestDTO pedido(String fecha, FranjaHorario franja, RegistroAsistenciaRequestDTO... registros) {
        return pedido(fecha, franja.getId(), null, registros);
    }

    private static AsistenciaRequestDTO pedido(String fecha, UUID idFranja, UUID idExcepcion,
                                               RegistroAsistenciaRequestDTO... registros) {
        return new AsistenciaRequestDTO(fecha, idFranja == null ? null : idFranja.toString(),
                idExcepcion == null ? null : idExcepcion.toString(), List.of(registros));
    }
}
