package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.FranjaHorarioRequestDTO;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import co.edu.futdata.services.facade.IFranjaHorarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Horario fijo del club, lunes a sábado (bloque 4A, tabla {@code franja_horario}). Lo leen los cuatro roles; solo el
 * administrador lo edita. La respuesta no trae datos personales: del entrenador, solo su nombre completo.
 */
@RestController
@RequestMapping("/api/horario-club")
@Tag(name = "Horario del club", description = "Franjas fijas de entrenamiento por categoría, día, hora y sede")
@ApiResponse(responseCode = "400", description = "Datos o filtros inválidos, o id que no es UUID",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene un rol permitido para esta operación",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class FranjaHorarioController {

    static final String LECTURA = "hasAnyRole('ADMINISTRADOR', 'TESORERO', 'ENTRENADOR', 'ACUDIENTE')";
    static final String EDICION = "hasRole('ADMINISTRADOR')";

    private final IFranjaHorarioService franjas;

    public FranjaHorarioController(IFranjaHorarioService franjas) {
        this.franjas = franjas;
    }

    @GetMapping
    @PreAuthorize(LECTURA)
    @Operation(summary = "Listar las franjas",
            description = "Ordenadas por día, hora de inicio y categoría. Filtros opcionales y combinables.")
    @ApiResponse(responseCode = "200", description = "Lista, posiblemente vacía")
    public List<FranjaHorarioResponseDTO> listar(
            @Parameter(description = "Nombre exacto de una de las 9 categorías, por ejemplo Semillero o Sub-11")
            @RequestParam(required = false) String categoria,
            @Parameter(description = "Id (UUID) del entrenador") @RequestParam(required = false) UUID entrenador,
            @Parameter(description = "Horizontes o UVA El Paraíso") @RequestParam(required = false) String sede) {
        return franjas.listar(categoria, entrenador, sede);
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Consultar una franja")
    @ApiResponse(responseCode = "200", description = "La franja")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public FranjaHorarioResponseDTO consultar(@PathVariable UUID id) {
        return franjas.consultar(id);
    }

    @PostMapping
    @PreAuthorize(EDICION)
    @Operation(summary = "Crear una franja",
            description = "idEntrenador null = \"por turnos\"; si viene, el entrenador debe existir y estar activo. "
                    + "grupo solo en Cachorros y Semillero. Categorías distintas pueden coincidir en hora y sede.")
    @ApiResponse(responseCode = "201", description = "Creada; la cabecera Location tiene su URL")
    @ApiResponse(responseCode = "409", description = "Ese entrenador ya tiene otra franja que se cruza con este horario",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<FranjaHorarioResponseDTO> crear(@RequestBody FranjaHorarioRequestDTO datos) {
        FranjaHorarioResponseDTO creada = franjas.crear(datos);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.idFranja()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDICION)
    @Operation(summary = "Reemplazar una franja",
            description = "Reemplaza todos los campos. Si la franja no existe responde 404 antes de validar los datos. "
                    + "El estado del entrenador solo se revisa si idEntrenador cambia. La franja no choca consigo misma.")
    @ApiResponse(responseCode = "200", description = "La franja actualizada")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Ese entrenador ya tiene otra franja que se cruza con este horario",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public FranjaHorarioResponseDTO actualizar(@PathVariable UUID id, @RequestBody FranjaHorarioRequestDTO datos) {
        return franjas.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(EDICION)
    @Operation(summary = "Eliminar una franja")
    @ApiResponse(responseCode = "204", description = "Eliminada")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        franjas.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
