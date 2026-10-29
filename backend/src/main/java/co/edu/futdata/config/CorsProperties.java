package co.edu.futdata.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Orígenes del frontend que pueden llamar a la API ({@code futdata.cors.origins}, variable
 * {@code CORS_ORIGINS} separada por comas). Deben ser orígenes exactos, por ejemplo
 * {@code https://futdata.vercel.app}; no usar {@code *}.
 */
@Validated
@ConfigurationProperties(prefix = "futdata.cors")
public record CorsProperties(@NotEmpty List<String> origins) {
}
