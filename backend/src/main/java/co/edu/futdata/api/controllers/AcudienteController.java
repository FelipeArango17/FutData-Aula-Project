package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.MiJugadorFichaResponseDTO;
import co.edu.futdata.api.dto.MiJugadorResponseDTO;
import co.edu.futdata.services.facade.IDeportistaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que el acudiente ve de sus propios jugadores. El acudiente sale siempre del token ({@code sub}): ningún
 * parámetro, cuerpo ni cabecera puede indicar de quién se trata. Un deportista de otro acudiente responde igual que
 * uno que no existe (404, no 403).
 */
@RestController
@RequestMapping("/api/acudientes/me")
@Tag(name = "Acudientes", description = "Mis jugadores (solo el rol ACUDIENTE)")
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ACUDIENTE o no completó el 2FA",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class AcudienteController {

    private final IDeportistaService deportistas;

    public AcudienteController(IDeportistaService deportistas) {
        this.deportistas = deportistas;
    }

    @GetMapping("/deportistas")
    @PreAuthorize("hasRole('ACUDIENTE')")
    @Operation(summary = "Mis jugadores",
            description = "Los deportistas vinculados al acudiente del token, ordenados por apellido y nombre, sin paginar, "
                    + "con la foto como URL firmada temporal y la categoría calculada. No trae documento, teléfono ni "
                    + "dirección. Lista vacía si no tiene jugadores vinculados.")
    public List<MiJugadorResponseDTO> misJugadores(@AuthenticationPrincipal Jwt jwt) {
        return deportistas.misJugadores(usuario(jwt));
    }

    @GetMapping("/deportistas/{id}")
    @PreAuthorize("hasRole('ACUDIENTE')")
    @Operation(summary = "La ficha de uno de mis jugadores",
            description = "Incluye documento, teléfono, dirección, estadoInscripcion (COMPLETA o PENDIENTE) y los "
                    + "documentos obligatorios que aún faltan por aprobar.")
    @ApiResponse(responseCode = "400", description = "El id no es un UUID",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "No existe o no es de un jugador del acudiente (el mismo mensaje en los dos casos)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public MiJugadorFichaResponseDTO miJugador(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return deportistas.miJugador(usuario(jwt), id);
    }

    private static UUID usuario(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
