package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code POST /api/horario-semana/extras}: un entrenamiento adicional que no está en las franjas fijas. Todo
 * llega como texto y el servicio lo valida junto, para responder un solo {@code 400} con todos los campos que fallan.
 */
public record ExtraRequestDTO(
        @Schema(example = "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b", description = "Id (UUID) de la categoría",
                requiredMode = Schema.RequiredMode.REQUIRED) String idCategoria,
        @Schema(example = "2026-11-10", description = "AAAA-MM-DD; de lunes a sábado y no pasada (hoy sí)",
                requiredMode = Schema.RequiredMode.REQUIRED) String fecha,
        @Schema(example = "18:00", description = "HH:mm de 24 horas", requiredMode = Schema.RequiredMode.REQUIRED)
        String horaInicio,
        @Schema(example = "19:30", description = "HH:mm; posterior a horaInicio", requiredMode = Schema.RequiredMode.REQUIRED)
        String horaFin,
        @Schema(example = "Horizontes", description = "Horizontes o UVA El Paraíso", requiredMode = Schema.RequiredMode.REQUIRED)
        String sede,
        @Schema(nullable = true, example = "e0000000-0000-0000-0000-00000000000a",
                description = "Id (UUID) del entrenador, que debe existir y estar activo; null = \"por turnos\"")
        String idEntrenador,
        @Schema(nullable = true, example = "tarde", description = "mañana o tarde; solo en Cachorros y Semillero") String grupo,
        @Schema(nullable = true, example = "Partido amistoso", description = "Opcional, máximo 200 caracteres") String motivo) {
}
