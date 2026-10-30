package co.edu.futdata.api.dto;

import java.time.LocalDate;

/** Datos personales para registrar un deportista. */
public record DeportistaRequestDTO(
        String nombre,
        String apellido,
        String tipoDocumento,
        String documento,
        LocalDate fechaNacimiento,
        String telefono,
        String direccion) {
}
