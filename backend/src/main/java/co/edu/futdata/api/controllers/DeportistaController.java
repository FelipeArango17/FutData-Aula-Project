package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.services.facade.IDeportistaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Registro de datos personales de deportistas (HU-008, tasks 312 y 314). */
@RestController
@RequestMapping("/api/deportistas")
@Tag(name = "Deportistas", description = "Registro de deportistas")
@ApiResponse(responseCode = "400", description = "Datos inválidos o id no UUID",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ADMINISTRADOR",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class DeportistaController {

    private final IDeportistaService deportistas;

    public DeportistaController(IDeportistaService deportistas) {
        this.deportistas = deportistas;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ENTRENADOR')")
    @Operation(summary = "Listar deportistas")
    public List<DeportistaResponseDTO> listar(Authentication autenticacion) {
        boolean datosPrivados = autenticacion.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
        return deportistas.listar(datosPrivados);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registrar un deportista")
    @ApiResponse(responseCode = "201", description = "Deportista creado")
    @ApiResponse(responseCode = "409", description = "Documento duplicado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<DeportistaResponseDTO> crear(@RequestBody DeportistaRequestDTO datos) {
        DeportistaResponseDTO creado = deportistas.crear(datos);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Consultar un deportista")
    public DeportistaResponseDTO consultar(@PathVariable UUID id) {
        return deportistas.consultar(id);
    }
}
