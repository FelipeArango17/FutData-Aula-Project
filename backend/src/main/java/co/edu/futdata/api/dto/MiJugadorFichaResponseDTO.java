package co.edu.futdata.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La ficha de uno de los jugadores del acudiente: lo de {@link MiJugadorResponseDTO} más sus datos de contacto.
 *
 * @param documentosFaltantes tipos de documento obligatorio que aún no tienen uno APROBADO, en el orden oficial; vacía
 *                            si y solo si {@code estadoInscripcion} es COMPLETA
 */
public record MiJugadorFichaResponseDTO(
        UUID id,
        String nombre,
        String apellido,
        String tipoDocumento,
        String documento,
        LocalDate fechaNacimiento,
        int edad,
        String telefono,
        String direccion,
        String categoria,
        UUID idCategoria,
        String grupo,
        String posicion,
        String foto,
        String parentesco,
        boolean activo,
        String estadoInscripcion,
        List<String> documentosFaltantes) {
}
