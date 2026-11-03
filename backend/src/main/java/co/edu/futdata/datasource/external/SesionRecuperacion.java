package co.edu.futdata.datasource.external;

import java.util.UUID;

/**
 * Lo que devuelve la verificación del enlace de crear contraseña: el usuario dueño del enlace y el token de acceso
 * de la sesión que Supabase abre al verificar, que se cierra enseguida con {@link ISupabaseAuthAdmin#cerrarSesion}.
 *
 * @param idUsuario   id del usuario en Supabase Auth (el mismo {@code usuario.id_usuario})
 * @param tokenAcceso JWT de la sesión. <b>Secreto</b>: no sale en {@link #toString()} ni en logs
 */
public record SesionRecuperacion(UUID idUsuario, String tokenAcceso) {

    @Override
    public String toString() {
        return "SesionRecuperacion[idUsuario=" + idUsuario + ", tokenAcceso=****]";
    }
}
