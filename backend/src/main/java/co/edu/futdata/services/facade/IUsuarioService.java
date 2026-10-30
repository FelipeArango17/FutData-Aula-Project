package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.UsuarioRequestDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import java.util.List;
import java.util.UUID;

public interface IUsuarioService {
    List<UsuarioResponseDTO> listar();
    UsuarioResponseDTO consultar(UUID id);
    UsuarioResponseDTO crear(UsuarioRequestDTO datos);
    UsuarioResponseDTO cambiarRol(UUID id, String rol);
    UsuarioResponseDTO cambiarAcceso(UUID id, boolean activo);
}
