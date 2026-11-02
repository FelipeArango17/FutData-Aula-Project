package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Una franja para {@code POST /api/horario-club} y {@code PUT /api/horario-club/{id}}. El {@code PUT} reemplaza
 * todos los campos: uno que no se envía queda vacío (y, si es obligatorio, da {@code 400}).
 *
 * <p>Sin anotaciones de Bean Validation, a propósito (como {@link EntrenadorRequestDTO}): el servicio limpia y valida
 * con {@code ReglasFranja} y devuelve todos los errores juntos en un solo {@code 400}.
 */
public record FranjaHorarioRequestDTO(
        @Schema(example = "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Id de una de las 9 categorías") UUID idCategoria,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "6",
                description = "1 = lunes ... 6 = sábado") Integer dia,
        @Schema(example = "16:30", requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^([01]\\d|2[0-3]):[0-5]\\d$",
                description = "HH:mm de 24 horas") String horaInicio,
        @Schema(example = "18:00", requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^([01]\\d|2[0-3]):[0-5]\\d$",
                description = "HH:mm de 24 horas; posterior a horaInicio") String horaFin,
        @Schema(example = "UVA El Paraíso", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"Horizontes", "UVA El Paraíso"}) String sede,
        @Schema(example = "null", nullable = true,
                description = "Entrenador fijo; null = \"por turnos\". Debe estar activo, salvo en un PUT que conserva el mismo")
                UUID idEntrenador,
        @Schema(example = "tarde", nullable = true, allowableValues = {"mañana", "tarde"},
                description = "Opcional; solo en Cachorros y Semillero") String grupo) {
}
