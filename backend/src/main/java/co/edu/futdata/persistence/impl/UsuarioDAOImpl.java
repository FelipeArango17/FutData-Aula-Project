package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioDAOImpl implements IUsuarioDAO {
    private static final String COLUMNAS = """
            u.id_usuario, u.nombre, u.apellido, u.correo, u.activo, u.creado_en,
            r.nombre as rol""";
    private static final RowMapper<Usuario> MAPEO = UsuarioDAOImpl::mapear;
    private final NamedParameterJdbcTemplate jdbc;

    public UsuarioDAOImpl(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Usuario> listar() {
        return jdbc.query("select " + COLUMNAS + " from usuario u join usuario_rol ur on ur.id_usuario = u.id_usuario "
                + "join rol r on r.id_rol = ur.id_rol order by u.nombre, u.correo", MAPEO);
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return jdbc.query("select " + COLUMNAS + " from usuario u join usuario_rol ur on ur.id_usuario = u.id_usuario "
                + "join rol r on r.id_rol = ur.id_rol where u.id_usuario = :id", Map.of("id", id), MAPEO)
                .stream().findFirst();
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return jdbc.query("select " + COLUMNAS + " from usuario u join usuario_rol ur on ur.id_usuario = u.id_usuario "
                + "join rol r on r.id_rol = ur.id_rol where lower(u.correo) = lower(:correo)",
                Map.of("correo", correo), MAPEO).stream().findFirst();
    }

    @Override
    public boolean existeCorreo(String correo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from usuario where lower(correo) = lower(:correo))",
                Map.of("correo", correo), Boolean.class));
    }

    @Override
    public UUID crear(Usuario usuario) {
        jdbc.update("insert into usuario (id_usuario, nombre, apellido, correo) values (:id, :nombre, :apellido, :correo)",
                Map.of("id", usuario.getId(), "nombre", usuario.getNombre(), "apellido", usuario.getApellido(),
                        "correo", usuario.getCorreo()));
        return usuario.getId();
    }

    @Override
    public void asignarRol(UUID idUsuario, String rol) {
        jdbc.update("insert into usuario_rol (id_usuario, id_rol) "
                + "select :id, id_rol from rol where nombre = :rol", Map.of("id", idUsuario, "rol", rol));
    }

    @Override
    public void actualizarRol(UUID idUsuario, String rol) {
        jdbc.update("update usuario_rol set id_rol = (select id_rol from rol where nombre = :rol) "
                + "where id_usuario = :id", Map.of("id", idUsuario, "rol", rol));
    }

    @Override
    public void actualizarActivo(UUID idUsuario, boolean activo) {
        jdbc.update("update usuario set activo = :activo where id_usuario = :id",
                Map.of("id", idUsuario, "activo", activo));
    }

    @Override
    public long administradoresActivos() {
        return jdbc.queryForObject("select count(*) from usuario u join usuario_rol ur on ur.id_usuario = u.id_usuario "
                + "join rol r on r.id_rol = ur.id_rol where u.activo and r.nombre = 'ADMINISTRADOR'",
                Map.of(), Long.class);
    }

    private static Usuario mapear(ResultSet fila, int numero) throws SQLException {
        Usuario u = new Usuario();
        u.setId(fila.getObject("id_usuario", UUID.class));
        u.setNombre(fila.getString("nombre"));
        u.setApellido(fila.getString("apellido"));
        u.setCorreo(fila.getString("correo"));
        u.setActivo(fila.getBoolean("activo"));
        u.setCreadoEn(fila.getObject("creado_en", java.time.OffsetDateTime.class));
        u.setRol(fila.getString("rol"));
        return u;
    }
}
