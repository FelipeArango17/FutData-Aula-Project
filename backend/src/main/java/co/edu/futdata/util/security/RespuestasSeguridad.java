package co.edu.futdata.util.security;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuestas {@code 401} y {@code 403} de los filtros de seguridad con el mismo JSON que el resto de la API
 * ({@link ErrorResponseDTO}). El motivo exacto (token vencido, firma inválida...) no sale en la respuesta.
 *
 * <p>Los {@code 403} de {@code @PreAuthorize} se lanzan dentro del controlador y los responde
 * {@code ManejadorGlobalErrores}, con el mismo mensaje.
 */
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String SIN_SESION = "Debes iniciar sesión. Si ya la iniciaste, venció: inicia sesión de nuevo.";
    public static final String SIN_PERMISO = "No tienes permiso para hacer esta acción.";

    private final ObjectMapper json;

    public RespuestasSeguridad(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException e)
            throws IOException {
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(peticion, respuesta, HttpStatus.UNAUTHORIZED, SIN_SESION);
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException e)
            throws IOException {
        escribir(peticion, respuesta, HttpStatus.FORBIDDEN, SIN_PERMISO);
    }

    private void escribir(HttpServletRequest peticion, HttpServletResponse respuesta, HttpStatus estado, String mensaje)
            throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), new ErrorResponseDTO(Instant.now(), estado.value(),
                estado.getReasonPhrase(), mensaje, peticion.getRequestURI()));
    }
}
