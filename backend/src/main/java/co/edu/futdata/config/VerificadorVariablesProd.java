package co.edu.futdata.config;

import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Con el perfil {@code prod}, detiene el arranque si falta alguna variable de entorno obligatoria y dice
 * cuáles. Sin esto Spring deja el texto {@code ${VARIABLE}} tal cual: la base de datos fallaría con un
 * mensaje confuso y CORS aceptaría un origen inválido.
 *
 * <p>Corre antes de crear los beans; está registrado en {@code META-INF/spring.factories}.
 */
public class VerificadorVariablesProd implements EnvironmentPostProcessor, Ordered {

    static final List<String> OBLIGATORIAS = List.of(
            "DB_URL", "DB_USER", "DB_PASSWORD", "CORS_ORIGINS", "N8N_BASE_URL", "N8N_WEBHOOK_TOKEN",
            "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY", "FRONTEND_URL");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
        if (!entorno.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> faltan = OBLIGATORIAS.stream()
                .filter(variable -> !StringUtils.hasText(entorno.getProperty(variable)))
                .toList();
        if (!faltan.isEmpty()) {
            throw new IllegalStateException("Perfil prod: faltan las variables de entorno "
                    + String.join(", ", faltan) + ". Ver backend/.env.example.");
        }
    }

    /** Después de cargar application.yml y backend/.env, para ver todas las fuentes. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
