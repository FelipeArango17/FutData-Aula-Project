package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Una categoría con el entrenador que la dirige, para {@code GET /api/categorias}.
 *
 * @param edadMinima edad por año de nacimiento (año actual menos año de nacimiento), mínima; por ejemplo 12 en Sub-12
 * @param edadMaxima edad por año de nacimiento, máxima; por ejemplo 12 en Sub-12
 * @param entrenador quien la dirige, o {@code null} si todavía no tiene entrenador
 */
public record CategoriaResponseDTO(
        @Schema(example = "0b6c3f1e-9d2a-4c8b-a1e7-5f4d3c2b1a09") UUID id,
        @Schema(example = "Sub-12") String nombre,
        @Schema(example = "12") int edadMinima,
        @Schema(example = "12") int edadMaxima,
        EntrenadorResumenDTO entrenador) {

    /** Solo lo necesario para mostrar quién dirige la categoría: nada de documento, teléfono ni correo. */
    public record EntrenadorResumenDTO(
            @Schema(example = "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b") UUID id,
            @Schema(example = "Andrés") String nombre,
            @Schema(example = "Mora") String apellido) {
    }
}
