package co.edu.futdata.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita {@code @Scheduled} (cobros mensuales automáticos). Sin tareas activas no hace nada. */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
