package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un deportista del cuerpo de {@code PUT /api/asistencia/sesion}. Todo llega como texto y el servicio lo valida junto. */
public record RegistroAsistenciaRequestDTO(
        @Schema(example = "0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b", description = "Id (UUID) del deportista de la lista de la sesión",
                requiredMode = Schema.RequiredMode.REQUIRED) String idDeportista,
        @Schema(example = "PRESENTE", description = "PRESENTE, AUSENTE o JUSTIFICADO",
                requiredMode = Schema.RequiredMode.REQUIRED) String estado,
        @Schema(example = "Llegó tarde", nullable = true, description = "Opcional, máximo 300 caracteres") String observaciones) {
}
