package co.edu.futdata.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Datos generales de la documentación OpenAPI. Swagger UI: {@code /swagger-ui.html};
 * especificación en JSON: {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI futDataOpenApi(ObjectProvider<BuildProperties> build) {
        BuildProperties info = build.getIfAvailable();
        return new OpenAPI().info(new Info()
                .title("FutData API")
                .version(info != null ? info.getVersion() : "desarrollo")
                .description("API REST de FutData, Asociación Deportiva Prado Fútbol Club. "
                        + "Todavía no tiene autenticación (llega con HU-001, HU-002 y HU-005): "
                        + "no debe desplegarse en un entorno público."));
    }
}
