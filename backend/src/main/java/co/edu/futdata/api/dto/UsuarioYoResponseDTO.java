package co.edu.futdata.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Perfil de {@code GET /api/usuarios/yo}: los campos de {@link UsuarioResponseDTO} más {@code entrenador}, que siempre
 * va en el JSON y es {@code null} salvo para un ENTRENADOR con fila de entrenador vinculada a su cuenta.
 */
public record UsuarioYoResponseDTO(UUID id, String nombre, String apellido, String correo, String rol,
                                   boolean activo, OffsetDateTime creadoEn, EntrenadorYoDTO entrenador) {
}
