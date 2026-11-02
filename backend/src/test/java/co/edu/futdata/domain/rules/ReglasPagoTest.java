package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReglasPagoTest {

    @ParameterizedTest
    @CsvSource({"CARD,TARJETA", "NEQUI,NEQUI", "PSE,PSE", "BANCOLOMBIA_QR,QR", "BANCOLOMBIA_TRANSFER,BOTON_BANCOLOMBIA",
            "DAVIPLATA,WOMPI_OTRO", "PCOL,WOMPI_OTRO", "ALGO_NUEVO,WOMPI_OTRO", "card,TARJETA"})
    void mapeaLosMediosDeWompiYElRestoCaeEnWompiOtro(String tipo, MetodoPago esperado) {
        assertThat(ReglasPago.metodoDesdeWompi(tipo)).isEqualTo(esperado);
    }

    @Test
    void sinMedioCaeEnWompiOtro() {
        assertThat(ReglasPago.metodoDesdeWompi(null)).isEqualTo(MetodoPago.WOMPI_OTRO);
        assertThat(ReglasPago.metodoDesdeWompi("")).isEqualTo(MetodoPago.WOMPI_OTRO);
    }

    @Test
    void laReferenciaEsFdYElIdYSoloTieneCaracteresPermitidos() {
        UUID id = UUID.fromString("7f0c2a52-6a5d-4f0e-9d5e-0b8d2d9c1a11");
        assertThat(ReglasPago.referenciaDe(id)).isEqualTo("FD-7f0c2a52-6a5d-4f0e-9d5e-0b8d2d9c1a11").matches("[A-Za-z0-9_-]+");
    }

    @Test
    void centavos() {
        assertThat(ReglasPago.aCentavos(90_000)).isEqualTo(9_000_000L);
    }

    @Test
    void vencimientoDeLaMensualidad() {
        assertThat(ReglasPago.vencimientoMensualidad(LocalDate.of(2026, 11, 1), 5)).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(ReglasPago.vencimientoMensualidad(LocalDate.of(2027, 2, 1), 31)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(ReglasPago.vencimientoMensualidad(LocalDate.of(2026, 11, 1), 0)).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void conceptoDeLaMensualidad() {
        assertThat(ReglasPago.conceptoMensualidad(LocalDate.of(2026, 11, 1))).isEqualTo("Mensualidad noviembre 2026");
    }
}
