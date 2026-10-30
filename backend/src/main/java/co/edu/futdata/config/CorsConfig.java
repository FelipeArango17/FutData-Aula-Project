package co.edu.futdata.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el frontend (React en Vercel; en desarrollo, Vite en {@code http://localhost:5173}).
 *
 * <p>El frontend envía el JWT de Supabase en la cabecera {@code Authorization}. Además se permiten credenciales
 * (cookies) para la cookie HttpOnly de "Recordar este equipo" (HU-002, decisión D16): el frontend debe usar
 * {@code withCredentials: true} en Axios. Con credenciales, los orígenes tienen que ser exactos: nunca {@code *}.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties cors;

    public CorsConfig(CorsProperties cors) {
        this.cors = cors;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(cors.origins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .exposedHeaders("Location")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
