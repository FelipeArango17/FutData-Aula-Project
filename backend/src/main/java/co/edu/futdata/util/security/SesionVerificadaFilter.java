package co.edu.futdata.util.security;

import co.edu.futdata.persistence.dao.ICodigoVerificacionDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import co.edu.futdata.api.dto.ErrorResponseDTO;

/**
 * Impide acceder a la API protegida antes de completar el segundo paso.
 * El perfil y las rutas 2FA son las únicas excepciones necesarias para completar el login.
 */
@Component
public class SesionVerificadaFilter extends OncePerRequestFilter {
    private static final String MENSAJE = "Debes verificar el código 2FA antes de continuar.";
    private final ICodigoVerificacionDAO codigos;
    private final ObjectMapper json;

    public SesionVerificadaFilter(ICodigoVerificacionDAO codigos, ObjectMapper json) {
        this.codigos = codigos;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return ruta.startsWith(request.getContextPath() + "/api/auth/2fa/")
                || ruta.equals(request.getContextPath() + "/api/usuarios/yo")
                || ruta.equals(request.getContextPath() + "/api/health")
                || ruta.startsWith(request.getContextPath() + "/swagger-ui")
                || ruta.startsWith(request.getContextPath() + "/v3/api-docs")
                || ruta.equals(request.getContextPath() + "/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            chain.doFilter(request, response);
            return;
        }
        if (autenticacion.getAuthorities().isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        UUID usuario;
        try {
            usuario = UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            escribir(response, request);
            return;
        }
        if (!codigos.sesionVerificada(usuario, Instant.now())) {
            escribir(response, request);
            return;
        }
        chain.doFilter(request, response);
    }

    private void escribir(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), new ErrorResponseDTO(Instant.now(),
                HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase(), MENSAJE,
                request.getRequestURI(), List.of("codigo"), "2FA_REQUERIDA", null));
    }
}
