package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.domain.rules.CiclosDeCobro.Ciclo;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CiclosDeCobroTest {

    private static LocalDate f(String fecha) {
        return LocalDate.parse(fecha);
    }

    @Test
    void elCicloCeroEsLaFechaDeRegistro() {
        assertThat(CiclosDeCobro.fechaDeCobro(f("2026-10-31"), 0)).isEqualTo(f("2026-10-31"));
    }

    @Test
    void registroElTreintaYUnoNoCorreElDiaMesTrasMes() {
        LocalDate ancla = f("2026-10-31");
        assertThat(java.util.stream.IntStream.rangeClosed(0, 4).mapToObj(n -> CiclosDeCobro.fechaDeCobro(ancla, n)))
                .containsExactly(f("2026-10-31"), f("2026-11-30"), f("2026-12-31"), f("2027-01-31"), f("2027-02-28"));
        assertThat(CiclosDeCobro.fechaDeCobro(ancla, 5)).isEqualTo(f("2027-03-31")); // vuelve al 31: no hay corrimiento
    }

    @Test
    void treintaYUnoDeEneroCaeEn28DeFebrero() {
        assertThat(CiclosDeCobro.fechaDeCobro(f("2027-01-31"), 1)).isEqualTo(f("2027-02-28"));
    }

    @Test
    void enBisiestoFebreroTiene29() {
        assertThat(CiclosDeCobro.fechaDeCobro(f("2028-01-31"), 1)).isEqualTo(f("2028-02-29"));
        assertThat(CiclosDeCobro.fechaDeCobro(f("2028-01-29"), 1)).isEqualTo(f("2028-02-29"));
        assertThat(CiclosDeCobro.fechaDeCobro(f("2028-01-30"), 1)).isEqualTo(f("2028-02-29"));
        assertThat(CiclosDeCobro.fechaDeCobro(f("2027-01-29"), 1)).isEqualTo(f("2027-02-28"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-29, 2026-02-28", "2026-01-30, 2026-02-28", "2026-01-31, 2026-02-28",
            "2026-03-31, 2026-04-30", "2026-05-31, 2026-06-30", "2026-08-31, 2026-09-30",
            "2026-12-31, 2027-01-31", "2026-11-30, 2026-12-30", "2026-12-15, 2027-01-15"})
    void elSiguienteCicloDeVariosAnclas(String ancla, String esperado) {
        assertThat(CiclosDeCobro.fechaDeCobro(f(ancla), 1)).isEqualTo(f(esperado));
    }

    @Test
    void registroElTreintaYUnoDeDiciembreCruzaElAno() {
        LocalDate ancla = f("2026-12-31");
        assertThat(CiclosDeCobro.fechaDeCobro(ancla, 1)).isEqualTo(f("2027-01-31"));
        assertThat(CiclosDeCobro.fechaDeCobro(ancla, 2)).isEqualTo(f("2027-02-28"));
        assertThat(CiclosDeCobro.fechaDeCobro(ancla, 12)).isEqualTo(f("2027-12-31"));
    }

    @Test
    void unCicloNegativoEsError() {
        assertThatThrownBy(() -> CiclosDeCobro.fechaDeCobro(f("2026-01-01"), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Ciclo vigente ---

    @Test
    void elLimiteExactoDeHoyMasCincoEntra() {
        // registro el 15 de nov; hoy 10 nov + 5 = 15 nov: el ciclo 0 (15 nov) ya es vigente
        Ciclo c = CiclosDeCobro.cicloVigente(f("2026-10-15"), f("2026-11-10"), 5).orElseThrow();
        assertThat(c).isEqualTo(new Ciclo(1, f("2026-11-15"), f("2026-11-01")));
    }

    @Test
    void unDiaMasAllaDelLimiteNoEntraYSigueElCicloAnterior() {
        // hoy 9 nov + 5 = 14 nov < 15 nov: todavía manda el ciclo de octubre
        Ciclo c = CiclosDeCobro.cicloVigente(f("2026-10-15"), f("2026-11-09"), 5).orElseThrow();
        assertThat(c).isEqualTo(new Ciclo(0, f("2026-10-15"), f("2026-10-01")));
    }

    @Test
    void unRegistradoHaceDosAnosSoloTieneElCicloVigenteSinCiclosViejos() {
        Ciclo c = CiclosDeCobro.cicloVigente(f("2024-11-20"), f("2026-11-18"), 5).orElseThrow();
        assertThat(c.numero()).isEqualTo(24);
        assertThat(c.fechaCobro()).isEqualTo(f("2026-11-20"));
        assertThat(c.periodo()).isEqualTo(f("2026-11-01"));
    }

    @Test
    void registroElTreintaYUnoElCicloVigenteDeFebreroEsElVeintiocho() {
        Ciclo c = CiclosDeCobro.cicloVigente(f("2026-10-31"), f("2027-02-25"), 5).orElseThrow();
        assertThat(c.fechaCobro()).isEqualTo(f("2027-02-28"));
        assertThat(c.periodo()).isEqualTo(f("2027-02-01"));
    }

    @Test
    void enBisiestoElCicloVigenteDeFebreroEsElVeintinueve() {
        Ciclo c = CiclosDeCobro.cicloVigente(f("2027-10-31"), f("2028-02-27"), 5).orElseThrow();
        assertThat(c.fechaCobro()).isEqualTo(f("2028-02-29"));
    }

    @Test
    void elDiaDelRegistroEsElCicloCero() {
        Ciclo c = CiclosDeCobro.cicloVigente(f("2026-11-10"), f("2026-11-10"), 5).orElseThrow();
        assertThat(c).isEqualTo(new Ciclo(0, f("2026-11-10"), f("2026-11-01")));
    }

    @Test
    void unRegistroAFuturoPasadoElLimiteNoTieneCiclo() {
        assertThat(CiclosDeCobro.cicloVigente(f("2026-11-16"), f("2026-11-10"), 5)).isEmpty();
        assertThat(CiclosDeCobro.cicloVigente(f("2026-11-15"), f("2026-11-10"), 5)).isPresent();
    }

    @Test
    void cruzaElAnoAlAnticipar() {
        // registro el 3: hoy 28 dic + 5 = 2 ene, todavía no llega el 3 de enero; con hoy 29 dic (+5 = 3 ene) ya es vigente
        Ciclo c = CiclosDeCobro.cicloVigente(f("2026-11-03"), f("2026-12-28"), 5).orElseThrow();
        assertThat(c.fechaCobro()).isEqualTo(f("2026-12-03"));
        Ciclo siguiente = CiclosDeCobro.cicloVigente(f("2026-10-03"), f("2026-12-29"), 5).orElseThrow();
        assertThat(siguiente.fechaCobro()).isEqualTo(f("2027-01-03"));
        assertThat(siguiente.periodo()).isEqualTo(f("2027-01-01"));
    }

    @Test
    void elVencimientoEsLaFechaDeCobroOHoySiYaPaso() {
        assertThat(CiclosDeCobro.vencimiento(f("2026-11-15"), f("2026-11-10"))).isEqualTo(f("2026-11-15"));
        assertThat(CiclosDeCobro.vencimiento(f("2026-11-10"), f("2026-11-10"))).isEqualTo(f("2026-11-10"));
        assertThat(CiclosDeCobro.vencimiento(f("2026-11-02"), f("2026-11-10"))).isEqualTo(f("2026-11-10"));
    }

    @Test
    void laVentanaDeIngresoIncluyeElSeptimoDia() {
        assertThat(CiclosDeCobro.esIngresoReciente(f("2026-11-03"), f("2026-11-10"), 7)).isTrue();  // hoy - 7
        assertThat(CiclosDeCobro.esIngresoReciente(f("2026-11-02"), f("2026-11-10"), 7)).isFalse(); // hoy - 8
        assertThat(CiclosDeCobro.esIngresoReciente(f("2026-11-10"), f("2026-11-10"), 7)).isTrue();
    }
}
