package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.UsuarioRequestDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.api.dto.UsuarioYoResponseDTO;
import co.edu.futdata.services.facade.IUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Cuentas de usuario y sus roles (HU-004, CU-06). Solo para ADMINISTRADOR. */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Gestión de usuarios y roles")
@ApiResponse(responseCode = "400", description = "Datos inválidos",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ADMINISTRADOR",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class UsuarioController {
    private final IUsuarioService usuarios;

    public UsuarioController(IUsuarioService usuarios) { this.usuarios = usuarios; }

    @GetMapping("/yo")
    @Operation(summary = "Consultar el perfil del usuario autenticado")
    public UsuarioYoResponseDTO yo(@AuthenticationPrincipal Jwt jwt) {
        return usuarios.perfil(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<UsuarioResponseDTO> listar() { return usuarios.listar(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioResponseDTO consultar(@PathVariable UUID id) { return usuarios.consultar(id); }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear usuario e invitarlo por correo")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "409", description = "Correo duplicado")
    public ResponseEntity<UsuarioResponseDTO> crear(@RequestBody UsuarioRequestDTO datos) {
        UsuarioResponseDTO creado = usuarios.crear(datos);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioResponseDTO cambiarRol(@PathVariable UUID id, @RequestBody UsuarioRequestDTO datos) {
        return usuarios.cambiarRol(id, datos == null ? null : datos.rol());
    }

    @PostMapping("/{id}/revocar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioResponseDTO revocar(@PathVariable UUID id) { return usuarios.cambiarAcceso(id, false); }

    @PostMapping("/{id}/restablecer")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioResponseDTO restablecer(@PathVariable UUID id) { return usuarios.cambiarAcceso(id, true); }
}
