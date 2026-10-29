package co.edu.futdata.datasource.external;

/**
 * n8n respondió {@code 400}: el evento no está soportado o los {@code datos} no cumplen el contrato.
 * Es un error de programación del servicio que armó los datos; reintentar no sirve.
 */
public class NotificacionRechazadaException extends NotificacionException {

    private final String detalle;

    public NotificacionRechazadaException(String evento, String detalle) {
        super("n8n rechazó la notificación " + evento + " (400): los datos no cumplen el contrato"
                + (detalle != null ? ". Detalle: " + detalle : ""), evento, 400, null);
        this.detalle = detalle;
    }

    /**
     * Explicación de n8n ({@code detalle} o, si no viene, {@code error}), o {@code null} si no vino o
     * parecía tener datos sensibles.
     */
    public String getDetalle() {
        return detalle;
    }
}
