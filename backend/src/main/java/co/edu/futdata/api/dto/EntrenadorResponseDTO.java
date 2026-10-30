package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un entrenador como lo recibe el frontend (misma forma que {@code entrenadoresService.js}, más
 * {@code fechaNacimiento} y {@code direccion}). Los opcionales vacíos llegan como {@code null}.
 *
 * @param categorias nombres de las categorías que dirige, en el orden oficial (Sub-8, Sub-10, Sub-12, Sub-14, Sub-16)
 */
public record EntrenadorResponseDTO(
        @Schema(example = "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b") UUID id,
        @Schema(example = "Luis") String nombre,
        @Schema(example = "Pérez") String apellido,
        @Schema(example = "CC", allowableValues = {"CC", "TI", "CE", "PASAPORTE"}) String tipoDocumento,
        @Schema(example = "1098765432") String documento,
        @Schema(example = "1990-05-14") LocalDate fechaNacimiento,
        @Schema(example = "3001112233") String telefono,
        @Schema(example = "luis.perez@pradofc.co") String correo,
        @Schema(example = "Porteros") String especialidad,
        @Schema(example = "[\"Sub-8\", \"Sub-12\"]") List<String> categorias,
        @Schema(example = "Lunes a viernes, 3:30 p. m. a 7:30 p. m.") String horarioTrabajo,
        @Schema(example = "Calle 40 Sur 75b-81") String direccion) {
}
