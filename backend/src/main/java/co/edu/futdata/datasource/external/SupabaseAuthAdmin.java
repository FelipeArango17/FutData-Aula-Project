package co.edu.futdata.datasource.external;

import co.edu.futdata.config.SupabaseProperties;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class SupabaseAuthAdmin implements ISupabaseAuthAdmin {
    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthAdmin.class);
    private final RestClient http;
    private final SupabaseProperties propiedades;

    @Autowired
    public SupabaseAuthAdmin(RestClient.Builder builder, SupabaseProperties propiedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(fabrica).build();
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

    @Override
    public void eliminar(UUID idUsuario) {
        validarConfiguracion();
        http.delete()
                .uri(propiedades.url() + "/auth/v1/admin/users/" + idUsuario)
                .header("apikey", propiedades.serviceRoleKey())
                .header("Authorization", "Bearer " + propiedades.serviceRoleKey())
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void cambiarAcceso(UUID idUsuario, boolean activo) {
        validarConfiguracion();
        http.patch()
                .uri(propiedades.url() + "/auth/v1/admin/users/" + idUsuario)
                .header("apikey", propiedades.serviceRoleKey())
                .header("Authorization", "Bearer " + propiedades.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ban_duration", activo ? "none" : "876000h"))
                .retrieve()
                .toBodilessEntity();
    }

    private void validarConfiguracion() {
        if (propiedades.url() == null || propiedades.url().isBlank()
                || propiedades.serviceRoleKey() == null || propiedades.serviceRoleKey().isBlank()) {
            log.warn("Supabase Auth Admin no está configurado");
            throw new IllegalStateException("Supabase Auth no está configurado.");
        }
    }

    private record AuthUser(UUID id) { }
}
