package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.AccionSemanaResponseDTO;
import co.edu.futdata.api.dto.CancelacionRequestDTO;
import co.edu.futdata.api.dto.ExtraRequestDTO;
import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import co.edu.futdata.api.dto.SesionSemanaResponseDTO;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.datasource.external.NotificacionRechazadaException;
import co.edu.futdata.datasource.external.NotificadorMalConfiguradoException;
import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.Destinatario;
import co.edu.futdata.persistence.dao.FranjaYaCanceladaException;
import co.edu.futdata.persistence.dao.IDestinatariosDAO;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IExcepcionHorarioDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.SemanaNoPermitidaException;
import co.edu.futdata.services.facade.SinPermisoHorarioException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** {@link HorarioSemanaServiceImpl} con los DAO simulados. El recorrido completo contra PostgreSQL está en {@code HorarioSemanaConPostgresTest}. */
class HorarioSemanaServiceImplTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID SEMILLERO = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6c");
    private static final UUID SUB12 = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID SUB13 = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6d");
    private static final UUID LUIS = UUID.fromString("e0000000-0000-0000-0000-00000000000a");
    private static final UUID ANA = UUID.fromString("e0000000-0000-0000-0000-00000000000b");
    private static final UUID INACTIVO = UUID.fromString("e0000000-0000-0000-0000-0000000000ff");
    private static final UUID USUARIO_LUIS = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID USUARIO_ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID USUARIO_ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");

    private static final Solicitante ADMIN = new Solicitante(USUARIO_ADMIN, Solicitante.ADMINISTRADOR);
    private static final Solicitante ENTRENADOR_LUIS = new Solicitante(USUARIO_LUIS, Solicitante.ENTRENADOR);
    private static final Solicitante ACUDIENTE = new Solicitante(USUARIO_ACUDIENTE, Solicitante.ACUDIENTE);
    private static final Solicitante TESORERO = new Solicitante(UUID.randomUUID(), "TESORERO");

    private IFranjaHorarioDAO franjas;
    private IExcepcionHorarioDAO excepciones;
    private IEntrenadorDAO entrenadores;
    private ICategoriaDAO categorias;
    private IDestinatariosDAO destinatarios;
    private INotificador notificador;

    private FranjaHorario deSub12;      // martes, Sub-12, dirige Luis
    private FranjaHorario semilleroTarde; // martes, Semillero, "por turnos"
    private FranjaHorario semilleroMan; // martes, Semillero grupo mañana, "por turnos"
    private FranjaHorario sub13Ana;     // sábado, Sub-13, dirige Ana
    private FranjaHorario sub13Luis;    // lunes, Sub-13 (categoría de Ana) pero lo dirige Luis

    @BeforeEach
    void preparar() {
        franjas = mock(IFranjaHorarioDAO.class);
        excepciones = mock(IExcepcionHorarioDAO.class);
        entrenadores = mock(IEntrenadorDAO.class);
        categorias = mock(ICategoriaDAO.class);
        destinatarios = mock(IDestinatariosDAO.class);
        notificador = mock(INotificador.class);
        deSub12 = franja("Sub-12", SUB12, 2, "16:30", "17:30", LUIS, "Luis Pérez", null);
        semilleroTarde = franja("Semillero", SEMILLERO, 2, "16:30", "18:00", null, null, "tarde");
        semilleroMan = franja("Semillero", SEMILLERO, 2, "16:30", "18:00", null, null, "mañana");
        sub13Ana = franja("Sub-13", SUB13, 6, "08:00", "09:00", ANA, "Ana Díaz", null);
        sub13Luis = franja("Sub-13", SUB13, 1, "19:00", "20:00", LUIS, "Luis Pérez", null);
        // Desordenadas a propósito: el servicio ordena por fecha, hora, categoría oficial y grupo
        when(franjas.listar(null, null, null)).thenReturn(List.of(deSub12, semilleroTarde, semilleroMan, sub13Ana, sub13Luis));
        for (FranjaHorario f : List.of(deSub12, semilleroTarde, semilleroMan, sub13Ana, sub13Luis)) {
            when(franjas.buscarPorId(f.getId())).thenReturn(Optional.of(f));
        }
        when(excepciones.listar(any(), any())).thenReturn(List.of());
        when(franjas.nombreDeCategoria(SUB12)).thenReturn(Optional.of("Sub-12"));
        when(franjas.nombreDeCategoria(SEMILLERO)).thenReturn(Optional.of("Semillero"));
        Entrenador activo = new Entrenador();
        activo.setId(ANA);
        activo.setActivo(true);
        when(entrenadores.buscarPorId(ANA)).thenReturn(Optional.of(activo));
        Entrenador activoLuis = new Entrenador();
        activoLuis.setId(LUIS);
        when(entrenadores.buscarPorId(LUIS)).thenReturn(Optional.of(activoLuis));
        Entrenador inactivo = new Entrenador();
        inactivo.setId(INACTIVO);
        inactivo.setActivo(false);
        when(entrenadores.buscarPorId(INACTIVO)).thenReturn(Optional.of(inactivo));
        // Luis es el entrenador de Sub-12 (y dirige además una franja de Sub-13, que es de Ana)
        Entrenador luis = new Entrenador();
        luis.setId(LUIS);
        when(entrenadores.buscarPorIdUsuario(USUARIO_LUIS)).thenReturn(Optional.of(luis));
        when(categorias.listarPorEntrenador(LUIS)).thenReturn(List.of(new Categoria(SUB12, "Sub-12", 12, 12, LUIS)));
    }

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
        f.setEntrenadorActivo(idEntrenador == null ? null : true);
        f.setGrupo(grupo);
        return f;
    }

    private HorarioSemanaServiceImpl servicioEl(String instante) {
        return servicioEl(instante, TransactionOperations.withoutTransaction());
    }

    private HorarioSemanaServiceImpl servicioEl(String instante, TransactionOperations transaccion) {
        return new HorarioSemanaServiceImpl(franjas, excepciones, entrenadores, categorias,
                Clock.fixed(Instant.parse(instante), BOGOTA), transaccion, destinatarios, notificador);
    }

    /** Lunes 2 de noviembre de 2026: ninguna sesión de la semana (2 al 7) es de una fecha pasada. */
    private HorarioSemanaServiceImpl lunes() {
        return servicioEl("2026-11-02T15:00:00Z");
    }

    /** Miércoles 4 de noviembre de 2026 (semana del 2 al 7). */
    private HorarioSemanaServiceImpl miercoles() {
        return servicioEl("2026-11-04T15:00:00Z");
    }

    private static ExcepcionHorario cancelacion(UUID id, FranjaHorario f, LocalDate fecha, String motivo) {
        return new ExcepcionHorario(id, ExcepcionHorario.CANCELACION, fecha, f.getId(), f.getIdCategoria(), f.getCategoria(),
                f.getDia(), f.getHoraInicio(), f.getHoraFin(), f.getSede(), f.getIdEntrenador(), f.getEntrenador(),
                f.getEntrenadorActivo(), f.getGrupo(), motivo, null);
    }

    private static ExcepcionHorario extra(UUID id, LocalDate fecha) {
        return new ExcepcionHorario(id, ExcepcionHorario.EXTRA, fecha, null, SUB12, "Sub-12", null, LocalTime.of(18, 0),
                LocalTime.of(19, 0), "Horizontes", LUIS, "Luis Pérez", true, null, null, null);
    }

    private static SesionSemanaResponseDTO de(HorarioSemanaResponseDTO semana, FranjaHorario f) {
        return semana.sesiones().stream().filter(s -> f.getId().equals(s.idFranja())).findFirst().orElseThrow();
    }

    // --- La semana: fechas ----------------------------------------------------------------------------------

    @Test
    void sinFechaUsaLaSemanaDeHoyEnBogota() {
        HorarioSemanaResponseDTO semana = miercoles().consultar(null, ADMIN);

        assertThat(semana.desde()).isEqualTo("2026-11-02");
        assertThat(semana.hasta()).isEqualTo("2026-11-07");
        verify(excepciones).listar(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 7));
    }

    @Test
    void unaFechaEnBlancoEsComoNoPonerla() {
        assertThat(miercoles().consultar("  ", ADMIN).desde()).isEqualTo("2026-11-02");
    }

    @Test
    void cadaFranjaCaeEnLaFechaDeSuDiaYSaleOrdenada() {
        List<SesionSemanaResponseDTO> sesiones = miercoles().consultar(null, ADMIN).sesiones();

        assertThat(sesiones).extracting(SesionSemanaResponseDTO::fecha, SesionSemanaResponseDTO::horaInicio,
                        SesionSemanaResponseDTO::categoria, SesionSemanaResponseDTO::grupo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2026-11-02", "19:00", "Sub-13", null),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "Semillero", "mañana"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "Semillero", "tarde"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "Sub-12", null),
                        org.assertj.core.groups.Tuple.tuple("2026-11-07", "08:00", "Sub-13", null));
    }

    @Test
    void cualquierDiaDeLaSemanaPedidaDaLaMismaSemanaYElDomingoLaSiguiente() {
        assertThat(miercoles().consultar("2026-11-07", ADMIN).desde()).isEqualTo("2026-11-02");
        assertThat(miercoles().consultar("2026-11-08", ADMIN).desde()).isEqualTo("2026-11-09");
        assertThat(miercoles().consultar("2026-11-09", ADMIN).hasta()).isEqualTo("2026-11-14");
        assertThat(miercoles().consultar("2020-01-01", ADMIN).desde()).isEqualTo("2019-12-30");
    }

    @Test
    void elDomingoSinFechaMuestraLaSemanaQueEmpiezaManana() {
        HorarioSemanaResponseDTO semana = servicioEl("2026-11-08T15:00:00Z").consultar(null, ADMIN);

        assertThat(semana.desde()).isEqualTo("2026-11-09");
        assertThat(semana.sesiones().get(0).fecha()).isEqualTo("2026-11-09");
    }

    @Test
    void fechaInvalidaEsUn400ConElCampoFecha() {
        for (String mala : List.of("hoy", "2026-13-01", "2026-02-30", "04/11/2026")) {
            assertThatThrownBy(() -> miercoles().consultar(mala, ADMIN))
                    .isInstanceOf(DatosInvalidosException.class)
                    .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos()).containsExactly("fecha"))
                    .hasMessageContaining("AAAA-MM-DD");
        }
    }

    @Test
    void elAcudientePuedePedirLaSemanaEnCursoYNoOtra() {
        assertThat(miercoles().consultar(null, ACUDIENTE).desde()).isEqualTo("2026-11-02");
        assertThat(miercoles().consultar("2026-11-07", ACUDIENTE).desde()).isEqualTo("2026-11-02");
        for (String otra : List.of("2026-10-31", "2026-11-09", "2026-11-08")) {
            assertThatThrownBy(() -> miercoles().consultar(otra, ACUDIENTE))
                    .isInstanceOf(SemanaNoPermitidaException.class)
                    .hasMessage("Solo puedes consultar la semana en curso");
        }
    }

    @Test
    void elDomingoLaSemanaEnCursoDelAcudienteEsLaSiguiente() {
        HorarioSemanaServiceImpl domingo = servicioEl("2026-11-08T15:00:00Z");

        assertThat(domingo.consultar("2026-11-09", ACUDIENTE).desde()).isEqualTo("2026-11-09");
        assertThat(domingo.consultar("2026-11-08", ACUDIENTE).desde()).isEqualTo("2026-11-09");
        assertThatThrownBy(() -> domingo.consultar("2026-11-02", ACUDIENTE)).isInstanceOf(SemanaNoPermitidaException.class);
    }

    @Test
    void losDemasRolesPidenCualquierSemana() {
        assertThat(miercoles().consultar("2027-03-01", ADMIN).desde()).isEqualTo("2027-03-01");
        assertThat(miercoles().consultar("2027-03-01", ENTRENADOR_LUIS).desde()).isEqualTo("2027-03-01");
        assertThat(miercoles().consultar("2027-03-01", TESORERO).desde()).isEqualTo("2027-03-01");
    }

    // --- La semana: cancelaciones ---------------------------------------------------------------------------

    @Test
    void unaFranjaConCancelacionEnSuFechaSaleCanceladaConSuIdYMotivo() {
        UUID id = UUID.randomUUID();
        when(excepciones.listar(any(), any()))
                .thenReturn(List.of(cancelacion(id, deSub12, LocalDate.of(2026, 11, 3), "Lluvia")));

        HorarioSemanaResponseDTO semana = miercoles().consultar(null, ADMIN);

        SesionSemanaResponseDTO cancelada = de(semana, deSub12);
        assertThat(cancelada.tipo()).isEqualTo("FRANJA");
        assertThat(cancelada.estado()).isEqualTo("CANCELADO");
        assertThat(cancelada.idExcepcion()).isEqualTo(id);
        assertThat(cancelada.motivo()).isEqualTo("Lluvia");
        assertThat(cancelada.idFranja()).isEqualTo(deSub12.getId());
        assertThat(cancelada.fecha()).isEqualTo("2026-11-03");
        // Las demás siguen ACTIVAS, sin excepción ni motivo
        assertThat(semana.sesiones()).filteredOn(s -> !s.idFranja().equals(deSub12.getId()))
                .allSatisfy(s -> {
                    assertThat(s.estado()).isEqualTo("ACTIVO");
                    assertThat(s.idExcepcion()).isNull();
                    assertThat(s.motivo()).isNull();
                });
    }

    @Test
    void unaCancelacionSinMotivoSaleCanceladaConMotivoNull() {
        UUID id = UUID.randomUUID();
        when(excepciones.listar(any(), any())).thenReturn(List.of(cancelacion(id, sub13Ana, LocalDate.of(2026, 11, 7), null)));

        SesionSemanaResponseDTO s = de(miercoles().consultar(null, ADMIN), sub13Ana);

        assertThat(s.estado()).isEqualTo("CANCELADO");
        assertThat(s.motivo()).isNull();
        assertThat(s.idExcepcion()).isEqualTo(id);
    }

    @Test
    void unaCancelacionDeOtraSemanaNoAfectaEstaSemana() {
        when(excepciones.listar(any(), any()))
                .thenReturn(List.of(cancelacion(UUID.randomUUID(), deSub12, LocalDate.of(2026, 11, 10), null)));

        assertThat(miercoles().consultar(null, ADMIN).sesiones()).allMatch(s -> s.estado().equals("ACTIVO"));
    }

    @Test
    void unaCancelacionCuyoDiaYaNoCoincideConElDeSuFranjaNoSeAplica() {
        // La franja era de otro día (el 4A le cambió el día): la cancelación quedó en un miércoles y la franja es martes
        when(excepciones.listar(any(), any()))
                .thenReturn(List.of(cancelacion(UUID.randomUUID(), deSub12, LocalDate.of(2026, 11, 4), "Vieja")));

        SesionSemanaResponseDTO s = de(miercoles().consultar(null, ADMIN), deSub12);

        assertThat(s.estado()).isEqualTo("ACTIVO");
        assertThat(s.idExcepcion()).isNull();
        assertThat(s.motivo()).isNull();
    }

    // --- La semana: extras y fechas pasadas -----------------------------------------------------------------

    private static ExcepcionHorario extraEn(UUID id, String fecha, UUID categoria, String nombre, UUID entrenador,
                                            String nombreEntrenador, Boolean activo, String grupo, String motivo) {
        return new ExcepcionHorario(id, ExcepcionHorario.EXTRA, LocalDate.parse(fecha), null, categoria, nombre, null,
                LocalTime.of(18, 0), LocalTime.of(19, 30), "UVA El Paraíso", entrenador, nombreEntrenador, activo, grupo,
                motivo, null);
    }

    @Test
    void losExtrasSeMuestranConSusCamposYOrdenadosJuntoConLasFranjas() {
        UUID id = UUID.randomUUID();
        when(excepciones.listar(any(), any())).thenReturn(List.of(
                extraEn(id, "2026-11-03", SUB12, "Sub-12", LUIS, "Luis Pérez", true, null, "Partido amistoso")));

        List<SesionSemanaResponseDTO> sesiones = lunes().consultar(null, ADMIN).sesiones();

        assertThat(sesiones).hasSize(6);
        // martes: las 16:30 (Semillero mañana, tarde, Sub-12) y después el extra de las 18:00
        assertThat(sesiones).extracting(SesionSemanaResponseDTO::fecha, SesionSemanaResponseDTO::horaInicio, SesionSemanaResponseDTO::tipo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2026-11-02", "19:00", "FRANJA"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "FRANJA"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "FRANJA"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "FRANJA"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "18:00", "EXTRA"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-07", "08:00", "FRANJA"));
        SesionSemanaResponseDTO x = sesiones.get(4);
        assertThat(x.estado()).isEqualTo("ACTIVO");
        assertThat(x.idFranja()).isNull();
        assertThat(x.idExcepcion()).isEqualTo(id);
        assertThat(x.dia()).isEqualTo(2);
        assertThat(x.horaFin()).isEqualTo("19:30");
        assertThat(x.idCategoria()).isEqualTo(SUB12);
        assertThat(x.categoria()).isEqualTo("Sub-12");
        assertThat(x.sede()).isEqualTo("UVA El Paraíso");
        assertThat(x.idEntrenador()).isEqualTo(LUIS);
        assertThat(x.entrenador()).isEqualTo("Luis Pérez");
        assertThat(x.entrenadorActivo()).isTrue();
        assertThat(x.grupo()).isNull();
        assertThat(x.motivo()).isEqualTo("Partido amistoso");
    }

    @Test
    void unExtraPorTurnosYUnoConEntrenadorQueSeDesactivoSeMuestranBien() {
        when(excepciones.listar(any(), any())).thenReturn(List.of(
                extraEn(UUID.randomUUID(), "2026-11-04", SEMILLERO, "Semillero", null, null, null, "tarde", null),
                extraEn(UUID.randomUUID(), "2026-11-05", SUB12, "Sub-12", LUIS, "Luis Pérez", false, null, null)));

        List<SesionSemanaResponseDTO> extras = lunes().consultar(null, ADMIN).sesiones().stream()
                .filter(s -> s.tipo().equals("EXTRA")).toList();

        assertThat(extras).hasSize(2);
        assertThat(extras.get(0).idEntrenador()).isNull();
        assertThat(extras.get(0).entrenador()).isNull();
        assertThat(extras.get(0).entrenadorActivo()).isNull();
        assertThat(extras.get(0).grupo()).isEqualTo("tarde");
        assertThat(extras.get(1).entrenadorActivo()).isFalse();
        assertThat(extras.get(1).dia()).isEqualTo(4);
    }

    @Test
    void puedeGestionarUnExtraSoloPorLaCategoriaNuncaPorSerElEntrenadorAsignado() {
        // Luis es de Sub-12. Extra de Semillero (de Ana) dirigido por Luis; extra de Sub-12 dirigido por Ana
        when(excepciones.listar(any(), any())).thenReturn(List.of(
                extraEn(UUID.randomUUID(), "2026-11-03", SEMILLERO, "Semillero", LUIS, "Luis Pérez", true, null, null),
                extraEn(UUID.randomUUID(), "2026-11-05", SUB12, "Sub-12", ANA, "Ana Díaz", true, null, null)));

        List<SesionSemanaResponseDTO> deLuis = lunes().consultar(null, ENTRENADOR_LUIS).sesiones().stream()
                .filter(s -> s.tipo().equals("EXTRA")).toList();
        assertThat(deLuis.get(0).categoria()).isEqualTo("Semillero");
        assertThat(deLuis.get(0).puedeGestionar()).as("lo dirige él pero la categoría es de Ana").isFalse();
        assertThat(deLuis.get(1).categoria()).isEqualTo("Sub-12");
        assertThat(deLuis.get(1).puedeGestionar()).as("categoría suya aunque lo dirija Ana").isTrue();

        assertThat(lunes().consultar(null, ADMIN).sesiones()).filteredOn(s -> s.tipo().equals("EXTRA"))
                .allMatch(SesionSemanaResponseDTO::puedeGestionar);
        assertThat(lunes().consultar(null, ACUDIENTE).sesiones()).noneMatch(SesionSemanaResponseDTO::puedeGestionar);
        assertThat(lunes().consultar(null, TESORERO).sesiones()).noneMatch(SesionSemanaResponseDTO::puedeGestionar);
    }

    @Test
    void puedeGestionarEsFalsoEnLasFechasAnterioresAHoyParaTodosLosRolesPeroHoyNoEsPasada() {
        // Martes 3 de noviembre: el lunes ya pasó; el martes (hoy) y lo que sigue, no
        when(excepciones.listar(any(), any())).thenReturn(List.of(
                extraEn(UUID.randomUUID(), "2026-11-02", SUB12, "Sub-12", LUIS, "Luis Pérez", true, null, null),
                extraEn(UUID.randomUUID(), "2026-11-03", SUB12, "Sub-12", LUIS, "Luis Pérez", true, null, null)));
        HorarioSemanaServiceImpl martes = servicioEl("2026-11-03T15:00:00Z");

        for (Solicitante quien : List.of(ADMIN, ENTRENADOR_LUIS)) {
            List<SesionSemanaResponseDTO> sesiones = martes.consultar(null, quien).sesiones();
            assertThat(sesiones).filteredOn(s -> s.fecha().equals("2026-11-02")).isNotEmpty()
                    .noneMatch(SesionSemanaResponseDTO::puedeGestionar);
            // Hoy (martes) la franja de Sub-12 y el extra de Sub-12 sí se gestionan
            assertThat(sesiones).filteredOn(s -> s.fecha().equals("2026-11-03") && s.categoria().equals("Sub-12"))
                    .isNotEmpty().allMatch(SesionSemanaResponseDTO::puedeGestionar);
        }
        // El sábado 7 (futuro) también
        assertThat(martes.consultar(null, ADMIN).sesiones()).filteredOn(s -> s.fecha().equals("2026-11-07"))
                .allMatch(SesionSemanaResponseDTO::puedeGestionar);
    }

    @Test
    void laFranjaCanceladaDeUnaFechaPasadaTampocoSeGestiona() {
        when(excepciones.listar(any(), any()))
                .thenReturn(List.of(cancelacion(UUID.randomUUID(), deSub12, LocalDate.of(2026, 11, 3), null)));

        SesionSemanaResponseDTO s = de(miercoles().consultar(null, ADMIN), deSub12);

        assertThat(s.estado()).isEqualTo("CANCELADO");
        assertThat(s.puedeGestionar()).isFalse();
    }

    // --- La semana: puedeGestionar --------------------------------------------------------------------------

    @Test
    void elAdministradorPuedeGestionarTodoSinConsultarEntrenadores() {
        assertThat(lunes().consultar(null, ADMIN).sesiones()).allMatch(SesionSemanaResponseDTO::puedeGestionar);
        verify(entrenadores, never()).buscarPorIdUsuario(any());
        verify(categorias, never()).listarPorEntrenador(any());
    }

    @Test
    void elEntrenadorPuedeGestionarSusCategoriasYLasFranjasQueDirige() {
        HorarioSemanaResponseDTO semana = lunes().consultar(null, ENTRENADOR_LUIS);

        assertThat(de(semana, deSub12).puedeGestionar()).as("su categoría y la dirige").isTrue();
        assertThat(de(semana, sub13Luis).puedeGestionar()).as("categoría de Ana, pero la dirige él").isTrue();
        assertThat(de(semana, sub13Ana).puedeGestionar()).as("categoría y franja de Ana").isFalse();
        assertThat(de(semana, semilleroTarde).puedeGestionar()).as("por turnos de una categoría ajena").isFalse();
        assertThat(de(semana, semilleroMan).puedeGestionar()).isFalse();
    }

    @Test
    void elEntrenadorPuedeGestionarUnaFranjaPorTurnosDeSuCategoria() {
        when(categorias.listarPorEntrenador(LUIS)).thenReturn(List.of(new Categoria(SEMILLERO, "Semillero", 9, 10, LUIS)));

        HorarioSemanaResponseDTO semana = lunes().consultar(null, ENTRENADOR_LUIS);

        assertThat(de(semana, semilleroTarde).puedeGestionar()).isTrue();
        assertThat(de(semana, semilleroMan).puedeGestionar()).isTrue();
        assertThat(de(semana, deSub12).puedeGestionar()).as("la dirige él").isTrue();
    }

    @Test
    void laLecturaDelEntrenadorEsUnaSolaPorPeticionYNuncaUnaPorSesion() {
        lunes().consultar(null, ENTRENADOR_LUIS);

        verify(entrenadores, times(1)).buscarPorIdUsuario(USUARIO_LUIS);
        verify(categorias, times(1)).listarPorEntrenador(LUIS);
        verify(excepciones, never()).esDuenoDeFranja(any(), any());
        verify(excepciones, never()).esDuenoDeCategoria(any(), any());
        verify(excepciones, never()).esDuenoDeExcepcion(any(), any());
    }

    @Test
    void unEntrenadorSinFilaVinculadaNoPuedeGestionarNada() {
        when(entrenadores.buscarPorIdUsuario(USUARIO_LUIS)).thenReturn(Optional.empty());

        assertThat(miercoles().consultar(null, ENTRENADOR_LUIS).sesiones()).noneMatch(SesionSemanaResponseDTO::puedeGestionar);
    }

    @Test
    void elAcudienteYElTesoreroNuncaPuedenGestionar() {
        assertThat(miercoles().consultar(null, ACUDIENTE).sesiones()).noneMatch(SesionSemanaResponseDTO::puedeGestionar);
        assertThat(miercoles().consultar(null, TESORERO).sesiones()).noneMatch(SesionSemanaResponseDTO::puedeGestionar);
        verify(entrenadores, never()).buscarPorIdUsuario(any());
    }

    @Test
    void laFranjaCanceladaTambienSeGestionaParaPoderDeshacerla() {
        when(excepciones.listar(any(), any()))
                .thenReturn(List.of(cancelacion(UUID.randomUUID(), deSub12, LocalDate.of(2026, 11, 3), null)));

        SesionSemanaResponseDTO s = de(lunes().consultar(null, ENTRENADOR_LUIS), deSub12);

        assertThat(s.estado()).isEqualTo("CANCELADO");
        assertThat(s.puedeGestionar()).isTrue();
    }

    // --- Cancelar -------------------------------------------------------------------------------------------

    /** El martes de la semana siguiente (10 de noviembre). */
    private static final String MARTES = "2026-11-10";

    private static CancelacionRequestDTO pedir(FranjaHorario f, String fecha, String motivo) {
        return new CancelacionRequestDTO(fecha, f == null ? null : f.getId().toString(), motivo);
    }

    private void creaLaCancelacion(UUID id, FranjaHorario f, String fecha, String motivo) {
        when(excepciones.crearCancelacion(any(), any(), any(), any())).thenReturn(id);
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(cancelacion(id, f, LocalDate.parse(fecha), motivo)));
    }

    @Test
    void cancelarGuardaConElUsuarioDelTokenYDevuelveLaSesionCancelada() {
        UUID id = UUID.randomUUID();
        creaLaCancelacion(id, deSub12, MARTES, "Lluvia");

        AccionSemanaResponseDTO respuesta = miercoles().cancelar(pedir(deSub12, MARTES, "  Lluvia  "), ADMIN);

        verify(excepciones).crearCancelacion(deSub12.getId(), LocalDate.parse(MARTES), "Lluvia", USUARIO_ADMIN);
        assertThat(respuesta.avisoEnviado()).isNull();
        SesionSemanaResponseDTO s = respuesta.sesion();
        assertThat(s.tipo()).isEqualTo("FRANJA");
        assertThat(s.estado()).isEqualTo("CANCELADO");
        assertThat(s.idExcepcion()).isEqualTo(id);
        assertThat(s.idFranja()).isEqualTo(deSub12.getId());
        assertThat(s.fecha()).isEqualTo(MARTES);
        assertThat(s.dia()).isEqualTo(2);
        assertThat(s.horaInicio()).isEqualTo("16:30");
        assertThat(s.horaFin()).isEqualTo("17:30");
        assertThat(s.categoria()).isEqualTo("Sub-12");
        assertThat(s.entrenador()).isEqualTo("Luis Pérez");
        assertThat(s.motivo()).isEqualTo("Lluvia");
        assertThat(s.puedeGestionar()).isTrue();
    }

    @Test
    void elMotivoVacioSeGuardaComoNull() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);

        miercoles().cancelar(pedir(deSub12, MARTES, "   "), ADMIN);

        verify(excepciones).crearCancelacion(any(), any(), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void hoyEsUnaFechaValidaParaCancelar() {
        FranjaHorario miercoles = franja("Sub-12", SUB12, 3, "16:00", "17:00", LUIS, "Luis Pérez", null);
        when(franjas.buscarPorId(miercoles.getId())).thenReturn(Optional.of(miercoles));
        creaLaCancelacion(UUID.randomUUID(), miercoles, "2026-11-04", null);

        assertThat(miercoles().cancelar(pedir(miercoles, "2026-11-04", null), ADMIN).sesion().estado()).isEqualTo("CANCELADO");
    }

    @Test
    void elCuerpoSeValidaJuntoEnUnSolo400() {
        assertThatThrownBy(() -> miercoles().cancelar(new CancelacionRequestDTO("mañana", "no-es-uuid", "x".repeat(201)), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsExactlyInAnyOrder("fecha", "idFranja", "motivo"));
        assertThatThrownBy(() -> miercoles().cancelar(new CancelacionRequestDTO(null, null, null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsExactlyInAnyOrder("fecha", "idFranja"));
        assertThatThrownBy(() -> miercoles().cancelar(null, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("cuerpo"));
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void unaFranjaQueNoExisteEs404() {
        FranjaHorario fantasma = franja("Sub-12", SUB12, 2, "16:00", "17:00", null, null, null);
        when(franjas.buscarPorId(fantasma.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> miercoles().cancelar(pedir(fantasma, MARTES, null), ADMIN))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe una franja con ese id.");
    }

    @Test
    void unaFechaPasadaEs400ConElCampoFecha() {
        // El martes 3 de noviembre ya pasó (hoy es miércoles 4)
        assertThatThrownBy(() -> miercoles().cancelar(pedir(deSub12, "2026-11-03", null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("fecha"))
                .hasMessageContaining("pasada");
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void unaFechaDeOtroDiaDeLaSemanaEs400ConElCampoFecha() {
        // 11 de noviembre es miércoles y la franja es de los martes
        assertThatThrownBy(() -> miercoles().cancelar(pedir(deSub12, "2026-11-11", null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("fecha"))
                .hasMessageContaining("no coincide");
        // y un domingo no coincide con ningún día de franja
        assertThatThrownBy(() -> miercoles().cancelar(pedir(deSub12, "2026-11-15", null), ADMIN))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void laFechaPasadaSeRevisaAntesQueElPermiso() {
        when(excepciones.esDuenoDeFranja(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> miercoles().cancelar(pedir(sub13Ana, "2026-10-31", null), ENTRENADOR_LUIS))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void elEntrenadorDuenoCancelaConLaConsultaDePropiedadEnSql() {
        when(excepciones.esDuenoDeFranja(USUARIO_LUIS, deSub12.getId())).thenReturn(true);
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);

        assertThat(miercoles().cancelar(pedir(deSub12, MARTES, null), ENTRENADOR_LUIS).sesion().estado())
                .isEqualTo("CANCELADO");
        verify(excepciones).crearCancelacion(deSub12.getId(), LocalDate.parse(MARTES), null, USUARIO_LUIS);
    }

    @Test
    void elEntrenadorAjenoOLaFranjaPorTurnosDeOtraCategoriaEs403SinGuardarNada() {
        when(excepciones.esDuenoDeFranja(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> miercoles().cancelar(pedir(semilleroTarde, MARTES, null), ENTRENADOR_LUIS))
                .isInstanceOf(SinPermisoHorarioException.class)
                .hasMessage("Solo puedes modificar el horario de tus categorías");
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void elAdministradorSeSaltaLaConsultaDePropiedadPorSuRol() {
        creaLaCancelacion(UUID.randomUUID(), semilleroTarde, MARTES, null);

        miercoles().cancelar(pedir(semilleroTarde, MARTES, null), ADMIN);

        verify(excepciones, never()).esDuenoDeFranja(any(), any());
    }

    @Test
    void otroRolQueLlegueAlServicioNoPuedeCancelar() {
        assertThatThrownBy(() -> miercoles().cancelar(pedir(deSub12, MARTES, null), TESORERO))
                .isInstanceOf(SinPermisoHorarioException.class);
        assertThatThrownBy(() -> miercoles().cancelar(pedir(deSub12, MARTES, null), ACUDIENTE))
                .isInstanceOf(SinPermisoHorarioException.class);
        verify(excepciones, never()).crearCancelacion(any(), any(), any(), any());
    }

    @Test
    void cancelarDosVecesDejaPasarLaExcepcionDelDaoQueElManejadorConvierteEn409() {
        when(excepciones.crearCancelacion(any(), any(), any(), any())).thenThrow(new FranjaYaCanceladaException(new RuntimeException()));

        assertThatThrownBy(() -> miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN))
                .isInstanceOf(FranjaYaCanceladaException.class)
                .hasMessage("Esa franja ya está cancelada en esa fecha");
    }

    // --- Deshacer -------------------------------------------------------------------------------------------

    @Test
    void deshacerBorraLaCancelacionYDevuelveLaFranjaActivaEseDia() {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(cancelacion(id, deSub12, LocalDate.parse(MARTES), "Lluvia")));
        when(excepciones.eliminar(id)).thenReturn(true);

        AccionSemanaResponseDTO respuesta = miercoles().deshacerCancelacion(id, ADMIN);

        verify(excepciones).eliminar(id);
        assertThat(respuesta.avisoEnviado()).isNull();
        SesionSemanaResponseDTO s = respuesta.sesion();
        assertThat(s.estado()).isEqualTo("ACTIVO");
        assertThat(s.idExcepcion()).isNull();
        assertThat(s.motivo()).isNull();
        assertThat(s.idFranja()).isEqualTo(deSub12.getId());
        assertThat(s.fecha()).isEqualTo(MARTES);
        assertThat(s.puedeGestionar()).isTrue();
    }

    @Test
    void deshacerUnaCancelacionQueNoExisteOUnExtraEs404() {
        UUID noExiste = UUID.randomUUID();
        UUID idExtra = UUID.randomUUID();
        when(excepciones.buscarPorId(noExiste)).thenReturn(Optional.empty());
        when(excepciones.buscarPorId(idExtra)).thenReturn(Optional.of(extra(idExtra, LocalDate.parse(MARTES))));

        for (UUID id : List.of(noExiste, idExtra)) {
            assertThatThrownBy(() -> miercoles().deshacerCancelacion(id, ADMIN))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("No existe una cancelación con ese id.");
        }
        verify(excepciones, never()).eliminar(any());
    }

    @Test
    void noSeDeshaceUnaCancelacionDeFechaPasadaPeroHoySi() {
        UUID pasada = UUID.randomUUID();
        UUID deHoy = UUID.randomUUID();
        FranjaHorario hoyMiercoles = franja("Sub-12", SUB12, 3, "16:00", "17:00", LUIS, "Luis Pérez", null);
        when(excepciones.buscarPorId(pasada)).thenReturn(Optional.of(cancelacion(pasada, deSub12, LocalDate.of(2026, 11, 3), null)));
        when(excepciones.buscarPorId(deHoy)).thenReturn(Optional.of(cancelacion(deHoy, hoyMiercoles, LocalDate.of(2026, 11, 4), null)));

        assertThatThrownBy(() -> miercoles().deshacerCancelacion(pasada, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("fecha"));
        assertThat(miercoles().deshacerCancelacion(deHoy, ADMIN).sesion().estado()).isEqualTo("ACTIVO");
    }

    @Test
    void elEntrenadorAjenoNoDeshaceYElDuenoSi() {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(cancelacion(id, semilleroTarde, LocalDate.parse(MARTES), null)));
        when(excepciones.esDuenoDeExcepcion(USUARIO_LUIS, id)).thenReturn(false);

        assertThatThrownBy(() -> miercoles().deshacerCancelacion(id, ENTRENADOR_LUIS))
                .isInstanceOf(SinPermisoHorarioException.class);
        verify(excepciones, never()).eliminar(any());

        when(excepciones.esDuenoDeExcepcion(USUARIO_LUIS, id)).thenReturn(true);
        assertThat(miercoles().deshacerCancelacion(id, ENTRENADOR_LUIS).sesion().estado()).isEqualTo("ACTIVO");
        verify(excepciones).eliminar(id);
    }

    @Test
    void elAdministradorDeshaceSinConsultarLaPropiedad() {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(cancelacion(id, semilleroTarde, LocalDate.parse(MARTES), null)));

        miercoles().deshacerCancelacion(id, ADMIN);

        verify(excepciones, never()).esDuenoDeExcepcion(any(), any());
    }

    // --- Crear un extra -------------------------------------------------------------------------------------

    private static ExtraRequestDTO extraPedido(UUID categoria, String fecha, String inicio, String fin, String sede,
                                               UUID entrenador, String grupo, String motivo) {
        return new ExtraRequestDTO(categoria == null ? null : categoria.toString(), fecha, inicio, fin, sede,
                entrenador == null ? null : entrenador.toString(), grupo, motivo);
    }

    /** Un extra válido del miércoles 11 de noviembre en Sub-12, dirigido por Luis. */
    private static ExtraRequestDTO sub12Luis() {
        return extraPedido(SUB12, "2026-11-11", "18:00", "19:30", "Horizontes", LUIS, null, "  Partido  ");
    }

    private UUID seCreaElExtra(String fecha, UUID categoria, String nombre, UUID entrenador, String nombreEntrenador,
                               String grupo, String motivo) {
        UUID id = UUID.randomUUID();
        when(excepciones.crearExtra(any())).thenReturn(id);
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(
                extraEn(id, fecha, categoria, nombre, entrenador, nombreEntrenador, entrenador == null ? null : true, grupo, motivo)));
        return id;
    }

    @Test
    void crearUnExtraGuardaTodoConElUsuarioDelTokenYDevuelveLaSesion() {
        UUID id = seCreaElExtra("2026-11-11", SUB12, "Sub-12", LUIS, "Luis Pérez", null, "Partido");

        AccionSemanaResponseDTO respuesta = miercoles().crearExtra(sub12Luis(), ADMIN);

        ArgumentCaptor<ExcepcionHorario> captura = ArgumentCaptor.forClass(ExcepcionHorario.class);
        verify(excepciones).crearExtra(captura.capture());
        ExcepcionHorario guardado = captura.getValue();
        assertThat(guardado.esExtra()).isTrue();
        assertThat(guardado.fecha()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(guardado.idCategoria()).isEqualTo(SUB12);
        assertThat(guardado.horaInicio()).isEqualTo(LocalTime.of(18, 0));
        assertThat(guardado.horaFin()).isEqualTo(LocalTime.of(19, 30));
        assertThat(guardado.sede()).isEqualTo("Horizontes");
        assertThat(guardado.idEntrenador()).isEqualTo(LUIS);
        assertThat(guardado.motivo()).isEqualTo("Partido");
        assertThat(guardado.creadoPor()).isEqualTo(USUARIO_ADMIN);
        assertThat(respuesta.avisoEnviado()).isNull();
        SesionSemanaResponseDTO s = respuesta.sesion();
        assertThat(s.tipo()).isEqualTo("EXTRA");
        assertThat(s.estado()).isEqualTo("ACTIVO");
        assertThat(s.idFranja()).isNull();
        assertThat(s.idExcepcion()).isEqualTo(id);
        assertThat(s.fecha()).isEqualTo("2026-11-11");
        assertThat(s.dia()).isEqualTo(3);
        assertThat(s.horaInicio()).isEqualTo("18:00");
        assertThat(s.horaFin()).isEqualTo("19:30");
        assertThat(s.categoria()).isEqualTo("Sub-12");
        assertThat(s.entrenador()).isEqualTo("Luis Pérez");
        assertThat(s.motivo()).isEqualTo("Partido");
        assertThat(s.puedeGestionar()).isTrue();
    }

    @Test
    void elBloqueoDelEntrenadorElCruceYElInsertCorrenEnLaMismaTransaccionYEnEseOrden() {
        AtomicBoolean dentro = new AtomicBoolean();
        int[] transacciones = {0};
        TransactionOperations contando = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> accion) {
                transacciones[0]++;
                dentro.set(true);
                try {
                    return accion.doInTransaction(new SimpleTransactionStatus());
                } finally {
                    dentro.set(false);
                }
            }
        };
        boolean[] enTransaccion = {false, false};
        doAnswer(i -> {
            enTransaccion[0] = dentro.get();
            return false;
        }).when(excepciones).hayFranjaQueSeCruza(any(), any(), any(), any());
        UUID id = UUID.randomUUID();
        doAnswer(i -> {
            enTransaccion[1] = dentro.get();
            return id;
        }).when(excepciones).crearExtra(any());
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(
                extraEn(id, "2026-11-11", SUB12, "Sub-12", LUIS, "Luis Pérez", true, null, null)));

        servicioEl("2026-11-04T15:00:00Z", contando).crearExtra(sub12Luis(), ADMIN);

        assertThat(transacciones[0]).as("una sola transacción").isEqualTo(1);
        assertThat(enTransaccion).containsExactly(true, true);
        org.mockito.InOrder orden = inOrder(excepciones);
        orden.verify(excepciones).hayFranjaQueSeCruza(LUIS, LocalDate.of(2026, 11, 11), LocalTime.of(18, 0), LocalTime.of(19, 30));
        orden.verify(excepciones).crearExtra(any());
    }

    @Test
    void unExtraPorTurnosNoBloqueaNiComparaNingunEntrenador() {
        seCreaElExtra("2026-11-11", SEMILLERO, "Semillero", null, null, "tarde", null);

        AccionSemanaResponseDTO respuesta = miercoles().crearExtra(
                extraPedido(SEMILLERO, "2026-11-11", "18:00", "19:00", "UVA El Paraíso", null, "  TARDE ", null), ADMIN);

        verify(excepciones, never()).hayFranjaQueSeCruza(any(), any(), any(), any());
        verify(entrenadores, never()).buscarPorId(any());
        ArgumentCaptor<ExcepcionHorario> captura = ArgumentCaptor.forClass(ExcepcionHorario.class);
        verify(excepciones).crearExtra(captura.capture());
        assertThat(captura.getValue().idEntrenador()).isNull();
        assertThat(captura.getValue().grupo()).isEqualTo("tarde");
        assertThat(respuesta.sesion().idEntrenador()).isNull();
        assertThat(respuesta.sesion().entrenador()).isNull();
    }

    @Test
    void hoyEsUnaFechaValidaParaUnExtra() {
        seCreaElExtra("2026-11-04", SUB12, "Sub-12", null, null, null, null);

        assertThat(miercoles().crearExtra(extraPedido(SUB12, "2026-11-04", "18:00", "19:00", "Horizontes", null, null, null), ADMIN)
                .sesion().puedeGestionar()).isTrue();
    }

    @Test
    void elCuerpoDelExtraSeValidaJuntoEnUnSolo400() {
        ExtraRequestDTO malo = new ExtraRequestDTO("no-uuid", "mañana", "25:00", "", "Cancha 3", "tampoco", "noche", "x".repeat(201));

        assertThatThrownBy(() -> miercoles().crearExtra(malo, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactlyInAnyOrder(
                        "idCategoria", "fecha", "horaInicio", "horaFin", "sede", "idEntrenador", "grupo", "motivo"));
        assertThatThrownBy(() -> miercoles().crearExtra(new ExtraRequestDTO(null, null, null, null, null, null, null, null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos())
                        .containsExactlyInAnyOrder("idCategoria", "fecha", "horaInicio", "horaFin", "sede"));
        assertThatThrownBy(() -> miercoles().crearExtra(null, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("cuerpo"));
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void laHoraFinDebeSerPosteriorYElGrupoSoloVaEnCachorrosYSemillero() {
        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-11", "19:00", "18:00", "Horizontes", null, null, null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("horaFin"));
        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", null, "tarde", null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("grupo"));
        seCreaElExtra("2026-11-11", SEMILLERO, "Semillero", null, null, "mañana", null);
        assertThat(miercoles().crearExtra(
                extraPedido(SEMILLERO, "2026-11-11", "18:00", "19:00", "Horizontes", null, "mañana", null), ADMIN)).isNotNull();
    }

    @Test
    void unaCategoriaQueNoExisteEs404DespuesDeValidarElCuerpo() {
        UUID fantasma = UUID.randomUUID();
        when(franjas.nombreDeCategoria(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(fantasma, "2026-11-11", "18:00", "19:00", "Horizontes", null, null, null), ADMIN))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe una categoría con ese id.");
        // con otro error en el cuerpo, gana el 400
        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(fantasma, "mañana", "18:00", "19:00", "Horizontes", null, null, null), ADMIN))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void unaFechaPasadaOEnDomingoEs400ConElCampoFecha() {
        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-03", "18:00", "19:00", "Horizontes", null, null, null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("fecha"))
                .hasMessageContaining("pasada");
        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-08", "18:00", "19:00", "Horizontes", null, null, null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("fecha"))
                .hasMessageContaining("lunes a sábado");
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void elEntrenadorSoloCreaExtrasEnSusCategoriasConLaConsultaEnSql() {
        when(excepciones.esDuenoDeCategoria(USUARIO_LUIS, SEMILLERO)).thenReturn(false);
        when(excepciones.esDuenoDeCategoria(USUARIO_LUIS, SUB12)).thenReturn(true);
        seCreaElExtra("2026-11-11", SUB12, "Sub-12", null, null, null, null);

        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SEMILLERO, "2026-11-11", "18:00", "19:00", "Horizontes", LUIS, null, null), ENTRENADOR_LUIS))
                .isInstanceOf(SinPermisoHorarioException.class)
                .hasMessage("Solo puedes modificar el horario de tus categorías");
        verify(excepciones, never()).crearExtra(any());
        assertThat(miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", null, null, null), ENTRENADOR_LUIS)).isNotNull();
    }

    @Test
    void elPermisoSeRevisaAntesQueElEntrenadorAsignadoYElAdministradorSeLoSalta() {
        when(excepciones.esDuenoDeCategoria(any(), any())).thenReturn(false);
        UUID fantasma = UUID.randomUUID();
        when(entrenadores.buscarPorId(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", fantasma, null, null), ENTRENADOR_LUIS))
                .isInstanceOf(SinPermisoHorarioException.class);
        // otros roles que lleguen al servicio no pueden
        assertThatThrownBy(() -> miercoles().crearExtra(sub12Luis(), ACUDIENTE)).isInstanceOf(SinPermisoHorarioException.class);
        seCreaElExtra("2026-11-11", SUB12, "Sub-12", null, null, null, null);
        miercoles().crearExtra(extraPedido(SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", null, null, null), ADMIN);
        verify(excepciones, never()).esDuenoDeCategoria(USUARIO_ADMIN, SUB12);
    }

    @Test
    void elEntrenadorAsignadoDebeExistirYEstarActivoConLosMensajesDeLasFranjas() {
        UUID fantasma = UUID.randomUUID();
        when(entrenadores.buscarPorId(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", fantasma, null, null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("idEntrenador"))
                .hasMessageContaining("no existe un entrenador con ese id");
        assertThatThrownBy(() -> miercoles().crearExtra(
                extraPedido(SUB12, "2026-11-11", "18:00", "19:00", "Horizontes", INACTIVO, null, null), ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("idEntrenador"))
                .hasMessageContaining("el entrenador está inactivo");
        verify(excepciones, never()).hayFranjaQueSeCruza(any(), any(), any(), any());
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void elCruceConUnaFranjaNoCanceladaEs409SinInsertar() {
        when(excepciones.hayFranjaQueSeCruza(any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> miercoles().crearExtra(sub12Luis(), ADMIN))
                .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCampos()).containsExactly("idEntrenador"))
                .hasMessage("Ese entrenador ya tiene otra franja que se cruza con este horario");
        verify(excepciones, never()).crearExtra(any());
    }

    @Test
    void elCruceConOtroExtraQueDetectaLaBaseEsElMismo409() {
        when(excepciones.crearExtra(any())).thenThrow(new CruceDeHorarioException(new RuntimeException()));

        assertThatThrownBy(() -> miercoles().crearExtra(sub12Luis(), ADMIN))
                .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCampos()).containsExactly("idEntrenador"))
                .hasMessage("Ese entrenador ya tiene otra franja que se cruza con este horario");
    }

    // --- Eliminar un extra ----------------------------------------------------------------------------------

    @Test
    void eliminarUnExtraLoBorraYRespondeSesionNull() {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(extraEn(id, "2026-11-11", SUB12, "Sub-12", LUIS, "Luis Pérez", true, null, null)));

        AccionSemanaResponseDTO respuesta = miercoles().eliminarExtra(id, ADMIN);

        verify(excepciones).eliminar(id);
        assertThat(respuesta.sesion()).isNull();
        assertThat(respuesta.avisoEnviado()).isNull();
        verify(excepciones, never()).esDuenoDeExcepcion(any(), any());
    }

    @Test
    void eliminarUnExtraQueNoExisteOUnaCancelacionEs404() {
        UUID noExiste = UUID.randomUUID();
        UUID idCancelacion = UUID.randomUUID();
        when(excepciones.buscarPorId(noExiste)).thenReturn(Optional.empty());
        when(excepciones.buscarPorId(idCancelacion))
                .thenReturn(Optional.of(cancelacion(idCancelacion, deSub12, LocalDate.of(2026, 11, 10), null)));

        for (UUID id : List.of(noExiste, idCancelacion)) {
            assertThatThrownBy(() -> miercoles().eliminarExtra(id, ADMIN))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("No existe un extra con ese id.");
        }
        verify(excepciones, never()).eliminar(any());
    }

    @Test
    void noSeEliminaUnExtraDeFechaPasadaPeroHoySi() {
        UUID pasado = UUID.randomUUID();
        UUID deHoy = UUID.randomUUID();
        when(excepciones.buscarPorId(pasado)).thenReturn(Optional.of(extraEn(pasado, "2026-11-03", SUB12, "Sub-12", null, null, null, null, null)));
        when(excepciones.buscarPorId(deHoy)).thenReturn(Optional.of(extraEn(deHoy, "2026-11-04", SUB12, "Sub-12", null, null, null, null, null)));

        assertThatThrownBy(() -> miercoles().eliminarExtra(pasado, ADMIN))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos()).containsExactly("fecha"));
        assertThat(miercoles().eliminarExtra(deHoy, ADMIN).sesion()).isNull();
        verify(excepciones).eliminar(deHoy);
        verify(excepciones, never()).eliminar(pasado);
    }

    @Test
    void elEntrenadorSoloEliminaExtrasDeSuCategoria() {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(extraEn(id, "2026-11-11", SEMILLERO, "Semillero", LUIS, "Luis Pérez", true, null, null)));
        when(excepciones.esDuenoDeExcepcion(USUARIO_LUIS, id)).thenReturn(false);

        assertThatThrownBy(() -> miercoles().eliminarExtra(id, ENTRENADOR_LUIS)).isInstanceOf(SinPermisoHorarioException.class);
        verify(excepciones, never()).eliminar(any());

        when(excepciones.esDuenoDeExcepcion(USUARIO_LUIS, id)).thenReturn(true);
        assertThat(miercoles().eliminarExtra(id, ENTRENADOR_LUIS).sesion()).isNull();
        verify(excepciones).eliminar(id);
        assertThatThrownBy(() -> miercoles().eliminarExtra(id, TESORERO)).isInstanceOf(SinPermisoHorarioException.class);
    }

    // --- Avisos CAMBIO_ENTRENAMIENTO ------------------------------------------------------------------------

    private static final Destinatario MARIA = new Destinatario("maria@correo.co", "María Torres");

    private void conDestinatarios(Destinatario... destino) {
        when(destinatarios.acudientesDeCategoria(any(), anyInt())).thenReturn(List.of(destino));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> datosEnviados() {
        ArgumentCaptor<Map<String, Object>> captura = ArgumentCaptor.forClass(Map.class);
        verify(notificador).notificar(org.mockito.ArgumentMatchers.eq("CAMBIO_ENTRENAMIENTO"), captura.capture());
        return captura.getValue();
    }

    private static Map<String, Object> entrenamiento(String categoria, String fecha, String hora, String lugar, String entrenador) {
        Map<String, Object> e = new java.util.LinkedHashMap<>();
        e.put("categoria", categoria);
        e.put("fecha", fecha);
        e.put("horaInicio", hora);
        e.put("lugar", lugar);
        e.put("entrenador", entrenador);
        return e;
    }

    private static List<Map<String, Object>> para(Destinatario... destino) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Destinatario d : destino) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("correo", d.correo());
            m.put("nombre", d.nombre());
            lista.add(m);
        }
        return lista;
    }

    @Test
    void cancelarUnaFranjaAvisaCanceladoConElMotivoDeLaCancelacion() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, "Lluvia");
        conDestinatarios(MARIA);

        AccionSemanaResponseDTO respuesta = miercoles().cancelar(pedir(deSub12, MARTES, "Lluvia"), ADMIN);

        assertThat(respuesta.avisoEnviado()).isTrue();
        assertThat(datosEnviados()).isEqualTo(Map.of(
                "tipoCambio", "CANCELADO",
                "entrenamiento", entrenamiento("Sub-12", MARTES, "16:30", "Horizontes", "Luis Pérez"),
                "motivo", "Lluvia",
                "destinatarios", para(MARIA)));
        // Los destinatarios son los acudientes de LA categoría de la franja, con el año de Bogotá
        verify(destinatarios).acudientesDeCategoria(SUB12, 2026);
    }

    @Test
    void laFranjaConGrupoVaConElGrupoEnLaCategoriaYPorTurnosSinNombre() {
        creaLaCancelacion(UUID.randomUUID(), semilleroTarde, MARTES, null);
        conDestinatarios(MARIA);

        miercoles().cancelar(pedir(semilleroTarde, MARTES, null), ADMIN);

        Map<String, Object> datos = datosEnviados();
        assertThat(datos.get("entrenamiento"))
                .isEqualTo(entrenamiento("Semillero (tarde)", MARTES, "16:30", "Horizontes", "Por turnos"));
        // Sin motivo: la clave ni va
        assertThat(datos).doesNotContainKey("motivo");
        assertThat(datos).containsOnlyKeys("tipoCambio", "entrenamiento", "destinatarios");
        verify(destinatarios).acudientesDeCategoria(SEMILLERO, 2026);
    }

    @Test
    void deshacerUnaCancelacionAvisaCreadoSinMotivo() {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(cancelacion(id, deSub12, LocalDate.parse(MARTES), "Lluvia")));
        conDestinatarios(MARIA);

        AccionSemanaResponseDTO respuesta = miercoles().deshacerCancelacion(id, ADMIN);

        assertThat(respuesta.avisoEnviado()).isTrue();
        assertThat(datosEnviados()).isEqualTo(Map.of(
                "tipoCambio", "CREADO",
                "entrenamiento", entrenamiento("Sub-12", MARTES, "16:30", "Horizontes", "Luis Pérez"),
                "destinatarios", para(MARIA)));
    }

    @Test
    void crearUnExtraAvisaCreadoSinMotivoAunqueElExtraLoTenga() {
        seCreaElExtra("2026-11-11", SUB12, "Sub-12", LUIS, "Luis Pérez", null, "Partido");
        conDestinatarios(MARIA);

        AccionSemanaResponseDTO respuesta = miercoles().crearExtra(sub12Luis(), ADMIN);

        assertThat(respuesta.avisoEnviado()).isTrue();
        // El extra de las pruebas es de las 18:00 en UVA El Paraíso; n8n ignora el motivo en CREADO, por eso no se manda
        assertThat(datosEnviados()).isEqualTo(Map.of(
                "tipoCambio", "CREADO",
                "entrenamiento", entrenamiento("Sub-12", "2026-11-11", "18:00", "UVA El Paraíso", "Luis Pérez"),
                "destinatarios", para(MARIA)));
    }

    @Test
    void eliminarUnExtraAvisaCanceladoConElMotivoDelExtraYConLosDatosLeidosAntesDeBorrar() {
        UUID id = UUID.randomUUID();
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(
                extraEn(id, "2026-11-11", SEMILLERO, "Semillero", null, null, null, "mañana", "Partido amistoso")));
        conDestinatarios(MARIA);

        AccionSemanaResponseDTO respuesta = miercoles().eliminarExtra(id, ADMIN);

        assertThat(respuesta.sesion()).isNull();
        assertThat(respuesta.avisoEnviado()).isTrue();
        assertThat(datosEnviados()).isEqualTo(Map.of(
                "tipoCambio", "CANCELADO",
                "entrenamiento", entrenamiento("Semillero (mañana)", "2026-11-11", "18:00", "UVA El Paraíso", "Por turnos"),
                "motivo", "Partido amistoso",
                "destinatarios", para(MARIA)));
        verify(destinatarios).acudientesDeCategoria(SEMILLERO, 2026);
        org.mockito.InOrder orden = inOrder(excepciones, notificador);
        orden.verify(excepciones).buscarPorId(id);
        orden.verify(excepciones).eliminar(id);
        orden.verify(notificador).notificar(any(), any());
    }

    @Test
    void sinDestinatariosNoSeLlamaANotificarYAvisoEnviadoEsNull() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);
        when(destinatarios.acudientesDeCategoria(any(), anyInt())).thenReturn(List.of());

        AccionSemanaResponseDTO respuesta = miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN);

        assertThat(respuesta.avisoEnviado()).isNull();
        assertThat(respuesta.sesion().estado()).isEqualTo("CANCELADO");
        verify(notificador, never()).notificar(any(), any());
    }

    @Test
    void losCorreosQueN8nDescartariaNoCuentanYSiNoQuedaNingunoNoSeLlama() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);
        conDestinatarios(new Destinatario("sin-arroba", "A"), new Destinatario("con espacio@correo.co", "B"),
                new Destinatario("x@y", "C"), new Destinatario("a".repeat(250) + "@correo.co", "D"));

        assertThat(miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN).avisoEnviado()).isNull();
        verify(notificador, never()).notificar(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void losTextosSeRecortanALosLimitesDeN8nYElCorreoInvalidoSeDescartaSinRechazarElBloque() {
        FranjaHorario larga = franja("Sub-12", SUB12, 2, "16:30", "17:30", LUIS, "Á".repeat(150), null);
        larga.setSede("Horizontes");
        when(franjas.buscarPorId(larga.getId())).thenReturn(Optional.of(larga));
        UUID id = UUID.randomUUID();
        when(excepciones.crearCancelacion(any(), any(), any(), any())).thenReturn(id);
        when(excepciones.buscarPorId(id)).thenReturn(Optional.of(cancelacion(id, larga, LocalDate.parse(MARTES), null)));
        conDestinatarios(new Destinatario("malo", "Malo"), new Destinatario("maria@correo.co", "N".repeat(150)),
                new Destinatario("pedro@correo.co", null));

        miercoles().cancelar(pedir(larga, MARTES, null), ADMIN);

        Map<String, Object> datos = datosEnviados();
        assertThat(((Map<String, Object>) datos.get("entrenamiento")).get("entrenador").toString()).hasSize(120);
        List<Map<String, Object>> enviados = (List<Map<String, Object>>) datos.get("destinatarios");
        assertThat(enviados).extracting(d -> d.get("correo")).containsExactly("maria@correo.co", "pedro@correo.co");
        assertThat(enviados.get(0).get("nombre").toString()).hasSize(120);
        assertThat(enviados.get(1)).doesNotContainKey("nombre");
    }

    @Test
    @SuppressWarnings("unchecked")
    void masDe200DestinatariosSeParteEnBloquesDe200() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, "Lluvia");
        List<Destinatario> muchos = new ArrayList<>();
        for (int i = 0; i < 450; i++) {
            muchos.add(new Destinatario("acudiente" + i + "@correo.co", "Acudiente " + i));
        }
        when(destinatarios.acudientesDeCategoria(any(), anyInt())).thenReturn(muchos);

        AccionSemanaResponseDTO respuesta = miercoles().cancelar(pedir(deSub12, MARTES, "Lluvia"), ADMIN);

        assertThat(respuesta.avisoEnviado()).isTrue();
        ArgumentCaptor<Map<String, Object>> captura = ArgumentCaptor.forClass(Map.class);
        verify(notificador, times(3)).notificar(org.mockito.ArgumentMatchers.eq("CAMBIO_ENTRENAMIENTO"), captura.capture());
        List<Map<String, Object>> llamadas = captura.getAllValues();
        assertThat(llamadas).extracting(d -> ((List<?>) d.get("destinatarios")).size()).containsExactly(200, 200, 50);
        // Cada bloque lleva el mismo aviso y ningún correo se repite entre bloques
        assertThat(llamadas).extracting(d -> d.get("entrenamiento")).containsOnly(
                entrenamiento("Sub-12", MARTES, "16:30", "Horizontes", "Luis Pérez"));
        assertThat(llamadas).extracting(d -> d.get("motivo")).containsOnly("Lluvia");
        assertThat(llamadas.stream().flatMap(d -> ((List<Map<String, Object>>) d.get("destinatarios")).stream())
                .map(d -> d.get("correo")).distinct().count()).isEqualTo(450);
    }

    @Test
    void justo200EsUnaLlamadaY201SonDos() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);
        List<Destinatario> lista = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lista.add(new Destinatario("a" + i + "@correo.co", null));
        }
        when(destinatarios.acudientesDeCategoria(any(), anyInt())).thenReturn(lista);
        miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN);
        verify(notificador, times(1)).notificar(any(), any());

        lista.add(new Destinatario("otro@correo.co", null));
        miercoles().cancelar(pedir(deSub12, "2026-11-17", null), ADMIN);
        verify(notificador, times(3)).notificar(any(), any());
    }

    @Test
    void cualquierRuntimeExceptionDelAvisoDaFalseYLaOperacionQuedaHecha() {
        List<RuntimeException> fallos = List.of(
                new NotificacionNoEnviadaException("tiempo agotado", "CAMBIO_ENTRENAMIENTO", null, null),
                new NotificacionRechazadaException("CAMBIO_ENTRENAMIENTO", "destinatarios"),
                new NotificadorMalConfiguradoException("token", "CAMBIO_ENTRENAMIENTO", 401),
                new IllegalArgumentException("datos vacíos"),
                new IllegalStateException("cualquier otra cosa"));
        for (RuntimeException fallo : fallos) {
            org.mockito.Mockito.reset(excepciones, notificador);
            when(excepciones.listar(any(), any())).thenReturn(List.of());
            creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);
            conDestinatarios(MARIA);
            doAnswer(i -> {
                throw fallo;
            }).when(notificador).notificar(any(), any());

            AccionSemanaResponseDTO respuesta = miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN);

            assertThat(respuesta.avisoEnviado()).as(fallo.getClass().getSimpleName()).isFalse();
            assertThat(respuesta.sesion().estado()).isEqualTo("CANCELADO");
            verify(excepciones).crearCancelacion(any(), any(), any(), any());
        }
    }

    @Test
    void siFallaUnBloqueNoSeSiguenEnviandoLosDemasYElResultadoEsFalse() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);
        List<Destinatario> muchos = new ArrayList<>();
        for (int i = 0; i < 450; i++) {
            muchos.add(new Destinatario("a" + i + "@correo.co", null));
        }
        when(destinatarios.acudientesDeCategoria(any(), anyInt())).thenReturn(muchos);
        doAnswer(i -> {
            throw new NotificacionNoEnviadaException("tiempo agotado", "CAMBIO_ENTRENAMIENTO", null, null);
        }).when(notificador).notificar(any(), any());

        assertThat(miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN).avisoEnviado()).isFalse();
        verify(notificador, times(1)).notificar(any(), any());
    }

    @Test
    void siNoSePuedenLeerLosDestinatariosDaFalseSinRomperLaOperacion() {
        creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);
        when(destinatarios.acudientesDeCategoria(any(), anyInt())).thenThrow(new IllegalStateException("base caída"));

        AccionSemanaResponseDTO respuesta = miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN);

        assertThat(respuesta.avisoEnviado()).isFalse();
        assertThat(respuesta.sesion().estado()).isEqualTo("CANCELADO");
        verify(notificador, never()).notificar(any(), any());
    }

    @Test
    void elAvisoSeEnviaDespuesDelCommitNuncaDentroDeLaTransaccion() {
        List<String> orden = new ArrayList<>();
        TransactionOperations anotando = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> accion) {
                orden.add("tx-inicio");
                try {
                    return accion.doInTransaction(new SimpleTransactionStatus());
                } finally {
                    orden.add("tx-commit");
                }
            }
        };
        conDestinatarios(MARIA);
        doAnswer(i -> {
            orden.add("aviso");
            return null;
        }).when(notificador).notificar(any(), any());
        doAnswer(i -> {
            orden.add("insert-o-delete");
            return UUID.randomUUID();
        }).when(excepciones).crearCancelacion(any(), any(), any(), any());
        doAnswer(i -> {
            orden.add("insert-o-delete");
            return UUID.randomUUID();
        }).when(excepciones).crearExtra(any());
        doAnswer(i -> {
            orden.add("insert-o-delete");
            return true;
        }).when(excepciones).eliminar(any());
        UUID idCancelacion = UUID.randomUUID();
        UUID idExtra = UUID.randomUUID();
        when(excepciones.buscarPorId(any())).thenAnswer(i -> {
            UUID id = i.getArgument(0);
            if (id.equals(idExtra)) {
                return Optional.of(extraEn(id, "2026-11-11", SUB12, "Sub-12", LUIS, "Luis Pérez", true, null, null));
            }
            return Optional.of(cancelacion(id, deSub12, LocalDate.parse(MARTES), null));
        });
        HorarioSemanaServiceImpl servicio = servicioEl("2026-11-04T15:00:00Z", anotando);

        // Cancelar y crear un extra: el insert va dentro y el aviso después del commit
        servicio.cancelar(pedir(deSub12, MARTES, null), ADMIN);
        assertThat(orden).containsExactly("tx-inicio", "insert-o-delete", "tx-commit", "aviso");
        orden.clear();
        servicio.crearExtra(sub12Luis(), ADMIN);
        assertThat(orden).containsExactly("tx-inicio", "insert-o-delete", "tx-commit", "aviso");
        orden.clear();
        // Deshacer y eliminar un extra: el borrado va dentro y el aviso después del commit
        servicio.deshacerCancelacion(idCancelacion, ADMIN);
        assertThat(orden).containsExactly("tx-inicio", "insert-o-delete", "tx-commit", "aviso");
        orden.clear();
        servicio.eliminarExtra(idExtra, ADMIN);
        assertThat(orden).containsExactly("tx-inicio", "insert-o-delete", "tx-commit", "aviso");
    }

    @Test
    void siLaTransaccionFallaNoSeAvisaANadie() {
        conDestinatarios(MARIA);
        when(excepciones.crearCancelacion(any(), any(), any(), any())).thenThrow(new FranjaYaCanceladaException(new RuntimeException()));

        assertThatThrownBy(() -> miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN)).isInstanceOf(FranjaYaCanceladaException.class);
        verify(notificador, never()).notificar(any(), any());
        verify(destinatarios, never()).acudientesDeCategoria(any(), anyInt());
    }

    @Test
    void elLogDeUnFalloNoTraeCorreosNiNombresPeroSiElEventoLaExcepcionYLaCantidad() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(HorarioSemanaServiceImpl.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> registro = new ch.qos.logback.core.read.ListAppender<>();
        registro.start();
        logger.addAppender(registro);
        try {
            creaLaCancelacion(UUID.randomUUID(), deSub12, MARTES, null);
            conDestinatarios(MARIA, new Destinatario("pedro@correo.co", "Pedro Gómez"));
            doAnswer(i -> {
                throw new NotificacionNoEnviadaException("falló con maria@correo.co de María Torres", "CAMBIO_ENTRENAMIENTO", 502,
                        new RuntimeException("causa con pedro@correo.co"));
            }).when(notificador).notificar(any(), any());

            assertThat(miercoles().cancelar(pedir(deSub12, MARTES, null), ADMIN).avisoEnviado()).isFalse();

            assertThat(registro.list).hasSize(1);
            ch.qos.logback.classic.spi.ILoggingEvent evento = registro.list.get(0);
            assertThat(evento.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            String texto = evento.getFormattedMessage();
            assertThat(texto).contains("CAMBIO_ENTRENAMIENTO", "NotificacionNoEnviadaException", "2 destinatarios");
            assertThat(texto).doesNotContain("@", "maria", "pedro", "María", "Torres", "Pedro", "Gómez");
            // y sin la excepción adjunta (su mensaje y su causa traen los datos)
            assertThat(evento.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(registro);
        }
    }
}
