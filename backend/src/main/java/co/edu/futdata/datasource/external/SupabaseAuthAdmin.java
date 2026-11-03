package co.edu.futdata.datasource.external;

import co.edu.futdata.config.SupabaseProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class SupabaseAuthAdmin implements ISupabaseAuthAdmin {
    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthAdmin.class);
    /** {@code ban_duration} para bloquear: 100 años ({@code time.ParseDuration} de Go). */
    static final String BLOQUEO = "876000h";
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

    /** Para las pruebas: recibe un {@code RestClient} ya armado (por ejemplo, con MockRestServiceServer). */
    SupabaseAuthAdmin(RestClient http, SupabaseProperties propiedades) {
        this.http = http;
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

    /**
     * Bloquea ({@code ban_duration = 876000h}, 100 años) o restaura ({@code none}) el acceso con
     * {@code PUT /auth/v1/admin/users/{id}}. Supabase Auth solo acepta {@code PUT} en esa ruta (antes se usaba
     * {@code PATCH}, que no existe, y toda llamada fallaba).
     *
     * @throws CuentaAuthNoEncontradaException {@code 404}: la cuenta no existe en Auth
     * @throws AuthPeticionInvalidaException   {@code 400} o {@code 422}: error de contrato
     * @throws AuthNoDisponibleException       {@code 401}, {@code 403}, {@code 429}, {@code 5xx}, tiempo agotado u otro código
     * @throws IllegalStateException           si falta {@code SUPABASE_URL} o la llave (como antes)
     */
    @Override
    public void cambiarAcceso(UUID idUsuario, boolean activo) {
        validarConfiguracion();
        String operacion = activo ? "restaurar el acceso" : "bloquear el acceso";
        llamar(operacion, () -> http.put()
                .uri(propiedades.url() + "/auth/v1/admin/users/" + idUsuario)
                .header("apikey", propiedades.serviceRoleKey())
                .header("Authorization", "Bearer " + propiedades.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ban_duration", activo ? "none" : BLOQUEO))
                .retrieve()
                .toBodilessEntity(), e -> {
                    int codigo = e.getStatusCode().value();
                    if (codigo == 404) {
                        return new CuentaAuthNoEncontradaException(codigo);
                    }
                    if (codigo == 400 || codigo == 422) {
                        log.error("Supabase Auth rechazó la petición para {} con {}: error de contrato, no de disponibilidad",
                                operacion, codigo);
                        return new AuthPeticionInvalidaException(operacion, codigo);
                    }
                    return null;
                });
    }

    // ---- Enlace para crear la contraseña (regla 9). Nada de esto registra tokens, enlaces ni contraseñas. ----

    @Override
    public UUID crearUsuario(String correo) {
        exigirConfiguracion("crear la cuenta");
        AuthUser respuesta = llamar("crear la cuenta", () -> http.post()
                .uri(propiedades.url() + "/auth/v1/admin/users")
                .header("apikey", propiedades.serviceRoleKey())
                .header("Authorization", "Bearer " + propiedades.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", correo, "email_confirm", true))
                .retrieve()
                .body(AuthUser.class), e -> {
                    if (esCorreoRepetido(e)) return new CorreoYaRegistradoException(e.getStatusCode().value());
                    return null;
                });
        if (respuesta == null || respuesta.id() == null) {
            throw new AuthNoDisponibleException("Supabase Auth no devolvió el id de la cuenta creada", 200);
        }
        return respuesta.id();
    }

    @Override
    public String generarTokenRecuperacion(String correo) {
        exigirConfiguracion("generar el enlace");
        RespuestaEnlace respuesta = llamar("generar el enlace", () -> http.post()
                .uri(propiedades.url() + "/auth/v1/admin/generate_link")
                .header("apikey", propiedades.serviceRoleKey())
                .header("Authorization", "Bearer " + propiedades.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "recovery", "email", correo))
                .retrieve()
                .body(RespuestaEnlace.class), e -> null);
        String token = respuesta == null ? null : respuesta.token();
        if (token == null || token.isBlank()) {
            throw new AuthNoDisponibleException("Supabase Auth no devolvió el token del enlace", 200);
        }
        return token;
    }

    @Override
    public SesionRecuperacion verificarTokenRecuperacion(String tokenHash) {
        exigirConfiguracion("verificar el enlace");
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new TokenRecuperacionInvalidoException(null);
        }
        RespuestaVerificacion respuesta = llamar("verificar el enlace", () -> http.post()
                .uri(propiedades.url() + "/auth/v1/verify")
                .header("apikey", propiedades.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "recovery", "token_hash", tokenHash))
                .retrieve()
                .body(RespuestaVerificacion.class), e -> {
                    int codigo = e.getStatusCode().value();
                    // 401 sería la llave rechazada y 429 el límite de Supabase: no son culpa del enlace
                    if (e.getStatusCode().is4xxClientError() && codigo != 401 && codigo != 429) {
                        return new TokenRecuperacionInvalidoException(codigo);
                    }
                    return null;
                });
        if (respuesta == null || respuesta.user() == null || respuesta.user().id() == null
                || respuesta.accessToken() == null || respuesta.accessToken().isBlank()) {
            throw new AuthNoDisponibleException("Supabase Auth verificó el enlace pero no devolvió la sesión", 200);
        }
        return new SesionRecuperacion(respuesta.user().id(), respuesta.accessToken());
    }

    @Override
    public void fijarContrasena(UUID idUsuario, String contrasena) {
        exigirConfiguracion("fijar la contraseña");
        llamar("fijar la contraseña", () -> http.put()
                .uri(propiedades.url() + "/auth/v1/admin/users/" + idUsuario)
                .header("apikey", propiedades.serviceRoleKey())
                .header("Authorization", "Bearer " + propiedades.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("password", contrasena))
                .retrieve()
                .toBodilessEntity(), e -> null);
    }

    @Override
    public void cerrarSesion(String tokenAcceso) {
        exigirConfiguracion("cerrar la sesión");
        try {
            llamar("cerrar la sesión", () -> http.post()
                    .uri(propiedades.url() + "/auth/v1/logout?scope=local")
                    .header("apikey", propiedades.serviceRoleKey())
                    .header("Authorization", "Bearer " + tokenAcceso)
                    .retrieve()
                    .toBodilessEntity(), e -> {
                        int codigo = e.getStatusCode().value();
                        // La sesión ya no existe o el token ya no sirve: no queda nada que cerrar
                        return codigo == 401 || codigo == 403 || codigo == 404 ? new SesionYaCerrada() : null;
                    });
        } catch (SesionYaCerrada e) {
            log.debug("La sesión del enlace ya estaba cerrada");
        }
    }

    /**
     * Hace la llamada y traduce las fallas. {@code traducir} devuelve la excepción propia de la operación para una
     * respuesta de error, o {@code null} para la traducción general ({@link AuthNoDisponibleException}).
     */
    private <T> T llamar(String operacion, Supplier<T> llamada, Function<RestClientResponseException, RuntimeException> traducir) {
        long inicio = System.nanoTime();
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            RuntimeException propia = traducir.apply(e);
            if (propia != null) {
                log.info("Supabase Auth respondió {} al {} ({} ms)", codigo, operacion, milisegundos(inicio));
                throw propia;
            }
            log.warn("Supabase Auth respondió {} al {} ({} ms)", codigo, operacion, milisegundos(inicio));
            throw new AuthNoDisponibleException("Supabase Auth respondió " + codigo + " al " + operacion, codigo);
        } catch (ResourceAccessException e) {
            log.warn("Supabase Auth no respondió al {} ({} ms)", operacion, milisegundos(inicio));
            throw new AuthNoDisponibleException("No se pudo contactar a Supabase Auth para " + operacion
                    + " (tiempo de espera agotado o error de red)", null);
        } catch (RestClientException e) {
            log.error("Error del cliente HTTP al {}: {} ({} ms)", operacion, e.getClass().getSimpleName(), milisegundos(inicio));
            throw new AuthNoDisponibleException("No se pudo " + operacion + " por un error del cliente HTTP", null);
        }
    }

    /** 422 con {@code error_code=email_exists} (versiones nuevas) o con el mensaje de las anteriores. */
    private static boolean esCorreoRepetido(RestClientResponseException e) {
        int codigo = e.getStatusCode().value();
        if (codigo != 422 && codigo != 400) return false;
        String cuerpo = e.getResponseBodyAsString();
        return cuerpo.contains("email_exists") || cuerpo.contains("already been registered");
    }

    private void exigirConfiguracion(String operacion) {
        if (propiedades.url() == null || propiedades.url().isBlank()
                || propiedades.serviceRoleKey() == null || propiedades.serviceRoleKey().isBlank()) {
            log.warn("Supabase Auth Admin no está configurado para {}", operacion);
            throw new AuthNoDisponibleException("Supabase Auth no está configurado para " + operacion, null);
        }
    }

    private static long milisegundos(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    /** Marca interna de {@link #cerrarSesion}: la sesión ya no existía. No sale de esta clase. */
    private static final class SesionYaCerrada extends RuntimeException {
        SesionYaCerrada() {
            super(null, null, false, false);
        }
    }

    /**
     * Respuesta de {@code generate_link}. La API HTTP devuelve {@code hashed_token} en la raíz; algunas versiones lo
     * anidan en {@code properties}. Se ignora {@code action_link}: el enlace se arma con el del frontend.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespuestaEnlace(@JsonProperty("hashed_token") String hashedToken, Propiedades properties) {
        String token() {
            if (hashedToken != null && !hashedToken.isBlank()) return hashedToken;
            return properties == null ? null : properties.hashedToken();
        }

        @Override
        public String toString() {
            return "RespuestaEnlace[****]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Propiedades(@JsonProperty("hashed_token") String hashedToken) {
        @Override
        public String toString() {
            return "Propiedades[****]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespuestaVerificacion(@JsonProperty("access_token") String accessToken, AuthUser user) {
        @Override
        public String toString() {
            return "RespuestaVerificacion[user=" + user + ", accessToken=****]";
        }
    }

    private void validarConfiguracion() {
        if (propiedades.url() == null || propiedades.url().isBlank()
                || propiedades.serviceRoleKey() == null || propiedades.serviceRoleKey().isBlank()) {
            log.warn("Supabase Auth Admin no está configurado");
            throw new IllegalStateException("Supabase Auth no está configurado.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthUser(UUID id) { }
}
