package co.edu.futdata.domain.entities;

import java.util.UUID;

/**
 * Un acudiente (tabla {@code acudiente}) tal como lo usa la inscripción única: solo los datos de contacto. El
 * documento y la fecha de nacimiento no se leen (migración 011: son opcionales y la inscripción no los pide).
 *
 * @param correo    siempre en minúsculas (el único de la base es sobre {@code lower(correo)})
 * @param idUsuario la cuenta de acceso enlazada; {@code null} mientras no tenga cuenta
 */
public record Acudiente(
        UUID id,
        String nombre,
        String apellido,
        String correo,
        String telefono,
        UUID idUsuario) {
}
