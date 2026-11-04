package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.UsuarioRequestDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.api.dto.UsuarioYoResponseDTO;
import java.util.List;
import java.util.UUID;

public interface IUsuarioService {
    List<UsuarioResponseDTO> listar();
    UsuarioResponseDTO consultar(UUID id);

    /** El perfil de quien consulta (GET /api/usuarios/yo), con el bloque {@code entrenador} si es ENTRENADOR con fila vinculada. */
    UsuarioYoResponseDTO perfil(UUID id);
    UsuarioResponseDTO crear(UsuarioRequestDTO datos);
    UsuarioResponseDTO cambiarRol(UUID id, String rol);
    UsuarioResponseDTO cambiarAcceso(UUID id, boolean activo);
}
