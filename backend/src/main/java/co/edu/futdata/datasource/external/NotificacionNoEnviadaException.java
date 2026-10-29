package co.edu.futdata.datasource.external;

/**
 * No se pudo confirmar el envío: n8n respondió {@code 502} (falló el SMTP), otro código inesperado, se
 * agotó el tiempo de espera o hubo un error de red. Hay que avisarle al usuario.
 *
 * <p><b>No reintentar automáticamente</b>: si n8n recibió la petición y solo se demoró la respuesta, el
 * correo saldría dos veces. En {@link INotificador#CODIGO_2FA} el usuario tiene "Reenviar código".
 */
public class NotificacionNoEnviadaException extends NotificacionException {

    public NotificacionNoEnviadaException(String mensaje, String evento, Integer codigoHttp, Throwable causa) {
        super(mensaje, evento, codigoHttp, causa);
    }
}
