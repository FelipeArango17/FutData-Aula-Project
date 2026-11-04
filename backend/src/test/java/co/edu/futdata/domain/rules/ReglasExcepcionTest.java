package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReglasExcepcionTest {

    /** Miércoles. */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);

    // --- Fecha pasada ---------------------------------------------------------------------------------------

    @Test
    void ayerEsPasadaYHoyYMananaNo() {
        assertThat(ReglasExcepcion.esFechaPasada(HOY.minusDays(1), HOY)).isTrue();
        assertThat(ReglasExcepcion.esFechaPasada(HOY.minusYears(1), HOY)).isTrue();
        assertThat(ReglasExcepcion.esFechaPasada(HOY, HOY)).isFalse();
        assertThat(ReglasExcepcion.esFechaPasada(HOY.plusDays(1), HOY)).isFalse();
    }

    // --- Día de la semana -----------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"2026-11-02,1", "2026-11-03,2", "2026-11-04,3", "2026-11-05,4", "2026-11-06,5", "2026-11-07,6"})
    void laFechaCoincideConElDiaDeLaFranjaDeLunesASabado(String fecha, int dia) {
        assertThat(ReglasExcepcion.coincideConElDia(LocalDate.parse(fecha), dia)).isTrue();
        assertThat(ReglasExcepcion.coincideConElDia(LocalDate.parse(fecha), dia % 6 + 1)).isFalse();
    }

    @Test
    void elDomingoNoCoincideConNingunDiaDeFranja() {
        for (int dia = 1; dia <= 6; dia++) {
            assertThat(ReglasExcepcion.coincideConElDia(LocalDate.of(2026, 11, 8), dia)).isFalse();
        }
    }

    @Test
    void unExtraEsDeLunesASabadoNuncaDomingo() {
        for (int d = 2; d <= 7; d++) {
            assertThat(ReglasExcepcion.esDiaDeEntrenamiento(LocalDate.of(2026, 11, d))).as("2026-11-0" + d).isTrue();
        }
        assertThat(ReglasExcepcion.esDiaDeEntrenamiento(LocalDate.of(2026, 11, 8))).isFalse();
        assertThat(ReglasExcepcion.esDiaDeEntrenamiento(LocalDate.of(2026, 11, 15))).isFalse();
    }

    // --- Motivo ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n ", " "})
    void unMotivoVacioOEnBlancoEsNull(String motivo) {
        assertThat(ReglasExcepcion.normalizarMotivo(motivo)).isNull();
    }

    @Test
    void elMotivoSeNormalizaSinEspaciosDeMas() {
        assertThat(ReglasExcepcion.normalizarMotivo("  Lluvia fuerte  ")).isEqualTo("Lluvia fuerte");
        assertThat(ReglasExcepcion.normalizarMotivo("Cancha   en \n mantenimiento")).isEqualTo("Cancha en mantenimiento");
        assertThat(ReglasExcepcion.normalizarMotivo("Partido")).isEqualTo("Partido");
    }

    @Test
    void elMotivoTieneComoMaximo200CaracteresYaNormalizado() {
        assertThat(ReglasExcepcion.motivoDemasiadoLargo(null)).isFalse();
        assertThat(ReglasExcepcion.motivoDemasiadoLargo("x".repeat(200))).isFalse();
        assertThat(ReglasExcepcion.motivoDemasiadoLargo("x".repeat(201))).isTrue();
        // Los espacios de los extremos no cuentan: se normaliza antes de medir
        assertThat(ReglasExcepcion.motivoDemasiadoLargo(ReglasExcepcion.normalizarMotivo("  " + "x".repeat(200) + "  ")))
                .isFalse();
    }

    // --- Cruce de horas -------------------------------------------------------------------------------------

    private static boolean cruzan(String inicioA, String finA, String inicioB, String finB) {
        boolean ab = ReglasExcepcion.seCruzan(LocalTime.parse(inicioA), LocalTime.parse(finA),
                LocalTime.parse(inicioB), LocalTime.parse(finB));
        boolean ba = ReglasExcepcion.seCruzan(LocalTime.parse(inicioB), LocalTime.parse(finB),
                LocalTime.parse(inicioA), LocalTime.parse(finA));
        assertThat(ab).as("simétrica").isEqualTo(ba);
        return ab;
    }

    @Test
    void losIntervalosQueSeSolapanSeCruzan() {
        assertThat(cruzan("16:00", "17:30", "17:00", "18:00")).isTrue();
        assertThat(cruzan("16:00", "17:30", "15:00", "16:30")).isTrue();
        assertThat(cruzan("16:00", "17:30", "16:00", "17:30")).isTrue();
        assertThat(cruzan("16:00", "18:00", "16:30", "17:00")).isTrue(); // uno dentro del otro
        assertThat(cruzan("16:00", "17:30", "17:29", "19:00")).isTrue(); // un minuto
    }

    @Test
    void tocarseEnUnExtremoNoEsCruzarse() {
        assertThat(cruzan("16:00", "17:30", "17:30", "19:00")).isFalse();
        assertThat(cruzan("16:00", "17:30", "14:00", "16:00")).isFalse();
    }

    @Test
    void losIntervalosSeparadosNoSeCruzan() {
        assertThat(cruzan("08:00", "09:00", "16:00", "17:30")).isFalse();
    }

    // --- Mensajes -------------------------------------------------------------------------------------------

    @Test
    void losMensajesEstanEnEspanolYElCruceEsElMismoDeLasFranjas() {
        assertThat(ReglasExcepcion.CRUCE).isEqualTo("Ese entrenador ya tiene otra franja que se cruza con este horario");
        assertThat(ReglasExcepcion.YA_CANCELADA).isEqualTo("Esa franja ya está cancelada en esa fecha");
        assertThat(ReglasExcepcion.MOTIVO_LARGO).contains("200");
        assertThat(ReglasExcepcion.SIN_PERMISO).isNotBlank();
        assertThat(ReglasExcepcion.FECHA_PASADA).isNotBlank();
        assertThat(ReglasExcepcion.DIA_NO_COINCIDE).isNotBlank();
        assertThat(ReglasExcepcion.FECHA_FUERA_DE_SEMANA).isNotBlank();
    }
}
