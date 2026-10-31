package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.api.dto.VerificarCodigoRequestDTO;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.security.JwtValidator;
import co.edu.futdata.util.security.RutasSin2FA;
import co.edu.futdata.util.security.SesionVerificadaFilter;
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
 * Segundo paso del inicio de sesión (HU-002) y cierre de sesión. Exige el JWT de Supabase del primer paso, con
 * cualquier rol: el usuario es el {@code sub} del token, la sesión es su {@code session_id} y el correo no viaja en
 * la petición. Las rutas {@code /2fa/**} no exigen haber verificado el código ({@link RutasSin2FA}); {@code /logout}
 * sí.
 */
@RestController
@RequestMapping("/api/auth")
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

    @PostMapping("/2fa/codigo")
    @Operation(summary = "Generar y enviar código de verificación al correo de la cuenta")
    public ResponseEntity<Codigo2FAResponseDTO> solicitar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(codigos.solicitar(usuario(jwt)));
    }

    @PostMapping("/2fa/reenviar")
    @Operation(summary = "Reenviar código respetando el límite de un minuto")
    public Codigo2FAResponseDTO reenviar(@AuthenticationPrincipal Jwt jwt) {
        return codigos.reenviar(usuario(jwt));
    }

    @PostMapping("/2fa/verificar")
    @Operation(summary = "Verificar código de seis dígitos",
            description = "Si es correcto, solo la sesión del token (claim session_id) queda verificada.")
    public UsuarioResponseDTO verificar(@AuthenticationPrincipal Jwt jwt, @RequestBody VerificarCodigoRequestDTO datos) {
        return codigos.verificar(usuario(jwt), sesion(jwt), datos == null ? null : datos.codigo());
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar la sesión del token",
            description = "La sesión deja de estar verificada; las demás sesiones del usuario no cambian. "
                    + "El frontend también debe cerrar la sesión en Supabase Auth.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        codigos.cerrarSesion(usuario(jwt), sesion(jwt));
        return ResponseEntity.noContent().build();
    }

    /** El {@code sub} de los tokens de Supabase es el UUID de la cuenta ({@code usuario.id_usuario}). */
    private static UUID usuario(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RecursoNoEncontradoException("La cuenta no está registrada en FutData.");
        }
    }

    /** El {@code session_id} del token. {@link SesionVerificadaFilter} ya respondió 401 si no venía. */
    private static UUID sesion(Jwt jwt) {
        return JwtValidator.idSesion(jwt)
                .orElseThrow(() -> new IllegalStateException("El token llegó al controlador sin session_id"));
    }
}
