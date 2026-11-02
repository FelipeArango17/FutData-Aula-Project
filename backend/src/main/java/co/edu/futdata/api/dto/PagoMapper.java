package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.Pago;

/** {@link Pago} -> DTO. */
public final class PagoMapper {

    private PagoMapper() {
    }

    public static PagoResponseDTO toDTO(Pago p) {
        return new PagoResponseDTO(p.id(), p.idDeportista(), p.nombreDeportista(), p.tipo(), p.concepto(), p.monto(), p.periodo(),
                p.fechaVencimiento(), p.estado(), p.vencido(), p.fechaPago(), p.metodo() == null ? null : p.metodo().name(),
                p.referenciaWompi(), p.idTransaccionWompi(), p.numeroComprobante(), p.notaInterna());
    }

    public static MiPagoResponseDTO toMiDTO(Pago p) {
        return new MiPagoResponseDTO(p.id(), p.idDeportista(), p.nombreDeportista(), p.tipo(), p.concepto(), p.monto(), p.periodo(),
                p.fechaVencimiento(), p.estado(), p.vencido(), p.fechaPago(), p.metodo() == null ? null : p.metodo().name(),
                p.estado() == EstadoPago.APROBADO && p.tieneComprobante());
    }
}
