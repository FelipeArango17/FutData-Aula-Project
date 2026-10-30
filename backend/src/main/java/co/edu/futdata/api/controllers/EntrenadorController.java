package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.services.facade.IEntrenadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Alta, consulta, edición, activación y categorías de los entrenadores (HU-025, CU-10). */
@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
@RequestMapping("/api/entrenadores")
@Tag(name = "Entrenadores", description = "Alta, consulta, edición y categorías de los entrenadores (HU-025)")
@ApiResponse(responseCode = "400", description = "Datos inválidos o id que no es UUID",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ADMINISTRADOR",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class EntrenadorController {

    private final IEntrenadorService entrenadores;

    public EntrenadorController(IEntrenadorService entrenadores) {
        this.entrenadores = entrenadores;
    }

    @GetMapping
    @Operation(summary = "Listar los entrenadores", description = "Ordenados por apellido y nombre, con sus categorías")
    @ApiResponse(responseCode = "200", description = "Lista, posiblemente vacía")
    public List<EntrenadorResponseDTO> listar() {
        return entrenadores.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un entrenador")
    @ApiResponse(responseCode = "200", description = "El entrenador")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public EntrenadorResponseDTO consultar(@PathVariable UUID id) {
        return entrenadores.consultar(id);
    }

    @PostMapping
    @Operation(summary = "Registrar un entrenador", description = "Solo datos personales; las categorías se asignan después")
    @ApiResponse(responseCode = "201", description = "Creado; la cabecera Location tiene su URL")
    @ApiResponse(responseCode = "409", description = "Documento (tipo y número) o correo de otro entrenador",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<EntrenadorResponseDTO> crear(@RequestBody EntrenadorRequestDTO datos) {
        EntrenadorResponseDTO creado = entrenadores.crear(datos);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar los datos personales de un entrenador", description = "No cambia sus categorías")
    @ApiResponse(responseCode = "200", description = "El entrenador actualizado")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Documento (tipo y número) o correo de otro entrenador",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public EntrenadorResponseDTO editar(@PathVariable UUID id, @RequestBody EntrenadorRequestDTO datos) {
        return entrenadores.editar(id, datos);
    }

    @PutMapping("/{id}/categorias")
    @Operation(summary = "Asignar las categorías de un entrenador",
            description = "Lista completa. Una categoría de otro entrenador exige \"reasignar\": true; "
                    + "una que tenía y no se envía queda sin entrenador")
    @ApiResponse(responseCode = "200", description = "El entrenador con sus categorías")
    @ApiResponse(responseCode = "404", description = "No existe el entrenador",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Categorías de otro entrenador sin reasignar, o entrenador inactivo",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public EntrenadorResponseDTO asignarCategorias(@PathVariable UUID id, @RequestBody AsignarCategoriasRequestDTO peticion) {
        return entrenadores.asignarCategorias(id, peticion);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar un entrenador",
            description = "Desactivarlo deja sin entrenador las categorías que dirigía y conserva su historial")
    @ApiResponse(responseCode = "200", description = "El entrenador con su estado")
    @ApiResponse(responseCode = "404", description = "No existe el entrenador",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public EntrenadorResponseDTO cambiarEstado(@PathVariable UUID id, @RequestBody CambiarEstadoEntrenadorRequestDTO peticion) {
        return entrenadores.cambiarEstado(id, peticion);
    }
}
