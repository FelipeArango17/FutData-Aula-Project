package co.edu.futdata.datasource.external;

/**
 * n8n no aceptó la notificación: respondió {@code 502} (falló el SMTP en un evento síncrono), otro código
 * inesperado, se agotó el tiempo de espera o hubo un error de red. Hay que avisarle al usuario.
 *
 * <p>En los eventos asíncronos ({@link INotificador#CODIGO_2FA}, {@link INotificador#CAMBIO_ENTRENAMIENTO}) n8n
 * responde {@code 202} antes de enviar: una falla del SMTP no llega aquí, solo queda en el registro de n8n.
 *
 * <p><b>No reintentar automáticamente</b>: si n8n recibió la petición y solo se demoró la respuesta, el
 * correo saldría dos veces. En {@link INotificador#CODIGO_2FA} el usuario tiene "Reenviar código".
 */
public class NotificacionNoEnviadaException extends NotificacionException {

    public NotificacionNoEnviadaException(String mensaje, String evento, Integer codigoHttp, Throwable causa) {
        super(mensaje, evento, codigoHttp, causa);
    }
}
