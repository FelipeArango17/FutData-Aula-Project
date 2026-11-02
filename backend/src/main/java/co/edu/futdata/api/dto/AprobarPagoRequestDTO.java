package co.edu.futdata.api.dto;

import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.time.LocalDate;

/** Aprobación manual: fecha de pago y método (EFECTIVO o TRANSFERENCIA) obligatorios; nota interna opcional. */
public record AprobarPagoRequestDTO(LocalDate fechaPago, MetodoPago metodo, String nota) {
}
