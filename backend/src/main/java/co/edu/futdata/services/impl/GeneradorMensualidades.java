package co.edu.futdata.services.impl;

import co.edu.futdata.services.facade.IPagoService;
import java.time.Clock;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cobros mensuales automáticos: cada día a las 6:00 (hora de Colombia) crea la mensualidad PENDIENTE del mes en curso de cada
 * deportista que no la tenga. Idempotente, así que correrlo todos los días (o dos veces) no duplica nada; el día 1 crea las
 * del mes nuevo y los días siguientes recogen a los deportistas registrados después. Solo existe si
 * {@code futdata.mensualidad.generacion-automatica=true}. Ver {@link IPagoService#generarMensualidades}.
 */
@Component
@ConditionalOnProperty(prefix = "futdata.mensualidad", name = "generacion-automatica", havingValue = "true")
public class GeneradorMensualidades {

    private static final Logger log = LoggerFactory.getLogger(GeneradorMensualidades.class);

    private final IPagoService pagos;
    private final Clock reloj;

    public GeneradorMensualidades(IPagoService pagos, Clock reloj) {
        this.pagos = pagos;
        this.reloj = reloj;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Bogota")
    public void generarDelMes() {
        try {
            var resultado = pagos.generarMensualidades(null, YearMonth.now(reloj));
            log.info("Mensualidades de {}: {} creadas, {} ya existían", resultado.periodo(), resultado.creadas(), resultado.yaExistentes());
        } catch (RuntimeException e) {
            // No debe tumbar el programador de tareas: se reintenta mañana
            log.error("No se pudieron generar las mensualidades: {}", e.getClass().getSimpleName());
        }
    }
}
