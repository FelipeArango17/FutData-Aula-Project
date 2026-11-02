package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.TipoPago;
import java.time.LocalDate;
import java.util.UUID;

/** Filtros opcionales del listado de pagos ({@code null} = sin filtrar). {@code periodo}: primer día del mes. */
public record FiltroPagos(EstadoPago estado, TipoPago tipo, UUID idDeportista, LocalDate periodo) {
}
