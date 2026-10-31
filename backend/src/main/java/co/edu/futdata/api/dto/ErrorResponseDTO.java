package co.edu.futdata.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Formato único de todas las respuestas de error de la API.
 *
 * @param timestamp momento del error, ISO 8601 en UTC
 * @param estado    código HTTP, igual al de la respuesta
 * @param error     nombre estándar del código HTTP, por ejemplo {@code Bad Request}
 * @param mensaje   explicación en español que el frontend puede mostrar; nunca incluye la traza
 * @param ruta      ruta de la petición, sin la cadena de consulta
 * @param campos    campos con error o en conflicto, ordenados, para marcarlos en el formulario. Solo aparece
 *                  cuando el error es de campos concretos ({@code 400} del servicio o {@code 409})
 */
public record ErrorResponseDTO(
        @Schema(example = "2026-10-28T23:15:30.123Z") Instant timestamp,
        @Schema(example = "400") int estado,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "nombre: no debe estar vacío") String mensaje,
        @Schema(example = "/api/entrenadores") String ruta,
        @Schema(example = "[\"nombre\"]") @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> campos,
        @Schema(example = "INCORRECTO") @JsonInclude(JsonInclude.Include.NON_NULL) String codigo,
        @Schema(example = "120000") @JsonInclude(JsonInclude.Include.NON_NULL) Long msRestantes) {

    public ErrorResponseDTO(Instant timestamp, int estado, String error, String mensaje, String ruta) {
        this(timestamp, estado, error, mensaje, ruta, List.of(), null, null);
    }

    public ErrorResponseDTO(Instant timestamp, int estado, String error, String mensaje, String ruta,
                            List<String> campos) {
        this(timestamp, estado, error, mensaje, ruta, campos, null, null);
    }
}
