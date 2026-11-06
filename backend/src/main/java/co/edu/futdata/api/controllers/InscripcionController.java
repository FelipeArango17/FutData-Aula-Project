package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.InscripcionRequestDTO;
import co.edu.futdata.api.dto.InscripcionResponseDTO;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.IInscripcionService;
import co.edu.futdata.services.facade.InscripcionResultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscripción única (bloque 2B): deportista, acudiente (creado o reutilizado por correo), vínculo y cuenta de acceso
 * del acudiente en un solo {@code POST}. Solo el ADMINISTRADOR. La foto y los documentos se suben después con sus
 * rutas de siempre.
 */
@RestController
@Tag(name = "Inscripciones", description = "Inscripción única de un deportista con su acudiente (administrador)")
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ADMINISTRADOR, o no completó el 2FA",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class InscripcionController {

    private final IInscripcionService inscripciones;
    private final IDeportistaService deportistas;

    public InscripcionController(IInscripcionService inscripciones, IDeportistaService deportistas) {
        this.inscripciones = inscripciones;
        this.deportistas = deportistas;
    }

    @PostMapping(value = "/api/inscripciones", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Inscribir un deportista con su acudiente",
            description = "Crea el deportista (activo, con la inscripción PENDIENTE hasta que se aprueben los 4 documentos "
                    + "obligatorios), crea o reutiliza al acudiente por correo (sin cambiar sus datos) y los vincula con "
                    + "el parentesco, todo o nada. Si el correo no tiene cuenta, se le crea una sin contraseña y, después "
                    + "de guardar, se le envía el enlace para crearla: correoEnviado es true, false (la inscripción quedó "
                    + "hecha igual) o null (no había cuenta nueva). Los errores llevan los campos prefijados: "
                    + "deportista.<campo> y acudiente.<campo>.")
    @ApiResponse(responseCode = "201", description = "Inscripción hecha (Cache-Control: no-store)")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, todos los errores juntos (campos prefijados), "
            + "o cuerpo ausente (campos: [\"cuerpo\"])",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Documento del deportista repetido (deportista.documento), correo "
            + "de una cuenta con otro rol o de otra inscripción simultánea (acudiente.correo)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Supabase Auth no está disponible; no se guardó nada",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<InscripcionResponseDTO> inscribir(@RequestBody(required = false) InscripcionRequestDTO datos,
                                                            @AuthenticationPrincipal Jwt jwt,
                                                            Authentication autenticacion) {
        InscripcionResultado r = inscripciones.inscribir(datos == null ? null : datos.aEntrada(),
                Solicitantes.de(jwt, autenticacion));
        // El deportista con la misma forma que POST /api/deportistas (la de la vista del administrador)
        var deportista = deportistas.consultar(r.deportista().getId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(InscripcionResponseDTO.de(r, deportista));
    }
}
