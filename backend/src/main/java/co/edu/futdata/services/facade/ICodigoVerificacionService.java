package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import java.util.UUID;

/**
 * Segundo paso del inicio de sesión: código de 6 dígitos por correo (HU-002, CU-00).
 *
 * <p>Se llama después del primer paso (correo y contraseña en Supabase Auth), con el JWT ya validado: el usuario es
 * el {@code sub} del token, nunca un correo enviado en la petición. Así nadie puede pedir ni probar códigos de otra
 * cuenta.
 */
public interface ICodigoVerificacionService {

    /** Envía un código nuevo, o informa el vigente si se envió hace menos de un minuto. */
    Codigo2FAResponseDTO solicitar(UUID idUsuario);

    /** Envía otro código; como máximo uno por minuto. */
    Codigo2FAResponseDTO reenviar(UUID idUsuario);

    /**
     * Valida el código del usuario; tras 5 intentos fallidos bloquea la verificación 15 minutos. Si es correcto, marca
     * como verificada solo la sesión {@code idSesion} (claim {@code session_id} del JWT), no las demás del usuario.
     */
    UsuarioResponseDTO verificar(UUID idUsuario, UUID idSesion, String codigo);

    /** Cierre de sesión: la sesión {@code idSesion} del usuario deja de estar verificada. */
    void cerrarSesion(UUID idUsuario, UUID idSesion);
}
