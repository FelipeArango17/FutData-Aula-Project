package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.PaginaDTO;
import co.edu.futdata.services.facade.IDeportistaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Operation(summary = "Listar deportistas (paginado)",
            description = "Categoría calculada por año de nacimiento. El entrenador no recibe teléfono, dirección ni "
                    + "estadoInscripcion. Un valor inválido de page, size, sort o categoria responde 400.")
    public PaginaDTO<DeportistaResponseDTO> listar(
            @Parameter(description = "Página, desde 0 (por defecto 0)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamaño de página, de 1 a 100 (por defecto 10)") @RequestParam(required = false) Integer size,
            @Parameter(description = "campo o campo,asc|desc; campos: nombre, apellido, documento, fechaNacimiento, "
                    + "fechaRegistro (por defecto apellido,asc)") @RequestParam(required = false) String sort,
            @Parameter(description = "true o false; sin él, todos") @RequestParam(required = false) Boolean activo,
            @Parameter(description = "Nombre exacto de una de las 9 categorías, por ejemplo Semillero o Sub-11")
            @RequestParam(required = false) String categoria,
            Authentication autenticacion) {
        boolean esAdministrador = autenticacion.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
        return deportistas.listar(page, size, sort, activo, categoria, esAdministrador);
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

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Editar un deportista",
            description = "Reemplaza nombre, apellido, tipo y número de documento, fecha de nacimiento, teléfono, "
                    + "dirección, posición, grupo y activo (obligatorio). No toca la foto. La regla de 4 a 16 años "
                    + "solo se exige si cambia la fecha de nacimiento.")
    @ApiResponse(responseCode = "200", description = "Deportista actualizado")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Documento duplicado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public DeportistaResponseDTO actualizar(@PathVariable UUID id, @RequestBody DeportistaRequestDTO datos) {
        return deportistas.actualizar(id, datos);
    }

    @PostMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Subir o reemplazar la foto",
            description = "Multipart con la parte 'archivo'. JPG o PNG (se revisa el contenido, no la extensión), "
                    + "máximo 5 MB. La respuesta trae la foto como URL firmada temporal.")
    @ApiResponse(responseCode = "200", description = "Foto guardada")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public DeportistaResponseDTO subirFoto(@PathVariable UUID id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        return deportistas.subirFoto(id, archivo.getBytes());
    }

    @DeleteMapping("/{id}/foto")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Quitar la foto", description = "Borra el archivo del bucket y deja la foto en null.")
    @ApiResponse(responseCode = "200", description = "Foto quitada")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public DeportistaResponseDTO eliminarFoto(@PathVariable UUID id) {
        return deportistas.eliminarFoto(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Consultar un deportista")
    public DeportistaResponseDTO consultar(@PathVariable UUID id) {
        return deportistas.consultar(id);
    }
}
