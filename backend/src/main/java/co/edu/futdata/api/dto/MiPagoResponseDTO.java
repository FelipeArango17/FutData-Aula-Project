package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.TipoPago;
import java.time.LocalDate;
import java.util.UUID;

/** Un cobro para el acudiente: sin nota interna, sin quién lo registró y sin datos internos de Wompi. */
public record MiPagoResponseDTO(
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
        boolean tieneComprobante) {
}
