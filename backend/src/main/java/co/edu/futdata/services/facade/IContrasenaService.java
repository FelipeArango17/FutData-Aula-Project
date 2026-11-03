package co.edu.futdata.services.facade;

/**
 * Crear la contraseña con el enlace del correo (regla 9), sin sesión. Ruta pública: {@code POST /api/auth/crear-contrasena}.
 *
 * <p>Errores: {@link EnlaceInvalidoException} y {@link PoliticaContrasenaException} ({@code 400}),
 * {@link DemasiadasSolicitudesException} ({@code 429}) y {@link CrearContrasenaNoDisponibleException} ({@code 503}).
 */
public interface IContrasenaService {

    /**
     * Valida el token y la política, verifica el enlace con Supabase (lo consume), fija la contraseña y cierra la
     * sesión que abrió la verificación. No inicia sesión ni devuelve tokens.
     *
     * @param ipCliente IP del cliente para el límite de intentos; solo se usa como clave y nunca se registra completa
     */
    void crearContrasena(String token, String contrasena, String ipCliente);
}
