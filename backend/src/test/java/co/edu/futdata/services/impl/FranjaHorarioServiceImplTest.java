package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.FranjaHorarioRequestDTO;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link FranjaHorarioServiceImpl} con los DAO simulados. La restricción de cruce real está en la prueba con PostgreSQL. */
class FranjaHorarioServiceImplTest {

    private static final UUID ID = UUID.fromString("0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b");
    private static final UUID SUB12 = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID SEMILLERO = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6c");
    private static final UUID ACTIVO = UUID.fromString("e0000000-0000-0000-0000-00000000000a");
    private static final UUID INACTIVO = UUID.fromString("e0000000-0000-0000-0000-0000000000ff");
    private static final UUID OTRO_INACTIVO = UUID.fromString("e0000000-0000-0000-0000-0000000000fe");

    private IFranjaHorarioDAO dao;
    private IEntrenadorDAO entrenadores;
    private FranjaHorarioServiceImpl servicio;

    @BeforeEach
    void preparar() {
        dao = mock(IFranjaHorarioDAO.class);
        entrenadores = mock(IEntrenadorDAO.class);
        servicio = new FranjaHorarioServiceImpl(dao, entrenadores);
        when(dao.nombreDeCategoria(SUB12)).thenReturn(Optional.of("Sub-12"));
        when(dao.nombreDeCategoria(SEMILLERO)).thenReturn(Optional.of("Semillero"));
        when(entrenadores.buscarPorId(ACTIVO)).thenReturn(Optional.of(entrenador(ACTIVO, true)));
        when(entrenadores.buscarPorId(INACTIVO)).thenReturn(Optional.of(entrenador(INACTIVO, false)));
        when(entrenadores.buscarPorId(OTRO_INACTIVO)).thenReturn(Optional.of(entrenador(OTRO_INACTIVO, false)));
        when(dao.crear(any())).thenReturn(ID);
        when(dao.actualizar(any())).thenReturn(true);
    }

    private static Entrenador entrenador(UUID id, boolean activo) {
        Entrenador e = new Entrenador();
        e.setId(id);
        e.setNombre("Luis");
        e.setApellido("Pérez");
        e.setActivo(activo);
        return e;
    }

    private static FranjaHorario guardada(UUID idEntrenador) {
        FranjaHorario f = new FranjaHorario();
        f.setId(ID);
        f.setIdCategoria(SUB12);
        f.setCategoria("Sub-12");
        f.setDia(1);
        f.setHoraInicio(LocalTime.of(19, 0));
        f.setHoraFin(LocalTime.of(20, 0));
        f.setSede("Horizontes");
        f.setIdEntrenador(idEntrenador);
        if (idEntrenador != null) {
            f.setEntrenador("Luis Pérez");
            f.setEntrenadorActivo(!idEntrenador.equals(INACTIVO));
        }
        return f;
    }

    private static FranjaHorarioRequestDTO pedido(UUID idEntrenador) {
        return new FranjaHorarioRequestDTO(SUB12, 1, "18:00", "19:30", "Horizontes", idEntrenador, null);
    }

    // --- Lectura ------------------------------------------------------------------------------------------------

    @Test
    void listarDevuelveLasHorasEnHHmmYElEntrenador() {
        when(dao.listar(null, null, null)).thenReturn(List.of(guardada(ACTIVO), guardada(null)));

        List<FranjaHorarioResponseDTO> lista = servicio.listar(null, null, null);

        assertThat(lista.get(0)).isEqualTo(new FranjaHorarioResponseDTO(ID, SUB12, "Sub-12", 1, "19:00", "20:00",
                "Horizontes", ACTIVO, "Luis Pérez", true, null));
        assertThat(lista.get(1).idEntrenador()).isNull();
        assertThat(lista.get(1).entrenador()).isNull();
        assertThat(lista.get(1).entrenadorActivo()).isNull();
    }

    @Test
    void losFiltrosValidosLleganAlDaoYLosVaciosNo() {
        servicio.listar(" Semillero ", ACTIVO, "UVA El Paraíso");
        verify(dao).listar("Semillero", ACTIVO, "UVA El Paraíso");
        servicio.listar("  ", null, "");
        verify(dao).listar(null, null, null);
    }

