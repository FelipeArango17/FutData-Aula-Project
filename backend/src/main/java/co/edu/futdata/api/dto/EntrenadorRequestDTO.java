package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Datos personales de un entrenador para {@code POST /api/entrenadores} y {@code PUT /api/entrenadores/{id}}.
 *
 * <p>No lleva {@code id} ni {@code categorias}: si el cuerpo los trae, se ignoran. Las categorías se asignan con
 * {@code PUT /api/entrenadores/{id}/categorias}.
 *
 * <p>Sin anotaciones de Bean Validation, a propósito: el servicio primero quita los espacios sobrantes y después
 * valida con {@code ReglasEntrenador}, así que un nombre de solo espacios cuenta como vacío y los largos se miden
 * sin espacios. Los errores salen igual que los de Bean Validation ({@code 400}, {@code campo: mensaje}).
 */
public record EntrenadorRequestDTO(
        @Schema(example = "Luis", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String nombre,
        @Schema(example = "Pérez", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String apellido,
        @Schema(example = "CC", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CC", "CE", "PASAPORTE"}) String tipoDocumento,
        @Schema(example = "1098765432", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "CC y CE: solo dígitos, de 6 a 12. PASAPORTE: letras y números, de 6 a 12, se guarda en mayúsculas")
                String documento,
        @Schema(example = "1990-05-14", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "AAAA-MM-DD; no futura y de alguien con al menos 18 años") LocalDate fechaNacimiento,
        @Schema(example = "3001112233", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Exactamente 10 dígitos") String telefono,
        @Schema(example = "luis.perez@pradofc.co", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 320,
                description = "Se guarda en minúsculas") String correo,
        @Schema(example = "Porteros", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String especialidad,
        @Schema(example = "Lunes a viernes, 3:30 p. m. a 7:30 p. m.", maxLength = 250,
                description = "Texto libre, opcional") String horarioTrabajo,
        @Schema(example = "Calle 40 Sur 75b-81", maxLength = 250, description = "Opcional") String direccion) {
}
