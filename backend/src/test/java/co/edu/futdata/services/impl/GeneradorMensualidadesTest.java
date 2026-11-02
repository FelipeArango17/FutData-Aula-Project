package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.ResultadoGeneracionDTO;
import co.edu.futdata.services.facade.IPagoService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** El proceso diario solo existe si se activa por propiedad, y un fallo no lo tumba. */
class GeneradorMensualidadesTest {

    private final IPagoService pagos = mock(IPagoService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("America/Bogota"));
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(IPagoService.class, () -> pagos).withBean(Clock.class, () -> reloj)
            .withUserConfiguration(GeneradorMensualidades.class);

    @Test
    void apagadoPorDefecto() {
        contexto.run(c -> assertThat(c).doesNotHaveBean(GeneradorMensualidades.class));
        contexto.withPropertyValues("futdata.mensualidad.generacion-automatica=false")
                .run(c -> assertThat(c).doesNotHaveBean(GeneradorMensualidades.class));
    }

    @Test
    void conLaPropiedadActivaGeneraLasDelMesEnCurso() {
        when(pagos.generarMensualidades(any(), any())).thenReturn(new ResultadoGeneracionDTO("2026-11", 3, 0));
        contexto.withPropertyValues("futdata.mensualidad.generacion-automatica=true").run(c -> {
            assertThat(c).hasSingleBean(GeneradorMensualidades.class);
            c.getBean(GeneradorMensualidades.class).generarDelMes();
            verify(pagos).generarMensualidades(null, YearMonth.of(2026, 11));
        });
    }

    @Test
    void unFalloNoTumbaElProceso() {
        when(pagos.generarMensualidades(any(), any())).thenThrow(new IllegalStateException("base caída"));
        contexto.withPropertyValues("futdata.mensualidad.generacion-automatica=true")
                .run(c -> c.getBean(GeneradorMensualidades.class).generarDelMes());
    }
}
