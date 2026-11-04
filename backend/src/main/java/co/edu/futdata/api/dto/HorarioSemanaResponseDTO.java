package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La semana pedida: {@code desde} es el lunes, {@code hasta} el sábado, y las sesiones van por fecha y hora. */
public record HorarioSemanaResponseDTO(
        @Schema(example = "2026-11-02") String desde,
        @Schema(example = "2026-11-07") String hasta,
        List<SesionSemanaResponseDTO> sesiones) {
}
