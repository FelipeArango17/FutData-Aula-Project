package co.edu.futdata.services.impl;

import co.edu.futdata.datasource.external.TransaccionWompi;
import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.Pago;
import co.edu.futdata.domain.rules.ReglasPago;
import co.edu.futdata.persistence.dao.IAuditoriaDAO;
import co.edu.futdata.persistence.dao.INotificacionPagoDAO;
import co.edu.futdata.persistence.dao.IPagoDAO;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * La máquina de estados de un cobro ante una transacción de Wompi. La usan el webhook y la conciliación, así que las dos
 * aplican <b>exactamente</b> las mismas reglas. <b>No abre la transacción</b>: quien llama ya tiene una abierta y el cobro
 * tomado con {@code FOR UPDATE}; todo lo que hace aquí es atómico con eso.
 *
 * <p>E = estado actual del cobro, T = estado de la transacción:
 * <pre>
 * T APPROVED, E PENDIENTE   monto igual -> APROBADO (fecha, método, id de transacción, comprobante, aviso);
 *                           monto distinto -> sin cambios + auditoría; ya hay un hermano aprobado -> sin cambios + auditoría
 * T APPROVED, E RECHAZADO   (el reintento del checkout aprobó después de un rechazo) hermano APROBADO -> sin cambios + auditoría
 *                           "posible pago duplicado"; si no, los hermanos PENDIENTES pasan a RECHAZADO ("Reemplazado...")
 *                           y luego se aprueba como en el caso anterior (el índice único de mensualidad exige ese orden)
 * T APPROVED, E APROBADO    mismo id de transacción -> se ignora (evento repetido); otro id -> auditoría, sin cambios
 * T DECLINED|ERROR|VOIDED, E PENDIENTE   -> RECHAZADO + aviso. No crea cobro nuevo (eso es /reintentar)
 * T VOIDED, E APROBADO con el mismo id   -> RECHAZADO + auditoría
 * cualquier otra combinación de DECLINED|ERROR|VOIDED, y T PENDING   -> nada
 * </pre>
 * Las auditorías no llevan llaves ni datos personales: solo ids del cobro, la referencia y los montos.
 */
@Component
public class AplicadorTransaccionWompi {

    static final String NOTA_REEMPLAZADO = "Reemplazado: el pago original fue aprobado por reintento";
    static final String EVENTO_MONTO_DISTINTO = "PAGO_MONTO_DISTINTO";
    static final String EVENTO_POSIBLE_DUPLICADO = "PAGO_POSIBLE_DUPLICADO";
    static final String EVENTO_ANULADO = "PAGO_ANULADO_WOMPI";
    static final String EVENTO_REEMPLAZADO = "PAGO_REEMPLAZADO";

    private static final Logger log = LoggerFactory.getLogger(AplicadorTransaccionWompi.class);

    private final IPagoDAO pagos;
    private final IAuditoriaDAO auditoria;
    private final INotificacionPagoDAO avisos;
    private final Clock reloj;

