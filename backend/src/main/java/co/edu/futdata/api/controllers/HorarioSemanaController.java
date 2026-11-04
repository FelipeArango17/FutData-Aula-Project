package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import co.edu.futdata.services.facade.IHorarioSemanaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Horario de la semana (bloque 4B-1): las franjas fijas del club puestas en las fechas de una semana. Lo leen los cuatro roles. */
@RestController
@RequestMapping("/api/horario-semana")
@Tag(name = "Horario de la semana", description = "Sesiones de lunes a sábado de una semana")
@ApiResponse(responseCode = "400", description = "La fecha no es válida",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "Sin rol permitido, sin 2FA, o un acudiente pidiendo otra semana",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class HorarioSemanaController {

    private final IHorarioSemanaService semana;

    public HorarioSemanaController(IHorarioSemanaService semana) {
        this.semana = semana;
    }

    @GetMapping
    @PreAuthorize(FranjaHorarioController.LECTURA)
    @Operation(summary = "Consultar una semana",
            description = "Cualquier día de la semana sirve; sin fecha, la semana en curso en Bogotá (el domingo cuenta "
                    + "la semana siguiente). El acudiente solo puede pedir la semana en curso.")
    public HorarioSemanaResponseDTO consultar(
            @Parameter(description = "AAAA-MM-DD") @RequestParam(required = false) String fecha,
            Authentication autenticacion) {
        boolean esAcudiente = autenticacion.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ACUDIENTE"));
        return semana.consultar(fecha, esAcudiente);
    }
}
