package co.edu.futdata.services.facade;

/** Webhook de Wompi: la única entrada de pagos que no viene de un usuario con JWT. */
public interface IPagoWompiService {

    /**
     * Verifica la firma del evento y, si es {@code transaction.updated}, aplica la máquina de estados al cobro de la
     * referencia, en una transacción. Con firma válida termina sin error en todos los casos (referencia desconocida,
     * evento repetido, monto distinto...): el controlador responde 200.
     *
     * @param cuerpo    el JSON crudo del evento
     * @param checksum  el encabezado {@code X-Event-Checksum} ({@code null} si no vino)
     * @throws FirmaWebhookInvalidaException firma inválida, ausente o evento ilegible ({@code 401})
     * @throws PagosNoConfiguradosException  falta {@code WOMPI_EVENTS_SECRET} ({@code 503})
     */
    void procesarEvento(String cuerpo, String checksum);
}
