package co.edu.futdata.util.security;

import co.edu.futdata.config.SupabaseProperties;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;

/**
 * Seguridad de la API (RF008, RNF001; HU-001 y HU-005).
 *
 * <ul>
 *   <li>Cada petición trae el JWT de Supabase Auth en {@code Authorization: Bearer ...}. Lo valida
 *       {@link JwtValidator}. Sin token o con uno inválido: {@code 401}.</li>
 *   <li>Públicas: {@code GET /api/health}, la documentación OpenAPI (en el perfil {@code prod} está apagada,
 *       ver {@code application.yml}) y las peticiones {@code OPTIONS} de CORS. Todo lo demás exige sesión.</li>
 *   <li>Los roles se exigen en cada controlador con {@code @PreAuthorize} (por ejemplo, entrenadores solo para
 *       {@code ADMINISTRADOR}). Sin el rol: {@code 403}.</li>
 *   <li>Sin estado: no hay sesión de servidor ni cookie de sesión, así que no aplica la protección CSRF. La cookie
 *       de "Recordar este equipo" (HU-002) solo identifica el equipo y siempre va junto con el JWT.</li>
 * </ul>
 *
 * <p>Las rutas del segundo paso ({@code /api/auth/2fa/**}) exigen el JWT pero ningún rol: el usuario sale del token.
 *
 * <p>Un filtro adicional exige que la sesión haya completado el segundo paso en todas las rutas protegidas,
 * salvo {@code /api/auth/2fa/**} y {@code /api/usuarios/yo}, que son necesarias para completar el ingreso.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, JwtValidator validador, RespuestasSeguridad respuestas,
                                                 SesionVerificadaFilter sesionVerificada)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(recurso -> recurso
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(validador))
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .addFilterAfter(sesionVerificada, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SupabaseProperties supabase) {
        return JwtValidator.decodificador(supabase.url());
    }

    @Bean
    public JwtValidator jwtValidator(IUsuarioDAO usuarios) {
        return new JwtValidator(usuarios);
    }

    @Bean
    public RespuestasSeguridad respuestasSeguridad(ObjectMapper json) {
        return new RespuestasSeguridad(json);
    }
}
