package co.edu.futdata.domain.entities;

import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un cobro (tabla {@code pago}). Es un {@code record} inmutable: cada cambio de estado se hace con el DAO y se vuelve
 * a leer. Montos en pesos enteros.
 *
 * @param referenciaWompi       {@code FD-<id_pago>}, se asigna al iniciar el pago en línea; Wompi la reusa en los reintentos
 *                              del checkout
 * @param idTransaccionWompi    ID de la transacción aprobada (solo si se aprobó por Wompi)
 * @param vencido               {@code PENDIENTE} con vencimiento anterior a hoy (vista {@code pago_con_estado})
 * @param nombreDeportista      nombre completo, solo para listados (puede ser {@code null} si no se pidió)
 */
public record Pago(
        UUID id,
        UUID idDeportista,
        TipoPago tipo,
        String concepto,
        long monto,
        LocalDate periodo,
        LocalDate fechaVencimiento,
        EstadoPago estado,
        LocalDate fechaPago,
        MetodoPago metodo,
        String referenciaWompi,
        String idTransaccionWompi,
        String numeroComprobante,
        String notaInterna,
        UUID registradoPor,
        Instant creadoEn,
        boolean vencido,
        String nombreDeportista) {

    public boolean tieneComprobante() {
        return numeroComprobante != null;
    }
}
