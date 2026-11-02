package co.edu.futdata.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Deportista leído. {@code edad} es la edad cumplida; {@code categoria} e {@code idCategoria} son la categoría
 * calculada por año de nacimiento (ambas {@code null} si queda fuera de 4 a 16). {@code grupo} solo viene si esa
 * categoría admite grupo (Cachorros o Semillero). {@code documento}, {@code telefono}, {@code direccion} y
 * {@code estadoInscripcion} solo para el administrador; al entrenador no se le envían {@code documento} ni
 * {@code estadoInscripcion} (ni la clave).
 *
 * @param foto              URL firmada temporal de la foto (nunca la ruta del bucket); {@code null} si no tiene o si
 *                          Storage no respondió
 * @param estadoInscripcion COMPLETA si los 4 documentos obligatorios tienen uno APROBADO; si no, PENDIENTE
 */
public record DeportistaResponseDTO(
        UUID id,
        String nombre,
        String apellido,
        String tipoDocumento,
        @JsonInclude(JsonInclude.Include.NON_NULL) String documento,
        LocalDate fechaNacimiento,
        int edad,
        String telefono,
        String direccion,
        String posicion,
        String foto,
        String categoria,
        UUID idCategoria,
        String grupo,
        boolean activo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String estadoInscripcion) {
}
