package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code POST /api/horario-semana/cancelaciones}. Todo llega como texto y el servicio lo valida junto, para
 * responder un solo {@code 400} con todos los campos que fallan.
 */
public record CancelacionRequestDTO(
        @Schema(example = "2026-11-10", description = "AAAA-MM-DD; no puede ser pasada y debe caer en el día de la franja",
                requiredMode = Schema.RequiredMode.REQUIRED) String fecha,
        @Schema(example = "0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b", description = "Id (UUID) de la franja que se cancela",
                requiredMode = Schema.RequiredMode.REQUIRED) String idFranja,
        @Schema(example = "Lluvia", nullable = true, description = "Opcional, máximo 200 caracteres") String motivo) {
}
