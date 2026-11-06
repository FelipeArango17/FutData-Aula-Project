package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Cuerpo de {@code PUT /api/asistencia/sesion}. La sesión es una franja en una fecha ({@code idFranja}) o un extra
 * ({@code idExcepcion}), exactamente una de las dos. Todo llega como texto y el servicio lo valida junto, para responder
 * un solo {@code 400} con todos los campos que fallan.
 */
public record AsistenciaRequestDTO(
        @Schema(example = "2026-11-03", description = "AAAA-MM-DD; hoy o pasada, nunca futura",
                requiredMode = Schema.RequiredMode.REQUIRED) String fecha,
        @Schema(example = "0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b", nullable = true,
                description = "Id de la franja (la fecha debe caer en su día de la semana). Exactamente uno: idFranja o idExcepcion")
        String idFranja,
        @Schema(example = "0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2c", nullable = true,
                description = "Id del extra (la fecha debe ser la del extra). Exactamente uno: idFranja o idExcepcion")
        String idExcepcion,
        @Schema(description = "Al menos un deportista de la lista de la sesión; puede ser solo una parte de la lista")
        List<RegistroAsistenciaRequestDTO> registros) {
}
