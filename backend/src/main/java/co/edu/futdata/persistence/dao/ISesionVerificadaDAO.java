package co.edu.futdata.persistence.dao;

import java.time.Instant;
import java.util.UUID;

/**
 * Sesiones de Supabase Auth que ya completaron el segundo paso (tabla {@code sesion_verificada}, migración 006).
 * Cada sesión se identifica por el claim {@code session_id} del JWT: verificar una no habilita las demás del usuario.
 */
public interface ISesionVerificadaDAO {

    /** Marca la sesión como verificada hasta {@code expiraEn}; si ya existía del mismo usuario, renueva la vigencia. */
    void marcarVerificada(UUID idSesion, UUID idUsuario, Instant verificadaEn, Instant expiraEn);

    /** {@code true} si la sesión es de ese usuario, está verificada y no ha vencido. */
    boolean estaVerificada(UUID idSesion, UUID idUsuario, Instant ahora);

    /** Borra la sesión si es de ese usuario (cierre de sesión). Devuelve las filas borradas. */
    int eliminar(UUID idSesion, UUID idUsuario);

    /** Borra todas las sesiones verificadas del usuario (acceso revocado). Devuelve las filas borradas. */
    int eliminarDeUsuario(UUID idUsuario);

    /** Borra las sesiones vencidas del usuario. */
    void limpiarVencidas(UUID idUsuario, Instant ahora);
}
