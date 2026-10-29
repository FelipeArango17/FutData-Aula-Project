package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Formato único de todas las respuestas de error de la API.
 *
 * @param timestamp momento del error, ISO 8601 en UTC
 * @param estado    código HTTP, igual al de la respuesta
 * @param error     nombre estándar del código HTTP, por ejemplo {@code Bad Request}
 * @param mensaje   explicación en español que el frontend puede mostrar; nunca incluye la traza
 * @param ruta      ruta de la petición, sin la cadena de consulta
 */
public record ErrorResponseDTO(
        @Schema(example = "2026-10-28T23:15:30.123Z") Instant timestamp,
        @Schema(example = "400") int estado,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "nombre: no debe estar vacío") String mensaje,
        @Schema(example = "/api/entrenamientos") String ruta) {
}
