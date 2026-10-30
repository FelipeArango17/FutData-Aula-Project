package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.Usuario;

public final class UsuarioMapper {
    private UsuarioMapper() { }

    public static UsuarioResponseDTO toDTO(Usuario usuario) {
        return new UsuarioResponseDTO(usuario.getId(), usuario.getNombre(), usuario.getApellido(),
                usuario.getCorreo(), usuario.getRol().toLowerCase(), usuario.isActivo(), usuario.getCreadoEn());
    }
}
