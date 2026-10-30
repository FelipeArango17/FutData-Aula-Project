package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.UsuarioRequestDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.services.facade.IUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Gestión de usuarios y roles")
@ApiResponse(responseCode = "400", description = "Datos inválidos",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class UsuarioController {
    private final IUsuarioService usuarios;

    public UsuarioController(IUsuarioService usuarios) { this.usuarios = usuarios; }

    @GetMapping
    public List<UsuarioResponseDTO> listar() { return usuarios.listar(); }

    @GetMapping("/{id}")
    public UsuarioResponseDTO consultar(@PathVariable UUID id) { return usuarios.consultar(id); }

    @PostMapping
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
    public UsuarioResponseDTO cambiarRol(@PathVariable UUID id, @RequestBody UsuarioRequestDTO datos) {
        return usuarios.cambiarRol(id, datos == null ? null : datos.rol());
    }

    @PostMapping("/{id}/revocar")
    public UsuarioResponseDTO revocar(@PathVariable UUID id) { return usuarios.cambiarAcceso(id, false); }

    @PostMapping("/{id}/restablecer")
    public UsuarioResponseDTO restablecer(@PathVariable UUID id) { return usuarios.cambiarAcceso(id, true); }
}
