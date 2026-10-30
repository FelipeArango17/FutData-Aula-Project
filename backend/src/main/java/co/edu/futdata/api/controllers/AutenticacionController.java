package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.Codigo2FARequestDTO;
import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.api.dto.VerificarCodigoRequestDTO;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/2fa")
@Tag(name = "Autenticación", description = "Verificación de identidad por código")
@ApiResponse(responseCode = "400", description = "Datos inválidos",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class AutenticacionController {
    private final ICodigoVerificacionService codigos;

    public AutenticacionController(ICodigoVerificacionService codigos) {
        this.codigos = codigos;
    }

    @PostMapping("/codigo")
    @Operation(summary = "Generar y enviar código de verificación")
    public ResponseEntity<Codigo2FAResponseDTO> solicitar(@RequestBody Codigo2FARequestDTO datos) {
        return ResponseEntity.accepted().body(codigos.solicitar(datos == null ? null : datos.correo()));
    }

    @PostMapping("/reenviar")
    @Operation(summary = "Reenviar código respetando el límite de un minuto")
    public Codigo2FAResponseDTO reenviar(@RequestBody Codigo2FARequestDTO datos) {
        return codigos.reenviar(datos == null ? null : datos.correo());
    }

    @PostMapping("/verificar")
    @Operation(summary = "Verificar código de seis dígitos")
    public UsuarioResponseDTO verificar(@RequestBody VerificarCodigoRequestDTO datos) {
        return codigos.verificar(datos == null ? null : datos.correo(), datos == null ? null : datos.codigo());
    }
}
