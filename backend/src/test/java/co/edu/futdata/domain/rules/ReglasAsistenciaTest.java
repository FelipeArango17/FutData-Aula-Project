package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReglasAsistenciaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);

    @Test
    void mananaEsFuturaYHoyYAyerNo() {
        assertThat(ReglasAsistencia.esFechaFutura(HOY.plusDays(1), HOY)).isTrue();
        assertThat(ReglasAsistencia.esFechaFutura(HOY, HOY)).isFalse();
        assertThat(ReglasAsistencia.esFechaFutura(HOY.minusDays(1), HOY)).isFalse();
        assertThat(ReglasAsistencia.esFechaFutura(HOY.minusYears(1), HOY)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRESENTE", "AUSENTE", "JUSTIFICADO"})
    void aceptaLosTresEstados(String estado) {
        assertThat(ReglasAsistencia.estado(estado)).contains(estado);
    }

    @Test
    void elEstadoSeNormalizaAMayusculasSinEspacios() {
        assertThat(ReglasAsistencia.estado("  presente ")).contains("PRESENTE");
        assertThat(ReglasAsistencia.estado("Justificado")).contains("JUSTIFICADO");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "TARDE", "PRESENTES", "PRESENTE AUSENTE", "null"})
    void rechazaCualquierOtroEstado(String estado) {
        assertThat(ReglasAsistencia.estado(estado)).isEmpty();
    }

    @Test
    void lasObservacionesSeNormalizanComoElMotivoDelHorario() {
        assertThat(ReglasAsistencia.normalizarObservaciones("  Llegó \n\n tarde  ")).isEqualTo("Llegó tarde");
        assertThat(ReglasAsistencia.normalizarObservaciones(" ")).isNull();
        assertThat(ReglasAsistencia.normalizarObservaciones(null)).isNull();
    }

    @Test
    void trescientosCaracteresPasanYTrescientosUnoNo() {
        assertThat(ReglasAsistencia.observacionesDemasiadoLargas("a".repeat(300))).isFalse();
        assertThat(ReglasAsistencia.observacionesDemasiadoLargas("a".repeat(301))).isTrue();
        assertThat(ReglasAsistencia.observacionesDemasiadoLargas(null)).isFalse();
    }

    @Test
    void losEstadosSonLosTresDeLaBase() {
        assertThat(ReglasAsistencia.ESTADOS).containsExactly("PRESENTE", "AUSENTE", "JUSTIFICADO");
        assertThat(ReglasAsistencia.MAX_OBSERVACIONES).isEqualTo(300);
    }
}
