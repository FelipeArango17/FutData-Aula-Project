package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** UsuarioDAOImpl contra PostgreSQL con las migraciones 001 a 004. Sin Docker ni base local, se omite. */
@ExtendWith(DockerDisponible.class)
class UsuarioDAOImplTest {

    private NamedParameterJdbcTemplate jdbc;
    private UsuarioDAOImpl usuarios;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        usuarios = new UsuarioDAOImpl(jdbc);
    }

    /** Usuario de Supabase Auth con su perfil y los roles indicados; cada prueba usa un correo propio. */
    private UUID usuario(boolean activo, String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo, activo) VALUES (:id, 'Prueba', 'Usuario', :correo, :activo)",
                Map.of("id", id, "correo", id + "@pradofc.co", "activo", activo));
        for (String rol : roles) {
            jdbc.update("""
                    INSERT INTO usuario_rol (id_usuario, id_rol)
                    SELECT :id, id_rol FROM rol WHERE nombre = :rol""", Map.of("id", id, "rol", rol));
        }
        return id;
    }

    @Test
    void devuelveLosRolesOrdenados() {
        UUID id = usuario(true, "TESORERO", "ADMINISTRADOR");

        assertThat(usuarios.rolesActivos(id)).containsExactly("ADMINISTRADOR", "TESORERO");
    }

    @Test
    void unaCuentaInactivaNoTieneRoles() {
        UUID id = usuario(false, "ADMINISTRADOR");

        assertThat(usuarios.rolesActivos(id)).isEmpty();
    }

    @Test
    void unUsuarioSinPerfilOSinRolesNoTieneRoles() {
        assertThat(usuarios.rolesActivos(UUID.randomUUID())).isEmpty();
        assertThat(usuarios.rolesActivos(usuario(true))).isEmpty();
    }
}
