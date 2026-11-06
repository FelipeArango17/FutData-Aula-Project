package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.AsistenciaRequestDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.SesionAsistenciaResponseDTO;
import co.edu.futdata.services.facade.IAsistenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistencia a una sesión del horario (bloque 4, versión mínima): la lista de deportistas de una franja en una fecha, o
 * de un extra, con su estado, y pasar lista. Solo el ADMINISTRADOR y el ENTRENADOR dueño de la categoría (o que dirige la
 * franja, como en el horario de la semana); el tesorero y el acudiente reciben {@code 403}. La autorización fina la decide
 * el servicio.
 */
@RestController
@RequestMapping("/api/asistencia")
@Tag(name = "Asistencia", description = "Pasar lista de una sesión del horario (administrador y entrenador de la categoría)")
@ApiResponse(responseCode = "400", description = "Datos inválidos: fecha inválida o futura, ni idFranja ni idExcepcion o los "
        + "dos, estado inválido, deportista repetido o que no está en la lista de la sesión",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "Sin rol permitido, sin 2FA o un entrenador sobre una categoría que no es suya",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "404", description = "No existe la franja o el extra",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "409", description = "La sesión está cancelada ese día, o se cruza con otro entrenamiento ya registrado",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class AsistenciaController {

    static final String ROLES = "hasAnyRole('ADMINISTRADOR', 'ENTRENADOR')";

    private final IAsistenciaService asistencia;

    public AsistenciaController(IAsistenciaService asistencia) {
        this.asistencia = asistencia;
    }

    @GetMapping("/sesion")
    @PreAuthorize(ROLES)
    @Operation(summary = "Lista de una sesión",
            description = "La sesión es una franja en una fecha (idFranja) o un extra (idExcepcion): exactamente uno de los "
                    + "dos. La fecha (AAAA-MM-DD) es hoy o pasada, y para una franja debe caer en su día de la semana; para un "
                    + "extra, ser la suya. Devuelve los deportistas ACTIVOS de la categoría de la sesión (y solo los de su grupo "
                    + "si la sesión lo tiene), por apellido y nombre, con el estado y las observaciones ya guardados "
                    + "(null = sin registrar). Solo lee: no crea nada. La categoría se calcula con la regla de hoy, también "
                    + "para una fecha pasada. puedeRegistrar es true cuando hay 200.")
    @ApiResponse(responseCode = "200", description = "La sesión y su lista de deportistas")
    public SesionAsistenciaResponseDTO consultar(
            @Parameter(description = "AAAA-MM-DD; hoy o pasada") @RequestParam(required = false) String fecha,
            @Parameter(description = "Id de la franja (si no es un extra)") @RequestParam(required = false) String idFranja,
            @Parameter(description = "Id del extra (si no es una franja)") @RequestParam(required = false) String idExcepcion,
            @AuthenticationPrincipal Jwt jwt, Authentication autenticacion) {
        return asistencia.consultar(fecha, idFranja, idExcepcion, Solicitantes.de(jwt, autenticacion));
    }

    @PutMapping("/sesion")
    @PreAuthorize(ROLES)
    @Operation(summary = "Pasar lista de una sesión",
            description = "Body { fecha, idFranja | idExcepcion (exactamente uno), registros: [{ idDeportista, estado, "
                    + "observaciones? }] }. estado: PRESENTE, AUSENTE o JUSTIFICADO; observaciones, hasta 300 caracteres. "
                    + "Guarda por deportista y sesión (upsert) y permite volver a guardar para corregir; registros puede traer "
                    + "solo una parte de la lista. Todo o nada, en una transacción: un registro inválido no deja ninguno. "
                    + "Crea el entrenamiento de la sesión la primera vez. Quien pasó lista queda registrado (el entrenador; "
                    + "nada si fue el administrador). Responde lo mismo que el GET, ya guardado. No se pasa lista de fechas "
                    + "futuras (400).")
    @ApiResponse(responseCode = "200", description = "Guardado: la sesión y su lista con los estados")
    public SesionAsistenciaResponseDTO registrar(@RequestBody(required = false) AsistenciaRequestDTO datos,
                                                 @AuthenticationPrincipal Jwt jwt, Authentication autenticacion) {
        // Sin cuerpo: el servicio responde 400 con los campos que faltan (no un 500)
        return asistencia.registrar(datos, Solicitantes.de(jwt, autenticacion));
    }
}
