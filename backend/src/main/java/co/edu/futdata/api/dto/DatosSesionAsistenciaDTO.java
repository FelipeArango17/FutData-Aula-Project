package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** La sesión de la que se pasa lista. {@code idEntrenador} y {@code entrenador} son los ASIGNADOS (null = "por turnos"). */
public record DatosSesionAsistenciaDTO(
        @Schema(example = "2026-11-03") String fecha,
        @Schema(example = "16:00") String horaInicio,
        @Schema(example = "17:30") String horaFin,
        UUID idCategoria,
        @Schema(example = "Sub-12") String categoria,
        @Schema(example = "Horizontes") String sede,
        @Schema(nullable = true, description = "mañana o tarde; null si la sesión no tiene grupo") String grupo,
        @Schema(nullable = true) UUID idEntrenador,
        @Schema(nullable = true, description = "Nombre y apellido; null = por turnos") String entrenador) {
}