    public AplicadorTransaccionWompi(IPagoDAO pagos, IAuditoriaDAO auditoria, INotificacionPagoDAO avisos, Clock reloj) {
        this.pagos = pagos;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    /** @return qué se hizo, para el log y las pruebas */
    public Resultado aplicar(Pago pago, TransaccionWompi t) {
        return switch (t.estado()) {
            case "APPROVED" -> aprobada(pago, t);
            case "DECLINED", "ERROR", "VOIDED" -> noAprobada(pago, t);
            default -> Resultado.IGNORADO; // PENDING y estados que no conocemos
        };
    }

    private Resultado aprobada(Pago pago, TransaccionWompi t) {
        switch (pago.estado()) {
            case PENDIENTE:
                return aprobarSiCorresponde(pago, t);
            case RECHAZADO:
                if (pagos.hayHermanoAprobado(pago)) {
                    return duplicado(pago, t, "ya hay otro cobro equivalente aprobado");
                }
                for (Pago hermano : pagos.hermanosPendientes(pago)) {
                    pagos.rechazar(hermano.id(), NOTA_REEMPLAZADO, null);
                    auditoria.registrar(null, EVENTO_REEMPLAZADO, detalle(hermano, t, "reemplazadoPor", pago.id().toString()));
                }
                return aprobarSiCorresponde(pago, t);
            default: // APROBADO
                if (t.id().equals(pago.idTransaccionWompi())) {
                    return Resultado.IGNORADO; // el mismo evento otra vez
                }
                return duplicado(pago, t, "otra transacción aprobada para un cobro ya aprobado");
        }
    }

    private Resultado aprobarSiCorresponde(Pago pago, TransaccionWompi t) {
        if (t.montoEnCentavos() != ReglasPago.aCentavos(pago.monto())) {
            // No se aprueba nada: el monto que pagó no es el que se cobra. Lo resuelve el administrador.
            log.warn("Wompi aprobó una transacción con monto distinto al del cobro {}", pago.id());
            Map<String, Object> d = detalle(pago, t, null, null);
            d.put("montoEsperadoEnCentavos", ReglasPago.aCentavos(pago.monto()));
            d.put("montoRecibidoEnCentavos", t.montoEnCentavos());
            auditoria.registrar(null, EVENTO_MONTO_DISTINTO, d);
            return Resultado.MONTO_DISTINTO;
        }
        if (pagos.hayHermanoAprobado(pago) || pagos.transaccionYaUsada(t.id())) {
            return duplicado(pago, t, "ya hay un cobro equivalente aprobado o la transacción ya se usó");
        }
        LocalDate hoy = LocalDate.now(reloj);
        pagos.aprobar(pago.id(), hoy, ReglasPago.metodoDesdeWompi(t.tipoMetodoPago()), t.id(), pagos.siguienteNumeroComprobante(),
                null, null);
        avisar(pago, "Tu pago de " + pago.concepto() + " fue aprobado");
        return Resultado.APROBADO;
    }

    private Resultado noAprobada(Pago pago, TransaccionWompi t) {
        if (pago.estado() == EstadoPago.PENDIENTE) {
            pagos.rechazar(pago.id(), null, null);
            avisar(pago, "Tu pago fue rechazado, puedes intentarlo de nuevo");
            return Resultado.RECHAZADO;
        }
        if (pago.estado() == EstadoPago.APROBADO && "VOIDED".equals(t.estado()) && t.id().equals(pago.idTransaccionWompi())) {
            pagos.rechazar(pago.id(), "Transacción anulada en Wompi", null);
            auditoria.registrar(null, EVENTO_ANULADO, detalle(pago, t, null, null));
            return Resultado.ANULADO;
        }
        return Resultado.IGNORADO;
    }

    private Resultado duplicado(Pago pago, TransaccionWompi t, String motivo) {
        log.warn("Posible pago duplicado en el cobro {}: {}", pago.id(), motivo);
        auditoria.registrar(null, EVENTO_POSIBLE_DUPLICADO, detalle(pago, t, "motivo", motivo));
        return Resultado.POSIBLE_DUPLICADO;
    }

    private void avisar(Pago pago, String mensaje) {
        for (UUID acudiente : pagos.acudientesDe(pago.idDeportista())) {
            avisos.crear(acudiente, mensaje);
        }
    }

    private static Map<String, Object> detalle(Pago pago, TransaccionWompi t, String clave, String valor) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("idPago", pago.id().toString());
        d.put("referencia", pago.referenciaWompi());
        d.put("idTransaccion", t.id());
        d.put("estadoTransaccion", t.estado());
        d.put("estadoCobro", pago.estado().name());
        if (clave != null) {
            d.put(clave, valor);
        }
        return d;
    }

    /** Qué hizo {@link #aplicar}. */
    public enum Resultado { APROBADO, RECHAZADO, ANULADO, MONTO_DISTINTO, POSIBLE_DUPLICADO, IGNORADO }
}
