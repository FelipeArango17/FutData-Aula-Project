package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Una franja del horario del club. Del entrenador solo van su nombre completo y si está activo: ningún otro dato
 * personal. Sin entrenador ({@code idEntrenador} null = "por turnos"), {@code entrenador} y
 * {@code entrenadorActivo} también son {@code null}.
 */
public record FranjaHorarioResponseDTO(
        @Schema(example = "0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b") UUID idFranja,
        @Schema(example = "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b") UUID idCategoria,
        @Schema(example = "Semillero") String categoria,
        @Schema(example = "2", description = "1 = lunes ... 6 = sábado") int dia,
        @Schema(example = "16:30") String horaInicio,
        @Schema(example = "18:00") String horaFin,
        @Schema(example = "UVA El Paraíso") String sede,
        @Schema(example = "null", nullable = true) UUID idEntrenador,
        @Schema(example = "null", nullable = true, description = "Nombre y apellido") String entrenador,
        @Schema(example = "null", nullable = true) Boolean entrenadorActivo,
        @Schema(example = "tarde", nullable = true) String grupo) {
}
