package co.edu.futdata.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación en hora de Colombia. Los servicios que necesitan "hoy" (edades, fechas de pago,
 * vencimientos) reciben este {@link Clock} en lugar de llamar a {@code LocalDate.now()}: así no dependen de la
 * zona del servidor y las pruebas pueden usar un reloj fijo.
 */
@Configuration
public class RelojConfig {

    /** El club opera en Medellín: todas las fechas de negocio son en esta zona. */
    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @Bean
    public Clock reloj() {
        return Clock.system(ZONA_COLOMBIA);
    }
}
