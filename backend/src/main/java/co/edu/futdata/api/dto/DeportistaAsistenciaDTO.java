package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Un deportista de la lista de la sesión con su asistencia ya guardada ({@code estado} null = sin registrar). */
public record DeportistaAsistenciaDTO(
        UUID idDeportista,
        String nombre,
        String apellido,
        @Schema(nullable = true, description = "PRESENTE, AUSENTE, JUSTIFICADO o null") String estado,
        @Schema(nullable = true) String observaciones) {
}
