package co.edu.futdata.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UsuarioResponseDTO(UUID id, String nombre, String apellido, String correo,
                                 String rol, boolean activo, OffsetDateTime creadoEn) {
}
