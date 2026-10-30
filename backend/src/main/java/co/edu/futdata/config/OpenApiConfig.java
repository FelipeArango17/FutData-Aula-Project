package co.edu.futdata.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Datos generales de la documentación OpenAPI. Swagger UI: {@code /swagger-ui.html};
 * especificación en JSON: {@code /v3/api-docs}. En el perfil {@code prod} están apagadas.
 *
 * <p>El botón <b>Authorize</b> de Swagger UI recibe el JWT de Supabase (sin la palabra {@code Bearer}).
 */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA = "jwt-supabase";

    @Bean
    public OpenAPI futDataOpenApi(ObjectProvider<BuildProperties> build) {
        BuildProperties info = build.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("FutData API")
                        .version(info != null ? info.getVersion() : "desarrollo")
                        .description("API REST de FutData, Asociación Deportiva Prado Fútbol Club. "
                                + "Todas las rutas, salvo /api/health, exigen el JWT de Supabase Auth en "
                                + "Authorization: Bearer. El segundo paso del inicio de sesión llega con HU-002."))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Token de acceso de Supabase Auth")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
