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

    /**
     * Nombres de los roles del usuario ({@code ADMINISTRADOR}, {@code TESORERO}, {@code ENTRENADOR},
     * {@code ACUDIENTE}), ordenados. Vacía si no existe en {@code usuario} o si su cuenta está inactiva
     * ({@code usuario.activo = false}): así revocar el acceso surte efecto en la siguiente petición. La usa
     * {@code util.security.JwtValidator} en cada petición autenticada.
     *
     * @param idUsuario el {@code sub} del JWT de Supabase Auth, que es {@code usuario.id_usuario}
     */
    List<String> rolesActivos(UUID idUsuario);
}
