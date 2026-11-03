package co.edu.futdata.datasource.external;

import java.util.UUID;

/**
 * Operaciones de administración de Supabase Auth que usa el backend con la llave {@code service_role}.
 *
 * <p>{@link #invitar}, {@link #eliminar} y {@link #cambiarAcceso} son las de la gestión de usuarios
 * ({@code UsuarioServiceImpl}). Las demás son el camino de "enlace para crear la contraseña" (regla 9), sin
 * contraseña temporal: {@link #crearUsuario} crea la cuenta sin contraseña, {@link #generarTokenRecuperacion}
 * devuelve el {@code hashed_token} con el que se arma el enlace del frontend (nunca el {@code action_link}), y al
 * crear la contraseña {@link #verificarTokenRecuperacion} consume el token, {@link #fijarContrasena} la guarda y
 * {@link #cerrarSesion} cierra la sesión que abrió la verificación.
 *
 * <p>Ninguna operación registra en logs un token, un enlace ni una contraseña.
 */
public interface ISupabaseAuthAdmin {
    UUID invitar(String correo);
    void eliminar(UUID idUsuario);
    void cambiarAcceso(UUID idUsuario, boolean activo);

    /**
     * Crea la cuenta de Supabase Auth con el correo ya confirmado y sin contraseña: no puede iniciar sesión hasta
     * que la cree con el enlace. No envía ningún correo.
     *
     * @return id del usuario nuevo en Supabase Auth
     * @throws CorreoYaRegistradoException si ya existe una cuenta con ese correo
     * @throws AuthNoDisponibleException   si Supabase Auth no responde bien
     */
    UUID crearUsuario(String correo);

    /**
     * Genera un enlace de recuperación ({@code type=recovery}) con la API admin, sin que Supabase envíe correo, y
     * devuelve solo su {@code hashed_token}. Un token nuevo invalida el anterior del mismo usuario. <b>El token es
     * secreto</b>.
     *
     * @throws AuthNoDisponibleException si Supabase Auth no responde bien o no existe una cuenta con ese correo
     */
    String generarTokenRecuperacion(String correo);

    /**
     * Verifica el token ({@code POST /verify} con {@code token_hash} y {@code type=recovery}). Lo <b>consume</b>:
     * no se puede volver a usar. Supabase abre una sesión, que hay que cerrar con {@link #cerrarSesion}.
     *
     * @throws TokenRecuperacionInvalidoException si el token no existe, venció, ya se usó o fue reemplazado
     * @throws AuthNoDisponibleException          si Supabase Auth no responde bien
     */
    SesionRecuperacion verificarTokenRecuperacion(String tokenHash);

    /**
     * Fija la contraseña del usuario con la API admin. La política ({@code ReglasContrasena}) se valida antes, en
     * el servicio.
     *
     * @throws AuthNoDisponibleException si Supabase Auth no responde bien o rechaza la contraseña
     */
    void fijarContrasena(UUID idUsuario, String contrasena);

    /**
     * Cierra solo la sesión de ese token de acceso ({@code POST /logout?scope=local}). Si la sesión ya no existe,
     * no hace nada.
     *
     * @throws AuthNoDisponibleException si Supabase Auth no responde bien
     */
    void cerrarSesion(String tokenAcceso);
}
