package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CronPropertiesTest {

    @Test
    void sinTokenOEnBlancoEstaDeshabilitada() {
        assertThat(new CronProperties(null).habilitado()).isFalse();
        assertThat(new CronProperties("").habilitado()).isFalse();
        assertThat(new CronProperties("   ").habilitado()).isFalse();
    }

    @Test
    void conTokenEstaHabilitadaYNoLoMuestraEnElToString() {
        CronProperties propiedades = new CronProperties("  secreto-largo  ");
        assertThat(propiedades.habilitado()).isTrue();
        assertThat(propiedades.token()).isEqualTo("secreto-largo");
        assertThat(propiedades.toString()).doesNotContain("secreto").contains("****");
    }

    @Test
    void elEnlaceDePagosSale_delFrontendUrl() {
        assertThat(new FrontendProperties("https://club.test/").enlacePagos()).isEqualTo("https://club.test/acudiente/pagos");
    }
}
