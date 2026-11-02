package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Nunca se mezclan las llaves de un ambiente con la URL del otro, y nada de esto muestra una llave. */
class WompiPropertiesTest {

    private static WompiProperties sandbox(String pub, String prv, String eventos, String integridad) {
        return new WompiProperties(pub, prv, eventos, integridad, WompiProperties.SANDBOX, null);
    }

    @Test
    void sandboxConLlavesDeSandboxEstaConfigurado() {
        assertThat(sandbox("pub_test_a", "prv_test_b", "test_events_c", "test_integrity_d").configurado()).isTrue();
    }

    @Test
    void faltaUnaLlaveNoEstaConfigurado() {
        assertThat(sandbox("pub_test_a", "prv_test_b", "test_events_c", "").configurado()).isFalse();
        assertThat(sandbox(null, null, null, null).configurado()).isFalse();
    }

    @Test
    void llavesDeProduccionConLaUrlDelSandboxNoSeAceptan() {
        assertThat(sandbox("pub_prod_a", "prv_prod_b", "prod_events_c", "prod_integrity_d").configurado()).isFalse();
        assertThat(sandbox("pub_test_a", "prv_test_b", "test_events_c", "prod_integrity_d").configurado()).isFalse();
    }

    @Test
    void llavesDeSandboxConLaUrlDeProduccionNoSeAceptan() {
        var mezcla = new WompiProperties("pub_test_a", "prv_test_b", "test_events_c", "test_integrity_d", WompiProperties.PRODUCCION, null);
        assertThat(mezcla.configurado()).isFalse();
        var prod = new WompiProperties("pub_prod_a", "prv_prod_b", "prod_events_c", "prod_integrity_d", WompiProperties.PRODUCCION + "/", null);
        assertThat(prod.configurado()).isTrue();
    }

    @Test
    void unaUrlDesconocidaNoEstaConfigurada() {
        var rara = new WompiProperties("pub_test_a", "prv_test_b", "test_events_c", "test_integrity_d", "https://otro.example/v1", null);
        assertThat(rara.configurado()).isFalse();
        assertThat(rara.webhookConfigurado()).isFalse();
    }

    @Test
    void elWebhookSoloNecesitaElSecretoDeEventos() {
        assertThat(sandbox(null, null, "test_events_c", null).webhookConfigurado()).isTrue();
        assertThat(sandbox(null, null, "prod_events_c", null).webhookConfigurado()).isFalse();
    }

    @Test
    void toStringNoMuestraLasLlaves() {
        String texto = sandbox("pub_test_AAA", "prv_test_BBB", "test_events_CCC", "test_integrity_DDD").toString();
        assertThat(texto).doesNotContain("AAA", "BBB", "CCC", "DDD");
    }
}
