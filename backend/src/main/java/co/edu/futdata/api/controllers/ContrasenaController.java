package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.CrearContrasenaRequestDTO;
import co.edu.futdata.api.dto.CrearContrasenaResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.services.facade.IContrasenaService;
import co.edu.futdata.util.security.IpCliente;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Crear la contraseña con el enlace del correo (regla 9). <b>Ruta pública</b> ({@code RutasSin2FA}): quien la usa
 * todavía no tiene contraseña ni sesión; el token del enlace es la credencial.
 */
@RestController
@Tag(name = "Autenticación", description = "Verificación de identidad por código")
public class ContrasenaController {

    private final IContrasenaService contrasenas;

    public ContrasenaController(IContrasenaService contrasenas) {
        this.contrasenas = contrasenas;
    }

    @PostMapping("/api/auth/crear-contrasena")
    @Operation(summary = "Crear la contraseña con el enlace del correo",
            description = "Pública. Verifica el enlace (lo consume), fija la contraseña y no inicia sesión. "
                    + "Máximo 10 intentos por minuto por IP")
    @ApiResponse(responseCode = "200", description = "Contraseña creada; el usuario inicia sesión aparte")
    @ApiResponse(responseCode = "400", description = "Contraseña fuera de la política (con campos [\"contrasena\"]), "
            + "o enlace inválido, vencido o usado (mensaje genérico, sin campos)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "429", description = "Demasiados intentos (cabecera Retry-After)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Supabase Auth no respondió",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public CrearContrasenaResponseDTO crear(@RequestBody CrearContrasenaRequestDTO datos, HttpServletRequest peticion) {
        contrasenas.crearContrasena(datos.token(), datos.contrasena(), IpCliente.de(peticion));
        return new CrearContrasenaResponseDTO(CrearContrasenaResponseDTO.CREADA);
    }
}
