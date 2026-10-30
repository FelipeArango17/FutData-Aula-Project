package co.edu.futdata.datasource.external;

import co.edu.futdata.config.SupabaseProperties;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SupabaseAuthAdmin implements ISupabaseAuthAdmin {
    private final RestClient http;
    private final SupabaseProperties propiedades;

    @Autowired
    public SupabaseAuthAdmin(RestClient.Builder builder, SupabaseProperties propiedades) {
        this.http = builder.build();
        this.propiedades = propiedades;
    }

    @Override
    public UUID invitar(String correo) {
        if (propiedades.url() == null || propiedades.url().isBlank()
                || propiedades.serviceRoleKey() == null || propiedades.serviceRoleKey().isBlank()) {
            throw new IllegalStateException("Supabase Auth no está configurado para enviar invitaciones.");
        }
        AuthUser respuesta = http.post()
                .uri(propiedades.url() + "/auth/v1/invite")
                .header("apikey", propiedades.serviceRoleKey())
                .header("Authorization", "Bearer " + propiedades.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", correo))
                .retrieve()
                .body(AuthUser.class);
        if (respuesta == null || respuesta.id() == null) {
            throw new IllegalStateException("Supabase Auth no devolvió el identificador del usuario.");
        }
        return respuesta.id();
    }

    private record AuthUser(UUID id) { }
}
