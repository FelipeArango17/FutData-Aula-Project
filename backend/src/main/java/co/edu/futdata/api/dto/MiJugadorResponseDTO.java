package co.edu.futdata.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un jugador del acudiente, en su lista. Sin documento, teléfono ni dirección. {@code categoria} e {@code idCategoria}
 * son {@code null} si queda fuera de 4 a 16 años; {@code grupo} solo viene en Cachorros y Semillero.
 *
 * @param foto              URL firmada temporal (nunca la ruta del bucket); {@code null} si no tiene o Storage no respondió
 * @param parentesco        el del vínculo con este acudiente, sin tilde (Madre, Padre, Abuelo, Abuela, Tia, Tio, Otro); puede ser {@code null}
 * @param estadoInscripcion COMPLETA si los 4 documentos obligatorios tienen uno APROBADO; si no, PENDIENTE
 */
public record MiJugadorResponseDTO(
        UUID id,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento,
        int edad,
        String categoria,
        UUID idCategoria,
        String grupo,
        String posicion,
        String foto,
        String parentesco,
        boolean activo,
        String estadoInscripcion) {
}
