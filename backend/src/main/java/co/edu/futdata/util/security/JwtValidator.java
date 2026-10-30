package co.edu.futdata.util.security;

import co.edu.futdata.persistence.dao.IUsuarioDAO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

/**
 * Valida el JWT de Supabase Auth y lo convierte en el usuario con sus roles ({@code JwtValidator} en el diagrama
 * de clases de autenticación del PPI, Fig. 4.7).
 *
 * <ul>
 *   <li>{@link #decodificador}: comprueba la firma con las llaves públicas de Supabase
 *       ({@code {SUPABASE_URL}/auth/v1/.well-known/jwks.json}), el vencimiento, el emisor
 *       ({@code {SUPABASE_URL}/auth/v1}) y la audiencia ({@code authenticated}). Acepta ES256 y RS256, las firmas
 *       asimétricas de Supabase. Un proyecto con el secreto compartido antiguo (HS256) no tiene llaves públicas:
 *       hay que migrarlo a llaves de firma (JWT Signing Keys) en el panel de Supabase.</li>
 *   <li>{@link #convert}: el {@code sub} del token es {@code usuario.id_usuario}; sus roles salen de la base de datos
 *       en cada petición ({@code ROLE_ADMINISTRADOR}...), así un cambio de rol o una cuenta desactivada surte efecto
 *       sin esperar a que venza el token. El PPI dibuja un solo rol ({@code rolDe}); el modelo de datos permite
 *       varios ({@code usuario_rol}) y aquí se cargan todos.</li>
 * </ul>
 */
public class JwtValidator implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Audiencia de los tokens de usuarios que iniciaron sesión en Supabase Auth. */
    public static final String AUDIENCIA = "authenticated";

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final IUsuarioDAO usuarios;

    public JwtValidator(IUsuarioDAO usuarios) {
        this.usuarios = usuarios;
    }

    /**
     * Decodificador de los tokens de Supabase. Sin {@code supabaseUrl} (perfil local sin Supabase) la aplicación
     * arranca, pero todo token se rechaza con {@code 401}.
     *
     * @param supabaseUrl URL del proyecto ({@code SUPABASE_URL}), por ejemplo {@code https://abcd.supabase.co}
     */
    public static JwtDecoder decodificador(String supabaseUrl) {
        if (!StringUtils.hasText(supabaseUrl)) {
            log.warn("Sin SUPABASE_URL: no se puede validar ningún JWT y las rutas protegidas responden 401");
            return token -> {
                throw new BadJwtException("No se puede validar el token: falta configurar SUPABASE_URL");
            };
        }
        String base = supabaseUrl.strip().replaceAll("/+$", "");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(base + "/auth/v1/.well-known/jwks.json")
                // Por defecto Spring solo acepta RS256; Supabase firma con ES256 en los proyectos nuevos
                .jwsAlgorithms(algoritmos -> {
                    algoritmos.add(SignatureAlgorithm.ES256);
                    algoritmos.add(SignatureAlgorithm.RS256);
                })
                .build();
        decoder.setJwtValidator(validaciones(base + "/auth/v1"));
        return decoder;
    }

    /** Vencimiento ({@code exp}, {@code nbf}), emisor y audiencia. Público para probarlo sin firmar tokens. */
    public static OAuth2TokenValidator<Jwt> validaciones(String emisor) {
        OAuth2TokenValidator<Jwt> audiencia = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCIA)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "El token no es de un usuario autenticado", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(emisor), audiencia);
    }

    /** El token ya validado, con los roles del usuario. Sin usuario o con la cuenta inactiva, sin roles. */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> roles = idUsuario(jwt.getSubject())
                .map(usuarios::rolesActivos)
                .orElse(List.of())
                .stream()
                .<GrantedAuthority>map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
        return new JwtAuthenticationToken(jwt, roles, jwt.getSubject());
    }

    private static Optional<UUID> idUsuario(String sub) {
        try {
            return Optional.of(UUID.fromString(sub));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
