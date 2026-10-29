package co.edu.futdata.datasource.external;

/**
 * El notificador no puede llamar a n8n por configuración: falta {@code N8N_BASE_URL} o
 * {@code N8N_WEBHOOK_TOKEN}, o n8n respondió {@code 401} porque el token no coincide. Hay que corregir
 * las variables de entorno; reintentar no sirve.
 */
public class NotificadorMalConfiguradoException extends NotificacionException {

    public NotificadorMalConfiguradoException(String mensaje, String evento, Integer codigoHttp) {
        super(mensaje, evento, codigoHttp, null);
    }
}
