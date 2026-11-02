package co.edu.futdata.services.facade;

/** El evento del webhook de Wompi no trae una firma válida (o no se puede leer). Se responde {@code 401} y no se toca nada. */
public class FirmaWebhookInvalidaException extends RuntimeException {

    public FirmaWebhookInvalidaException() {
        super("La firma del evento no es válida.");
    }
}
