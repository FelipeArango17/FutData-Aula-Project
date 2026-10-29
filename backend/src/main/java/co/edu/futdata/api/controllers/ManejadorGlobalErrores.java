package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.ComprobanteInvalidoException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Convierte las excepciones en la respuesta JSON uniforme {@link ErrorResponseDTO}.
 *
 * <ul>
 *   <li>{@code 400}: datos de entrada inválidos (Bean Validation, JSON mal formado, parámetros) o
 *       {@link ComprobanteInvalidoException}.</li>
 *   <li>{@code 404}: ruta inexistente o {@link RecursoNoEncontradoException}.</li>
 *   <li>Otros errores de Spring MVC ({@code 405}, {@code 415}...): su código, con un mensaje en español.</li>
 *   <li>{@code 500}: cualquier otra excepción. La traza solo va al log del servidor.</li>
 * </ul>
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);
    private static final String DATOS_INVALIDOS = "Los datos enviados no son válidos";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> cuerpoInvalido(MethodArgumentNotValidException e, HttpServletRequest peticion) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, mensaje.isEmpty() ? DATOS_INVALIDOS : mensaje, peticion);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> parametrosInvalidos(HandlerMethodValidationException e, HttpServletRequest peticion) {
        String mensaje = e.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> resultado.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, mensaje.isEmpty() ? DATOS_INVALIDOS : mensaje, peticion);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> jsonInvalido(HttpMessageNotReadableException e, HttpServletRequest peticion) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición falta o no es un JSON válido", peticion);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> parametroFaltante(MissingServletRequestParameterException e, HttpServletRequest peticion) {
        return responder(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + e.getParameterName() + "'", peticion);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> tipoInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest peticion) {
        return responder(HttpStatus.BAD_REQUEST, "El parámetro '" + e.getName() + "' tiene un valor inválido", peticion);
    }

    @ExceptionHandler(ComprobanteInvalidoException.class)
    public ResponseEntity<ErrorResponseDTO> comprobanteInvalido(ComprobanteInvalidoException e, HttpServletRequest peticion) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage(), peticion);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponseDTO> recursoNoEncontrado(RecursoNoEncontradoException e, HttpServletRequest peticion) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), peticion);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> rutaInexistente(NoResourceFoundException e, HttpServletRequest peticion) {
        return responder(HttpStatus.NOT_FOUND, "No existe la ruta " + peticion.getMethod() + " " + peticion.getRequestURI(), peticion);
    }

    /**
     * Demás errores de Spring MVC que ya traen su código HTTP (405, 406, 415, ResponseStatusException...).
     * Todos implementan {@link ErrorResponse}; heredan de una de estas dos clases.
     */
    @ExceptionHandler({ServletException.class, ErrorResponseException.class})
    public ResponseEntity<ErrorResponseDTO> errorDeSpring(Exception e, HttpServletRequest peticion) {
        if (!(e instanceof ErrorResponse conCodigo)) {
            return errorInterno(e, peticion);
        }
        HttpStatusCode estado = conCodigo.getStatusCode();
        String mensaje = switch (e) {
            case HttpRequestMethodNotSupportedException m -> "El método " + m.getMethod() + " no está permitido en esta ruta";
            case HttpMediaTypeNotSupportedException m -> "Tipo de contenido no soportado; usa application/json";
            case ResponseStatusException r when r.getReason() != null -> r.getReason();
            default -> "No se pudo procesar la petición";
        };
        if (estado.is5xxServerError()) {
            log.error("Error {} en {} {}", estado.value(), peticion.getMethod(), peticion.getRequestURI(), e);
        }
        return responder(estado, mensaje, peticion);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> errorInterno(Exception e, HttpServletRequest peticion) {
        log.error("Error no controlado en {} {}", peticion.getMethod(), peticion.getRequestURI(), e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno. Intenta de nuevo más tarde.", peticion);
    }

    private ResponseEntity<ErrorResponseDTO> responder(HttpStatusCode estado, String mensaje, HttpServletRequest peticion) {
        HttpStatus conocido = HttpStatus.resolve(estado.value());
        String error = conocido != null ? conocido.getReasonPhrase() : String.valueOf(estado.value());
        ErrorResponseDTO cuerpo = new ErrorResponseDTO(Instant.now(), estado.value(), error, mensaje, peticion.getRequestURI());
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
