package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code PATCH /api/entrenadores/{id}/estado}.
 *
 * @param activo {@code false} para desactivar al entrenador (sus categorías quedan sin entrenador) y {@code true}
 *               para volver a activarlo (sin categorías: se le asignan de nuevo). Obligatorio
 */
public record CambiarEstadoEntrenadorRequestDTO(
        @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED) Boolean activo) {
}
