package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Pago;
import java.util.List;

/** Una página de pagos y el total que cumple el filtro. */
public record PaginaPagos(List<Pago> contenido, long total) {
}
