package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.Usuario;

public final class UsuarioMapper {
    private UsuarioMapper() { }

    public static UsuarioYoResponseDTO toYoDTO(Usuario usuario, EntrenadorYoDTO entrenador) {
        return new UsuarioYoResponseDTO(usuario.getId(), usuario.getNombre(), usuario.getApellido(),
                usuario.getCorreo(), usuario.getRol().toLowerCase(), usuario.isActivo(), usuario.getCreadoEn(), entrenador);
    }

    public static UsuarioResponseDTO toDTO(Usuario usuario) {
        return new UsuarioResponseDTO(usuario.getId(), usuario.getNombre(), usuario.getApellido(),
                usuario.getCorreo(), usuario.getRol().toLowerCase(), usuario.isActivo(), usuario.getCreadoEn());
    }
}
