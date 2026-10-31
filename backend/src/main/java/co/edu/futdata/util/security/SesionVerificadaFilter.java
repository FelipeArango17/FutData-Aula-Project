package co.edu.futdata.util.security;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Después de validar el JWT, en todas las rutas que no son públicas ({@link RutasSin2FA}):
 *
 * <ol>
 *   <li>Sin el claim {@code session_id} (o si no es un UUID): {@code 401}. La verificación se guarda por sesión.</li>
 *   <li>Sin ningún rol en la base de datos (cuenta sin perfil, sin rol o con el acceso revocado): {@code 403}.</li>
 *   <li>Salvo en las rutas del segundo paso ({@link RutasSin2FA.Acceso#CON_TOKEN}), si la sesión del token no
 *       completó el 2FA o ya venció: {@code 403} con código {@code 2FA_REQUERIDA}.</li>
 * </ol>
 *
 * <p>Solo revisa los JWT: otra autenticación solo existe en las pruebas ({@code @WithMockUser}).
 */
@Component
public class SesionVerificadaFilter extends OncePerRequestFilter {
    private static final String MENSAJE = "Debes verificar el código 2FA antes de continuar.";
    private static final String PREFIJO_ROL = "ROLE_";

    private final ISesionVerificadaDAO sesiones;
    private final RespuestasSeguridad respuestas;
    private final ObjectMapper json;
    private final Clock reloj;
    private final List<RequestMatcher> publicas;
    private final List<RequestMatcher> sin2fa;

    public SesionVerificadaFilter(ISesionVerificadaDAO sesiones, RespuestasSeguridad respuestas, ObjectMapper json,
                                  Environment entorno) {
        this.sesiones = sesiones;
        this.respuestas = respuestas;
        this.json = json;
        this.reloj = Clock.systemUTC();
        boolean documentacion = entorno.acceptsProfiles(Profiles.of(RutasSin2FA.PERFIL_DOCUMENTACION));
        this.publicas = List.of(RutasSin2FA.publicas(documentacion));
        this.sin2fa = RutasSin2FA.vigentes(documentacion).stream()
                .filter(ruta -> ruta.acceso() == RutasSin2FA.Acceso.CON_TOKEN)
                .map(RutasSin2FA.Ruta::matcher)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return coincide(publicas, request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacion instanceof JwtAuthenticationToken token)) {
            // Sin token: la autorización responde 401 en las rutas protegidas.
            chain.doFilter(request, response);
            return;
        }
        Optional<UUID> sesion = JwtValidator.idSesion(token.getToken());
        if (sesion.isEmpty()) {
            respuestas.commence(request, response, new InvalidBearerTokenException("El token no trae session_id"));
            return;
        }
        if (autenticacion.getAuthorities().stream().noneMatch(a -> a.getAuthority().startsWith(PREFIJO_ROL))) {
            respuestas.handle(request, response, new AccessDeniedException("La cuenta no tiene roles activos"));
            return;
        }
        if (coincide(sin2fa, request)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<UUID> usuario = JwtValidator.idUsuario(token.getToken());
        if (usuario.isEmpty() || !sesiones.estaVerificada(sesion.get(), usuario.get(), reloj.instant())) {
            requiere2fa(response, request);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean coincide(List<RequestMatcher> rutas, HttpServletRequest request) {
        return rutas.stream().anyMatch(ruta -> ruta.matches(request));
    }

    private void requiere2fa(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), new ErrorResponseDTO(reloj.instant(),
                HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase(), MENSAJE,
                request.getRequestURI(), List.of("codigo"), "2FA_REQUERIDA", null));
    }
}
