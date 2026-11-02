package co.edu.futdata.services.impl;

import co.edu.futdata.config.WompiProperties;
import co.edu.futdata.datasource.external.TransaccionWompi;
import co.edu.futdata.datasource.external.WompiClient;
import co.edu.futdata.datasource.external.WompiFirmas;
import co.edu.futdata.persistence.dao.IPagoDAO;
import co.edu.futdata.services.facade.FirmaWebhookInvalidaException;
import co.edu.futdata.services.facade.IPagoWompiService;
import co.edu.futdata.services.facade.PagosNoConfiguradosException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Webhook de Wompi: firma en tiempo constante, un solo tipo de evento y todo cambio de estado en una transacción. */
@Service
public class PagoWompiServiceImpl implements IPagoWompiService {

    static final String EVENTO_TRANSACCION = "transaction.updated";
    private static final Logger log = LoggerFactory.getLogger(PagoWompiServiceImpl.class);

    private final WompiProperties wompi;
    private final IPagoDAO pagos;
    private final AplicadorTransaccionWompi aplicador;
    private final ObjectMapper json;
    private final TransactionTemplate transaccion;

    public PagoWompiServiceImpl(WompiProperties wompi, IPagoDAO pagos, AplicadorTransaccionWompi aplicador, ObjectMapper json,
                                PlatformTransactionManager administrador) {
        this.wompi = wompi;
        this.pagos = pagos;
        this.aplicador = aplicador;
        this.json = json;
        this.transaccion = new TransactionTemplate(administrador);
    }

    /** La configuración y la firma se revisan <b>antes</b> de abrir la transacción: un evento falso no toca la base de datos. */
    @Override
    public void procesarEvento(String cuerpo, String checksum) {
        if (!wompi.webhookConfigurado()) {
            log.error("Llegó un evento de Wompi pero WOMPI_EVENTS_SECRET falta o no es del ambiente de WOMPI_BASE_URL");
            throw new PagosNoConfiguradosException();
        }
        JsonNode evento = leer(cuerpo);
        if (!WompiFirmas.eventoValido(evento, checksum, wompi.eventsSecret())) {
            log.warn("Evento de Wompi rechazado: firma inválida");
            throw new FirmaWebhookInvalidaException();
        }
        if (!EVENTO_TRANSACCION.equals(evento.path("event").asText())) {
            log.info("Evento de Wompi ignorado: no es {}", EVENTO_TRANSACCION);
            return;
        }
        TransaccionWompi t = WompiClient.aTransaccion(evento.path("data").path("transaction"));
        transaccion.executeWithoutResult(estado -> pagos.buscarPorReferenciaParaActualizar(t.referencia()).ifPresentOrElse(
                pago -> log.info("Wompi {} -> cobro {}: {}", t.estado(), pago.id(), aplicador.aplicar(pago, t)),
                () -> log.warn("Evento de Wompi con una referencia que no corresponde a ningún cobro")));
    }

    private JsonNode leer(String cuerpo) {
        try {
            JsonNode evento = cuerpo == null ? null : json.readTree(cuerpo);
            if (evento == null || !evento.isObject()) {
                throw new FirmaWebhookInvalidaException();
            }
            return evento;
        } catch (JsonProcessingException e) {
            throw new FirmaWebhookInvalidaException();
        }
    }
}
