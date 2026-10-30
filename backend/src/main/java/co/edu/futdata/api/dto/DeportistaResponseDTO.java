package co.edu.futdata.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Deportista creado; la edad se calcula a partir de la fecha de nacimiento. */
public record DeportistaResponseDTO(
        UUID id,
        String nombre,
        String apellido,
        String tipoDocumento,
        String documento,
        LocalDate fechaNacimiento,
        int edad,
        String telefono,
        String direccion,
        UUID idCategoria) {
}
