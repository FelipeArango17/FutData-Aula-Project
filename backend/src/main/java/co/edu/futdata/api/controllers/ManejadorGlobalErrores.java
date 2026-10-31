package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.datasource.external.AlmacenamientoException;
import co.edu.futdata.datasource.external.ArchivoInvalidoException;
import co.edu.futdata.datasource.external.ArchivoNoEncontradoException;
import co.edu.futdata.datasource.external.ArchivoYaExisteException;
import co.edu.futdata.datasource.external.NotificacionException;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.CodigoNoEnviadoException;
import co.edu.futdata.services.facade.CodigoVerificacionException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.ComprobanteInvalidoException;
import co.edu.futdata.util.security.RespuestasSeguridad;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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
 *   <li>{@code 400}: datos de entrada inválidos (Bean Validation, JSON mal formado, parámetros),
 *       {@link DatosInvalidosException} o {@link ComprobanteInvalidoException}.</li>
 *   <li>{@code 403}: {@code @PreAuthorize} rechazó el rol ({@link AccessDeniedException}). Los {@code 401} y los
 *       {@code 403} de los filtros los responde {@link RespuestasSeguridad}, con el mismo formato.</li>
 *   <li>{@code 404}: ruta inexistente o {@link RecursoNoEncontradoException}.</li>
 *   <li>{@code 409}: {@link ConflictoException} (duplicados, reglas que dependen de otros registros) y las
 *       restricciones de la base de datos que el servicio no tradujo ({@link DataIntegrityViolationException}).</li>
 *   <li>Supabase Storage ({@link AlmacenamientoException}): {@code 400} archivo inválido, {@code 404} archivo
 *       inexistente, {@code 409} archivo que ya existe y {@code 503} cuando Storage falla o no está configurado.</li>
 *   <li>n8n ({@link NotificacionException}): {@code 503} si no se pudo enviar; {@code 500} si es un error de
 *       configuración o de contrato (lo debe corregir el equipo, no el usuario).</li>
 *   <li>Otros errores de Spring MVC ({@code 405}, {@code 415}...): su código, con un mensaje en español.</li>
 *   <li>{@code 500}: cualquier otra excepción. La traza solo va al log del servidor.</li>
 * </ul>
 *
 * <p>En los errores de servicios externos y de la base de datos, el detalle (rutas, buckets, nombres de variables,
 * valores repetidos) va solo al log: la respuesta lleva un mensaje genérico.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);
    private static final String DATOS_INVALIDOS = "Los datos enviados no son válidos";
    static final String ALMACENAMIENTO_NO_DISPONIBLE =
            "El almacenamiento de archivos no está disponible en este momento. Intenta de nuevo más tarde.";
    static final String NOTIFICACION_NO_ENVIADA = "No se pudo enviar el correo. Intenta de nuevo más tarde.";
    static final String CHOCA_CON_DATOS =
            "La operación no se puede completar porque choca con otros datos registrados.";
    static final String ERROR_INTERNO = "Ocurrió un error interno. Intenta de nuevo más tarde.";
    /** Solo el nombre de la restricción: el detalle de PostgreSQL trae los valores. */
    private static final Pattern RESTRICCION = Pattern.compile("constraint \"([^\"]+)\"");

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

    /**
     * JSON mal formado o un valor que no se puede convertir al tipo del campo (por ejemplo, una fecha que no es
     * {@code AAAA-MM-DD}). En el segundo caso nombra el campo, sin repetir el valor.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> jsonInvalido(HttpMessageNotReadableException e, HttpServletRequest peticion) {
        if (e.getCause() instanceof JsonMappingException mapeo && !mapeo.getPath().isEmpty()) {
            String campo = mapeo.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            if (!campo.isEmpty()) {
                return responder(HttpStatus.BAD_REQUEST, campo + ": tiene un valor o un formato inválido", List.of(campo), peticion);
            }
        }
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

    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<ErrorResponseDTO> datosInvalidos(DatosInvalidosException e, HttpServletRequest peticion) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage(), e.getCampos(), peticion);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponseDTO> conflicto(ConflictoException e, HttpServletRequest peticion) {
        return responder(HttpStatus.CONFLICT, e.getMessage(), e.getCampos(), peticion);
    }

    @ExceptionHandler(CodigoVerificacionException.class)
    public ResponseEntity<ErrorResponseDTO> codigoVerificacion(CodigoVerificacionException e,
                                                                 HttpServletRequest peticion) {
        return responder(HttpStatus.CONFLICT, e.getMessage(), e.getCampos(), e.getCodigo(), e.getMsRestantes(), peticion);
    }

    @ExceptionHandler(CodigoNoEnviadoException.class)
    public ResponseEntity<ErrorResponseDTO> codigoNoEnviado(CodigoNoEnviadoException e,
                                                             HttpServletRequest peticion) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), peticion);
    }

    @ExceptionHandler(ComprobanteInvalidoException.class)
    public ResponseEntity<ErrorResponseDTO> comprobanteInvalido(ComprobanteInvalidoException e, HttpServletRequest peticion) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage(), peticion);
    }

    /** Sin esto, el {@code 403} de {@code @PreAuthorize} caería en {@link #errorInterno} y respondería {@code 500}. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> sinPermiso(AccessDeniedException e, HttpServletRequest peticion) {
        return responder(HttpStatus.FORBIDDEN, RespuestasSeguridad.SIN_PERMISO, peticion);
    }

    @ExceptionHandler(AlmacenamientoException.class)
    public ResponseEntity<ErrorResponseDTO> almacenamiento(AlmacenamientoException e, HttpServletRequest peticion) {
        return switch (e) {
            case ArchivoInvalidoException invalido -> responder(HttpStatus.BAD_REQUEST, invalido.getMessage(), peticion);
            case ArchivoNoEncontradoException noEncontrado -> {
                log.warn("Archivo no encontrado en {} {}: {}", peticion.getMethod(), peticion.getRequestURI(), noEncontrado.getMessage());
                yield responder(HttpStatus.NOT_FOUND, "No se encontró el archivo.", peticion);
            }
            case ArchivoYaExisteException yaExiste -> {
                log.warn("Archivo repetido en {} {}: {}", peticion.getMethod(), peticion.getRequestURI(), yaExiste.getMessage());
                yield responder(HttpStatus.CONFLICT, "El archivo ya existe y no se puede reemplazar.", peticion);
            }
            default -> {
                log.error("Supabase Storage falló en {} {}: {} ({})", peticion.getMethod(), peticion.getRequestURI(),
                        e.getMessage(), e.getClass().getSimpleName());
                yield responder(HttpStatus.SERVICE_UNAVAILABLE, ALMACENAMIENTO_NO_DISPONIBLE, peticion);
            }
        };
    }

    @ExceptionHandler(NotificacionException.class)
    public ResponseEntity<ErrorResponseDTO> notificacion(NotificacionException e, HttpServletRequest peticion) {
        // Los mensajes de estas excepciones no traen correos ni códigos (los arma N8nClient sin datos personales)
        log.error("Notificación {} no enviada en {} {}: {} ({})", e.getEvento(), peticion.getMethod(),
                peticion.getRequestURI(), e.getMessage(), e.getClass().getSimpleName());
        return e instanceof NotificacionNoEnviadaException
                ? responder(HttpStatus.SERVICE_UNAVAILABLE, NOTIFICACION_NO_ENVIADA, peticion)
                : responder(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, peticion);
    }

    /**
     * Una restricción de la base de datos que el servicio no tradujo (llave foránea, {@code check}, {@code unique}
     * o exclusión). En el log va solo el nombre de la restricción, nunca los valores.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> integridad(DataIntegrityViolationException e, HttpServletRequest peticion) {
        Matcher restriccion = RESTRICCION.matcher(String.valueOf(e.getMostSpecificCause().getMessage()));
        log.warn("Restricción de la base de datos en {} {}: {}", peticion.getMethod(), peticion.getRequestURI(),
                restriccion.find() ? restriccion.group(1) : "(sin nombre)");
        return responder(HttpStatus.CONFLICT, CHOCA_CON_DATOS, peticion);
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
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, peticion);
    }

    private ResponseEntity<ErrorResponseDTO> responder(HttpStatusCode estado, String mensaje, HttpServletRequest peticion) {
        return responder(estado, mensaje, List.of(), peticion);
    }

    private ResponseEntity<ErrorResponseDTO> responder(HttpStatusCode estado, String mensaje, List<String> campos,
                                                       HttpServletRequest peticion) {
        return responder(estado, mensaje, campos, null, null, peticion);
    }

    private ResponseEntity<ErrorResponseDTO> responder(HttpStatusCode estado, String mensaje, List<String> campos,
                                                       String codigo, Long msRestantes, HttpServletRequest peticion) {
        HttpStatus conocido = HttpStatus.resolve(estado.value());
        String error = conocido != null ? conocido.getReasonPhrase() : String.valueOf(estado.value());
        ErrorResponseDTO cuerpo = new ErrorResponseDTO(Instant.now(), estado.value(), error, mensaje,
                peticion.getRequestURI(), campos, codigo, msRestantes);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
