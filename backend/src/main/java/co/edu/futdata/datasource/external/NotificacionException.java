package co.edu.futdata.datasource.external;

import java.util.OptionalInt;

/**
 * Falla al pedir una notificación con {@link INotificador}. Sirve para atrapar todas las fallas juntas;
 * cada caso tiene su subclase.
 *
 * <p>El mensaje está en español y <b>nunca</b> contiene datos de la petición (código 2FA, correos,
 * nombres, destinatarios) ni el token, así que se puede registrar en logs.
 */
public abstract class NotificacionException extends RuntimeException {

    private final String evento;
    private final Integer codigoHttp;

    protected NotificacionException(String mensaje, String evento, Integer codigoHttp, Throwable causa) {
        super(mensaje, causa);
        this.evento = evento;
        this.codigoHttp = codigoHttp;
    }

    /** Evento que se intentó notificar, por ejemplo {@link INotificador#CODIGO_2FA}. */
    public String getEvento() {
        return evento;
    }

    /** Código HTTP que respondió n8n; vacío si no hubo respuesta (timeout, error de red, sin configuración). */
    public OptionalInt getCodigoHttp() {
        return codigoHttp == null ? OptionalInt.empty() : OptionalInt.of(codigoHttp);
    }
}
