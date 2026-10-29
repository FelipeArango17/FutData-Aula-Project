package co.edu.futdata.util.reports;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.config.ClubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Valores por defecto de futdata.club (application.yml) y el generador como bean de Spring. */
@SpringBootTest
@TestPropertySource(locations = "classpath:pruebas.properties")
class ComprobanteConfiguracionTest {

    @Autowired
    private ClubProperties club;

    @Autowired
    private IGeneradorComprobante generador;

    @Test
    void losDatosDelClubTienenValorPorDefecto() {
        assertThat(club).isEqualTo(new ClubProperties("Asociación Deportiva Prado Fútbol Club", "811032309-1",
                "Calle 40 Sur 75b-81, San Antonio de Prado, Medellín, Antioquia", "3137317366", "prafofutbolclub@gmail.com"));
    }

    @Test
    void elGeneradorEsElDeOpenPdf() {
        assertThat(generador).isInstanceOf(ComprobantePdfGenerator.class);
    }
}