    @Test
    void unaCategoriaDeFiltroQueNoEsDelClubDa400() {
        assertThatThrownBy(() -> servicio.listar("Sub-10", null, null))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("categoria: debe ser una de Párvulos, Cachorros, Semillero, Sub-11, Sub-12, Sub-13, "
                        + "Sub-14, Sub-15, Sub-16");
        verifyNoInteractions(dao);
    }

    @Test
    void unaSedeDeFiltroQueNoEsDelClubDa400() {
        assertThatThrownBy(() -> servicio.listar(null, null, "Estadio"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("sede: debe ser Horizontes o UVA El Paraíso");
        verifyNoInteractions(dao);
    }

    @Test
    void losDosFiltrosInvalidosSalenJuntos() {
        assertThatThrownBy(() -> servicio.listar("semillero", null, "horizontes"))
                .isInstanceOf(DatosInvalidosException.class)
                .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos()).containsExactly("categoria", "sede"));
    }

    @Test
    void consultarUnaFranjaQueNoExisteDa404() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultar(ID))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(FranjaHorarioServiceImpl.NO_EXISTE);
    }

    // --- Crear --------------------------------------------------------------------------------------------------

    @Test
    void creaConLasHorasConvertidasYDevuelveLaFranjaGuardada() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(ACTIVO)));

        FranjaHorarioResponseDTO creada = servicio.crear(
                new FranjaHorarioRequestDTO(SEMILLERO, 2, " 07:30 ", "09:00", "UVA El Paraíso", ACTIVO, "Mañana"));

        ArgumentCaptor<FranjaHorario> guardadaEnDao = ArgumentCaptor.forClass(FranjaHorario.class);
        verify(dao).crear(guardadaEnDao.capture());
        FranjaHorario f = guardadaEnDao.getValue();
        assertThat(f.getIdCategoria()).isEqualTo(SEMILLERO);
        assertThat(f.getDia()).isEqualTo(2);
        assertThat(f.getHoraInicio()).isEqualTo(LocalTime.of(7, 30));
        assertThat(f.getHoraFin()).isEqualTo(LocalTime.of(9, 0));
        assertThat(f.getSede()).isEqualTo("UVA El Paraíso");
        assertThat(f.getIdEntrenador()).isEqualTo(ACTIVO);
        assertThat(f.getGrupo()).isEqualTo("mañana");
        assertThat(creada.idFranja()).isEqualTo(ID);
    }

    @Test
    void creaPorTurnosSinConsultarEntrenadores() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(null)));
        servicio.crear(pedido(null));
        verifyNoInteractions(entrenadores);
    }

    @Test
    void sinCuerpoDa400() {
        assertThatThrownBy(() -> servicio.crear(null))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("cuerpo: es obligatorio");
    }

    @Test
    void unaCategoriaQueNoExisteDa400EnIdCategoria() {
        UUID inexistente = UUID.randomUUID();
        when(dao.nombreDeCategoria(inexistente)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.crear(
                new FranjaHorarioRequestDTO(inexistente, 1, "18:00", "19:00", "Horizontes", null, "mañana")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("idCategoria: " + FranjaHorarioServiceImpl.CATEGORIA_NO_EXISTE);
        verify(dao, never()).crear(any());
    }

    @Test
    void unEntrenadorQueNoExisteDa400EnIdEntrenador() {
        UUID inexistente = UUID.randomUUID();
        when(entrenadores.buscarPorId(inexistente)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.crear(pedido(inexistente)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("idEntrenador: " + FranjaHorarioServiceImpl.ENTRENADOR_NO_EXISTE);
    }

    @Test
    void crearConUnEntrenadorInactivoDa400() {
        assertThatThrownBy(() -> servicio.crear(pedido(INACTIVO)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("idEntrenador: " + FranjaHorarioServiceImpl.ENTRENADOR_INACTIVO);
        verify(dao, never()).crear(any());
    }

    @Test
    void elGrupoEnSub12YLosDemasErroresSalenEnUnSolo400() {
        UUID sinEntrenador = UUID.randomUUID();
        when(entrenadores.buscarPorId(sinEntrenador)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.crear(
                new FranjaHorarioRequestDTO(SUB12, 7, "19:00", "18:00", "Estadio", sinEntrenador, "mañana")))
                .isInstanceOf(DatosInvalidosException.class)
                .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos())
                        .containsExactly("dia", "grupo", "horaFin", "idEntrenador", "sede"))
                .hasMessageContaining("grupo: solo se asigna en Cachorros y Semillero; la franja es de Sub-12");
    }

    @Test
    void elGrupoEnSemilleroEsValido() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(null)));
        servicio.crear(new FranjaHorarioRequestDTO(SEMILLERO, 4, "16:30", "18:00", "UVA El Paraíso", null, "tarde"));
        verify(dao).crear(any());
    }

    @Test
    void elCruceQueRechazaLaBaseEsUn409ConElMensajeExacto() {
        when(dao.crear(any())).thenThrow(new CruceDeHorarioException(null));
        assertThatThrownBy(() -> servicio.crear(pedido(ACTIVO)))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("Ese entrenador ya tiene otra franja que se cruza con este horario")
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("idEntrenador"));
    }

    // --- Actualizar ---------------------------------------------------------------------------------------------

    @Test
    void putDeUnIdInexistenteConCuerpoInvalidoDa404SinConsultarCategoriaNiEntrenador() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.empty());
        UUID entrenadorCualquiera = UUID.randomUUID();
        FranjaHorarioRequestDTO invalido =
                new FranjaHorarioRequestDTO(UUID.randomUUID(), 9, "7:30", "06:00", "Estadio", entrenadorCualquiera, "noche");

        assertThatThrownBy(() -> servicio.actualizar(ID, invalido))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(FranjaHorarioServiceImpl.NO_EXISTE);
        verify(dao, never()).nombreDeCategoria(any());
        verifyNoInteractions(entrenadores);
        verify(dao, never()).actualizar(any());
    }

    @Test
    void putQueConservaUnEntrenadorInactivoSeAceptaParaCorregirLasHoras() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(INACTIVO)));

        servicio.actualizar(ID, pedido(INACTIVO));

        ArgumentCaptor<FranjaHorario> cambios = ArgumentCaptor.forClass(FranjaHorario.class);
        verify(dao).actualizar(cambios.capture());
        assertThat(cambios.getValue().getId()).isEqualTo(ID);
        assertThat(cambios.getValue().getIdEntrenador()).isEqualTo(INACTIVO);
        assertThat(cambios.getValue().getHoraInicio()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void putQueCambiaAOtroEntrenadorInactivoDa400() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(INACTIVO)));
        assertThatThrownBy(() -> servicio.actualizar(ID, pedido(OTRO_INACTIVO)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("idEntrenador: " + FranjaHorarioServiceImpl.ENTRENADOR_INACTIVO);
        verify(dao, never()).actualizar(any());
    }

    @Test
    void putQuePasaDePorTurnosAUnInactivoDa400() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(null)));
        assertThatThrownBy(() -> servicio.actualizar(ID, pedido(INACTIVO)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("idEntrenador: " + FranjaHorarioServiceImpl.ENTRENADOR_INACTIVO);
    }

    @Test
    void putQueQuitaElEntrenadorQuedaPorTurnos() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(INACTIVO)));
        servicio.actualizar(ID, pedido(null));
        ArgumentCaptor<FranjaHorario> cambios = ArgumentCaptor.forClass(FranjaHorario.class);
        verify(dao).actualizar(cambios.capture());
        assertThat(cambios.getValue().getIdEntrenador()).isNull();
    }

    @Test
    void putConCruceDa409() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(ACTIVO)));
        when(dao.actualizar(any())).thenThrow(new CruceDeHorarioException(null));
        assertThatThrownBy(() -> servicio.actualizar(ID, pedido(ACTIVO)))
                .isInstanceOf(ConflictoException.class)
                .hasMessage(FranjaHorarioServiceImpl.CRUCE);
    }

    @Test
    void putSiLaFranjaDesaparecioAlGuardarDa404() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardada(null)));
        when(dao.actualizar(any())).thenReturn(false);
        assertThatThrownBy(() -> servicio.actualizar(ID, pedido(null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // --- Eliminar -----------------------------------------------------------------------------------------------

    @Test
    void eliminaUnaFranjaExistente() {
        when(dao.eliminar(ID)).thenReturn(true);
        servicio.eliminar(ID);
        verify(dao).eliminar(ID);
    }

    @Test
    void eliminarUnaFranjaQueNoExisteDa404() {
        when(dao.eliminar(ID)).thenReturn(false);
        assertThatThrownBy(() -> servicio.eliminar(ID))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(FranjaHorarioServiceImpl.NO_EXISTE);
    }
}
