package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Usuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUsuarioDAO {
    List<Usuario> listar();
    Optional<Usuario> buscarPorId(UUID id);
    Optional<Usuario> buscarPorCorreo(String correo);
    boolean existeCorreo(String correo);
    UUID crear(Usuario usuario);
    void asignarRol(UUID idUsuario, String rol);
    void actualizarRol(UUID idUsuario, String rol);
    void actualizarActivo(UUID idUsuario, boolean activo);
    long administradoresActivos();
}
