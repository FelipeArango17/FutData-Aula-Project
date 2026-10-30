package co.edu.futdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del backend de FutData.
 *
 * <p>{@code @ConfigurationPropertiesScan} registra las propiedades tipadas de {@code config}
 * ({@code futdata.cors}, {@code futdata.n8n}, {@code futdata.club} y {@code futdata.supabase}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FutDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(FutDataApplication.class, args);
    }
}
