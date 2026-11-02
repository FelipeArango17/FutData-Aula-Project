package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.TipoPago;
import java.time.LocalDate;
import java.util.UUID;

/** Un cobro para el administrador y el tesorero. {@code vencido}: PENDIENTE con vencimiento anterior a hoy. */
public record PagoResponseDTO(
        UUID id,
        UUID idDeportista,
        String nombreDeportista,
        TipoPago tipo,
        String concepto,
        long monto,
        LocalDate periodo,
        LocalDate fechaVencimiento,
        EstadoPago estado,
        boolean vencido,
        LocalDate fechaPago,
        String metodo,
        String referenciaWompi,
        String idTransaccionWompi,
        String numeroComprobante,
        String notaInterna) {
}
