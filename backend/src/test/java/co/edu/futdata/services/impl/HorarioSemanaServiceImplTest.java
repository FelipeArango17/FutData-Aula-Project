package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import co.edu.futdata.api.dto.SesionSemanaResponseDTO;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.SemanaNoPermitidaException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HorarioSemanaServiceImplTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID SEMILLERO = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6c");
    private static final UUID SUB12 = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID LUIS = UUID.fromString("e0000000-0000-0000-0000-00000000000a");

    private IFranjaHorarioDAO dao;

    @BeforeEach
    void preparar() {
        dao = mock(IFranjaHorarioDAO.class);
        // Desordenadas a propósito: el servicio ordena por fecha, hora, categoría oficial y grupo
        when(dao.listar(null, null, null)).thenReturn(List.of(
                franja("Sub-12", SUB12, 2, "16:30", "17:30", "Horizontes", LUIS, "Luis Pérez", false, null),
                franja("Semillero", SEMILLERO, 2, "16:30", "18:00", "UVA El Paraíso", null, null, null, "tarde"),
                franja("Semillero", SEMILLERO, 2, "16:30", "18:00", "UVA El Paraíso", null, null, null, "mañana"),
                franja("Sub-12", SUB12, 6, "08:00", "09:00", "Horizontes", LUIS, "Luis Pérez", true, null),
                franja("Sub-12", SUB12, 1, "19:00", "20:00", "Horizontes", LUIS, "Luis Pérez", true, null)));
    }

    private static FranjaHorario franja(String categoria, UUID idCategoria, int dia, String inicio, String fin,
                                        String sede, UUID idEntrenador, String entrenador, Boolean activo, String grupo) {
        FranjaHorario f = new FranjaHorario();
        f.setId(UUID.randomUUID());
        f.setIdCategoria(idCategoria);
        f.setCategoria(categoria);
        f.setDia(dia);
        f.setHoraInicio(LocalTime.parse(inicio));
        f.setHoraFin(LocalTime.parse(fin));
        f.setSede(sede);
        f.setIdEntrenador(idEntrenador);
        f.setEntrenador(entrenador);
        f.setEntrenadorActivo(activo);
        f.setGrupo(grupo);
        return f;
    }

    private HorarioSemanaServiceImpl servicioEl(String instante) {
        return new HorarioSemanaServiceImpl(dao, Clock.fixed(Instant.parse(instante), BOGOTA));
    }

    private HorarioSemanaServiceImpl miercoles() {
        return servicioEl("2026-11-04T15:00:00Z");
    }

    @Test
    void sinFechaUsaLaSemanaDeHoyEnBogota() {
        HorarioSemanaResponseDTO semana = miercoles().consultar(null, false);

        assertThat(semana.desde()).isEqualTo("2026-11-02");
        assertThat(semana.hasta()).isEqualTo("2026-11-07");
    }

    @Test
    void unaFechaEnBlancoEsComoNoPonerla() {
        assertThat(miercoles().consultar("  ", false).desde()).isEqualTo("2026-11-02");
    }

    @Test
    void cadaFranjaCaeEnLaFechaDeSuDiaYSaleOrdenada() {
        List<SesionSemanaResponseDTO> sesiones = miercoles().consultar(null, false).sesiones();

        assertThat(sesiones).extracting(SesionSemanaResponseDTO::fecha, SesionSemanaResponseDTO::horaInicio,
                        SesionSemanaResponseDTO::categoria, SesionSemanaResponseDTO::grupo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2026-11-02", "19:00", "Sub-12", null),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "Semillero", "mañana"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "Semillero", "tarde"),
                        org.assertj.core.groups.Tuple.tuple("2026-11-03", "16:30", "Sub-12", null),
                        org.assertj.core.groups.Tuple.tuple("2026-11-07", "08:00", "Sub-12", null));
    }

    @Test
    void laSesionTraeLosCamposDelContratoDel4b1() {
        SesionSemanaResponseDTO s = miercoles().consultar(null, false).sesiones().get(3);

        assertThat(s.tipo()).isEqualTo("FRANJA");
        assertThat(s.estado()).isEqualTo("ACTIVO");
        assertThat(s.idFranja()).isNotNull();
        assertThat(s.idExcepcion()).isNull();
        assertThat(s.motivo()).isNull();
        assertThat(s.puedeGestionar()).isFalse();
        assertThat(s.dia()).isEqualTo(2);
        assertThat(s.horaFin()).isEqualTo("17:30");
        assertThat(s.idCategoria()).isEqualTo(SUB12);
        assertThat(s.sede()).isEqualTo("Horizontes");
        assertThat(s.idEntrenador()).isEqualTo(LUIS);
        assertThat(s.entrenador()).isEqualTo("Luis Pérez");
        assertThat(s.entrenadorActivo()).isFalse();
    }

    @Test
    void unaFranjaSinEntrenadorSaleConEntrenadorNull() {
        SesionSemanaResponseDTO s = miercoles().consultar(null, false).sesiones().get(1);

        assertThat(s.idEntrenador()).isNull();
        assertThat(s.entrenador()).isNull();
        assertThat(s.entrenadorActivo()).isNull();
    }

    @Test
    void cualquierDiaDeLaSemanaPedidaDaLaMismaSemanaYElDomingoLaSiguiente() {
        assertThat(miercoles().consultar("2026-11-07", false).desde()).isEqualTo("2026-11-02");
        assertThat(miercoles().consultar("2026-11-08", false).desde()).isEqualTo("2026-11-09");
        assertThat(miercoles().consultar("2026-11-09", false).hasta()).isEqualTo("2026-11-14");
    }

    @Test
    void elDomingoSinFechaMuestraLaSemanaQueEmpiezaManana() {
        HorarioSemanaResponseDTO semana = servicioEl("2026-11-08T15:00:00Z").consultar(null, false);

        assertThat(semana.desde()).isEqualTo("2026-11-09");
        assertThat(semana.sesiones().get(0).fecha()).isEqualTo("2026-11-09");
    }

    @Test
    void fechaInvalidaEsUn400ConElCampoFecha() {
        for (String mala : List.of("hoy", "2026-13-01", "2026-02-30", "04/11/2026")) {
            assertThatThrownBy(() -> miercoles().consultar(mala, false))
                    .isInstanceOf(DatosInvalidosException.class)
                    .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos()).containsExactly("fecha"))
                    .hasMessageContaining("AAAA-MM-DD");
        }
    }

    @Test
    void elAcudientePuedePedirLaSemanaEnCursoConOSinFecha() {
        assertThat(miercoles().consultar(null, true).desde()).isEqualTo("2026-11-02");
        assertThat(miercoles().consultar("2026-11-02", true).desde()).isEqualTo("2026-11-02");
        assertThat(miercoles().consultar("2026-11-07", true).desde()).isEqualTo("2026-11-02");
    }

    @Test
    void elAcudienteNoPuedePedirOtraSemana() {
        for (String otra : List.of("2026-10-31", "2026-11-09", "2026-11-08")) {
            assertThatThrownBy(() -> miercoles().consultar(otra, true))
                    .isInstanceOf(SemanaNoPermitidaException.class)
                    .hasMessage("Solo puedes consultar la semana en curso");
        }
    }

    @Test
    void elDomingoLaSemanaEnCursoDelAcudienteEsLaSiguiente() {
        HorarioSemanaServiceImpl domingo = servicioEl("2026-11-08T15:00:00Z");

        assertThat(domingo.consultar("2026-11-09", true).desde()).isEqualTo("2026-11-09");
        assertThat(domingo.consultar("2026-11-08", true).desde()).isEqualTo("2026-11-09");
        assertThatThrownBy(() -> domingo.consultar("2026-11-02", true)).isInstanceOf(SemanaNoPermitidaException.class);
    }

    @Test
    void losDemasRolesPidenCualquierSemana() {
        assertThat(miercoles().consultar("2027-03-01", false).desde()).isEqualTo("2027-03-01");
        assertThat(miercoles().consultar("2020-01-01", false).desde()).isEqualTo("2019-12-30");
    }
}
