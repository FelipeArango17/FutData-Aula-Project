package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.AccionSemanaResponseDTO;
import co.edu.futdata.api.dto.CancelacionRequestDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.ExtraRequestDTO;
import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import co.edu.futdata.services.facade.IHorarioSemanaService;
import co.edu.futdata.services.facade.Solicitante;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Horario de la semana (bloque 4B): las franjas fijas del club puestas en las fechas de una semana, con sus
 * cancelaciones y sus extras. Lo leen los cuatro roles; cancelar, deshacer y crear o eliminar extras, solo el
 * ADMINISTRADOR y el ENTRENADOR dueño de la categoría.
 */
@RestController
@RequestMapping("/api/horario-semana")
@Tag(name = "Horario de la semana", description = "Sesiones de lunes a sábado de una semana, sus cancelaciones y sus extras")
@ApiResponse(responseCode = "400", description = "Datos inválidos o fecha no permitida",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "Sin rol permitido, sin 2FA, un acudiente pidiendo otra semana o un "
        + "entrenador sobre una categoría que no es suya",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class HorarioSemanaController {

    static final String ESCRITURA = "hasAnyRole('ADMINISTRADOR', 'ENTRENADOR')";

    /** Significado de {@code avisoEnviado}, común a los cuatro endpoints de escritura. */
    static final String AVISO = " Después de confirmar el cambio se avisa por correo (evento CAMBIO_ENTRENAMIENTO de n8n) a los "
            + "acudientes de la categoría. avisoEnviado: true = n8n aceptó el aviso (no garantiza que el correo llegue); "
            + "false = no se pudo avisar, pero la operación SÍ quedó hecha y la respuesta sigue siendo exitosa; "
            + "null = la categoría no tiene acudientes con correo a quienes avisar (no se llama a n8n).";

    private final IHorarioSemanaService semana;

    public HorarioSemanaController(IHorarioSemanaService semana) {
        this.semana = semana;
    }

    @GetMapping
    @PreAuthorize(FranjaHorarioController.LECTURA)
    @Operation(summary = "Consultar una semana",
            description = "Cualquier día de la semana sirve; sin fecha, la semana en curso en Bogotá (el domingo cuenta "
                    + "la semana siguiente). El acudiente solo puede pedir la semana en curso. Una franja cancelada ese "
                    + "día sale con estado CANCELADO, su idExcepcion y su motivo, y los extras salen con tipo EXTRA. "
                    + "puedeGestionar: el administrador, en todo; el entrenador, en las franjas de sus categorías o que él "
                    + "dirige y en los extras de sus categorías (no por ser el entrenador asignado); los demás roles, nunca. "
                    + "En una fecha anterior a hoy siempre es false.")
    @ApiResponse(responseCode = "200", description = "La semana, de lunes a sábado")
    public HorarioSemanaResponseDTO consultar(
            @Parameter(description = "AAAA-MM-DD") @RequestParam(required = false) String fecha,
            @AuthenticationPrincipal Jwt jwt, Authentication autenticacion) {
        return semana.consultar(fecha, solicitante(jwt, autenticacion));
    }

    @PostMapping("/cancelaciones")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Cancelar una franja en una fecha",
            description = "ADMINISTRADOR en cualquier categoría; ENTRENADOR solo en franjas de sus categorías o que él "
                    + "dirige. La fecha no puede ser pasada (hoy sí) y debe caer en el día de la semana de la franja. "
                    + "Una franja \"por turnos\" la cancela el dueño de su categoría. Avisa a los acudientes (CANCELADO)."
                    + AVISO)
    @ApiResponse(responseCode = "201", description = "Cancelada: la sesión resultante (estado CANCELADO)")
    @ApiResponse(responseCode = "404", description = "No existe la franja",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Esa franja ya está cancelada en esa fecha",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<AccionSemanaResponseDTO> cancelar(@RequestBody CancelacionRequestDTO datos,
                                                            @AuthenticationPrincipal Jwt jwt,
                                                            Authentication autenticacion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(semana.cancelar(datos, solicitante(jwt, autenticacion)));
    }

    @DeleteMapping("/cancelaciones/{idExcepcion}")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Deshacer una cancelación",
            description = "Mismos roles y permisos que al cancelar. No se deshace una cancelación de una fecha pasada. "
                    + "Responde la franja otra vez ACTIVA ese día. Avisa a los acudientes como un entrenamiento nuevo (CREADO)."
                    + AVISO)
    @ApiResponse(responseCode = "200", description = "Deshecha: la sesión resultante (estado ACTIVO)")
    @ApiResponse(responseCode = "404", description = "No existe o no es una cancelación",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public AccionSemanaResponseDTO deshacerCancelacion(@PathVariable UUID idExcepcion, @AuthenticationPrincipal Jwt jwt,
                                                       Authentication autenticacion) {
        return semana.deshacerCancelacion(idExcepcion, solicitante(jwt, autenticacion));
    }

    @PostMapping("/extras")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Crear un entrenamiento extra",
            description = "Un entrenamiento adicional a las franjas fijas, de lunes a sábado y no en el pasado (hoy sí). "
                    + "ADMINISTRADOR en cualquier categoría; ENTRENADOR solo en las suyas. idEntrenador null = \"por turnos\"; "
                    + "si viene debe existir y estar activo. Un extra puede quedar fuera de las franjas fijas, o en el horario "
                    + "de una franja cancelada ese día. El cruce del entrenador con una franja no cancelada o con otro extra "
                    + "da 409. Avisa a los acudientes (CREADO)." + AVISO)
    @ApiResponse(responseCode = "201", description = "Creado: la sesión resultante (tipo EXTRA, estado ACTIVO)")
    @ApiResponse(responseCode = "404", description = "No existe la categoría",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Ese entrenador ya tiene otra franja que se cruza con este horario",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<AccionSemanaResponseDTO> crearExtra(@RequestBody ExtraRequestDTO datos,
                                                              @AuthenticationPrincipal Jwt jwt,
                                                              Authentication autenticacion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(semana.crearExtra(datos, solicitante(jwt, autenticacion)));
    }

    @DeleteMapping("/extras/{idExcepcion}")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Eliminar un entrenamiento extra",
            description = "Mismos roles que al crearlo; el entrenador solo en extras de sus categorías (ser el entrenador "
                    + "asignado no basta). No se elimina un extra de una fecha pasada. Responde sesion null. Avisa a los "
                    + "acudientes (CANCELADO, con el motivo del extra)." + AVISO)
    @ApiResponse(responseCode = "200", description = "Eliminado: sesion es null")
    @ApiResponse(responseCode = "404", description = "No existe o no es un extra",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public AccionSemanaResponseDTO eliminarExtra(@PathVariable UUID idExcepcion, @AuthenticationPrincipal Jwt jwt,
                                                 Authentication autenticacion) {
        return semana.eliminarExtra(idExcepcion, solicitante(jwt, autenticacion));
    }

    /** El {@code sub} del token y su rol (si hubiera varios, el de más permisos). */
    private static Solicitante solicitante(Jwt jwt, Authentication autenticacion) {
        return Solicitantes.de(jwt, autenticacion);
    }
}
