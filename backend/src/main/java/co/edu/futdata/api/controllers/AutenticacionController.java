package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.api.dto.VerificarCodigoRequestDTO;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Segundo paso del inicio de sesión (HU-002). Exige el JWT de Supabase del primer paso, con cualquier rol: el
 * usuario es el {@code sub} del token y el correo no viaja en la petición.
 */
@RestController
@RequestMapping("/api/auth/2fa")
@Tag(name = "Autenticación", description = "Verificación de identidad por código")
@ApiResponse(responseCode = "400", description = "Datos inválidos",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin el JWT del primer paso, o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class AutenticacionController {
    private final ICodigoVerificacionService codigos;

    public AutenticacionController(ICodigoVerificacionService codigos) {
        this.codigos = codigos;
    }

    @PostMapping("/codigo")
    @Operation(summary = "Generar y enviar código de verificación al correo de la cuenta")
    public ResponseEntity<Codigo2FAResponseDTO> solicitar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(codigos.solicitar(usuario(jwt)));
    }

    @PostMapping("/reenviar")
    @Operation(summary = "Reenviar código respetando el límite de un minuto")
    public Codigo2FAResponseDTO reenviar(@AuthenticationPrincipal Jwt jwt) {
        return codigos.reenviar(usuario(jwt));
    }

    @PostMapping("/verificar")
    @Operation(summary = "Verificar código de seis dígitos")
    public UsuarioResponseDTO verificar(@AuthenticationPrincipal Jwt jwt, @RequestBody VerificarCodigoRequestDTO datos) {
        return codigos.verificar(usuario(jwt), datos == null ? null : datos.codigo());
    }

    /** El {@code sub} de los tokens de Supabase es el UUID de la cuenta ({@code usuario.id_usuario}). */
    private static UUID usuario(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RecursoNoEncontradoException("La cuenta no está registrada en FutData.");
        }
    }
}
