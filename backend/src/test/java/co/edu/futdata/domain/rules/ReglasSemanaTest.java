package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReglasSemanaTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    /** "Hoy" en Bogotá con un reloj fijo; las 23:30 locales ya son el día siguiente en UTC. */
    private static LocalDate hoy(String instante) {
        return LocalDate.now(Clock.fixed(Instant.parse(instante), BOGOTA));
    }

    @Test
    void elLunesYElMiercolesYElSabadoSonDeLaSemanaDelLunesDeEsaSemana() {
        assertThat(ReglasSemana.lunesDeLaSemana(hoy("2026-11-02T15:00:00Z"))).isEqualTo(LUNES);
        assertThat(ReglasSemana.lunesDeLaSemana(hoy("2026-11-04T15:00:00Z"))).isEqualTo(LUNES);
        assertThat(ReglasSemana.lunesDeLaSemana(hoy("2026-11-07T15:00:00Z"))).isEqualTo(LUNES);
    }

    @Test
    void elDomingoCuentaLaSemanaSiguiente() {
        assertThat(ReglasSemana.lunesDeLaSemana(hoy("2026-11-08T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void usaLaFechaDeBogotaNoLaDeUtc() {
        // Sábado 23:30 en Bogotá es domingo 04:30 UTC: en Bogotá sigue siendo sábado
        assertThat(ReglasSemana.lunesDeLaSemana(hoy("2026-11-08T04:30:00Z"))).isEqualTo(LUNES);
        // Domingo 23:30 en Bogotá es lunes 04:30 UTC: en Bogotá sigue siendo domingo
        assertThat(ReglasSemana.lunesDeLaSemana(hoy("2026-11-09T04:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @ParameterizedTest
    @CsvSource({"2026-11-02,2026-11-02", "2026-11-03,2026-11-02", "2026-11-07,2026-11-02", "2026-11-08,2026-11-09",
            "2026-11-09,2026-11-09", "2026-12-31,2026-12-28", "2027-01-03,2027-01-04"})
    void fechasExplicitas(String fecha, String lunesEsperado) {
        assertThat(ReglasSemana.lunesDeLaSemana(LocalDate.parse(fecha))).isEqualTo(LocalDate.parse(lunesEsperado));
    }

    @Test
    void elSabadoEsElLunesMasCincoYCadaFranjaCaeEnSuDia() {
        assertThat(ReglasSemana.sabado(LUNES)).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(ReglasSemana.fechaDeLaFranja(LUNES, 1)).isEqualTo(LUNES);
        assertThat(ReglasSemana.fechaDeLaFranja(LUNES, 2)).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(ReglasSemana.fechaDeLaFranja(LUNES, 6)).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void interpretaSoloFechasRealesConElFormatoIso() {
        assertThat(ReglasSemana.fecha("2026-11-04")).contains(LocalDate.of(2026, 11, 4));
        assertThat(ReglasSemana.fecha("2026-02-30")).isEmpty();
        assertThat(ReglasSemana.fecha("04/11/2026")).isEmpty();
        assertThat(ReglasSemana.fecha("2026-11-4")).isEmpty();
        assertThat(ReglasSemana.fecha("hoy")).isEmpty();
        assertThat(ReglasSemana.fecha(null)).isEmpty();
    }
}
