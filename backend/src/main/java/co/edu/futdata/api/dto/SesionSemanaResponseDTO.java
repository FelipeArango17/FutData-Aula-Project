package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Una sesión de la semana. En el bloque 4B-1 solo hay franjas: {@code tipo} FRANJA, {@code estado} ACTIVO,
 * {@code idExcepcion} y {@code motivo} null y {@code puedeGestionar} false. Los demás campos se llaman igual que en
 * {@code /api/horario-club}.
 */
public record SesionSemanaResponseDTO(
        @Schema(example = "FRANJA", description = "FRANJA o EXTRA") String tipo,
        @Schema(example = "ACTIVO", description = "ACTIVO o CANCELADO") String estado,
        @Schema(nullable = true) UUID idFranja,
        @Schema(nullable = true) UUID idExcepcion,
        @Schema(example = "2026-11-03") String fecha,
        @Schema(example = "2", description = "1 = lunes ... 6 = sábado") int dia,
        @Schema(example = "16:30") String horaInicio,
        @Schema(example = "18:00") String horaFin,
        UUID idCategoria,
        @Schema(example = "Semillero") String categoria,
        @Schema(example = "UVA El Paraíso") String sede,
        @Schema(nullable = true) UUID idEntrenador,
        @Schema(nullable = true, description = "Nombre y apellido; null = por turnos") String entrenador,
        @Schema(nullable = true) Boolean entrenadorActivo,
        @Schema(nullable = true) String grupo,
        @Schema(nullable = true) String motivo,
        @Schema(description = "Si quien consulta puede cancelar o editar la sesión") boolean puedeGestionar) {
}
