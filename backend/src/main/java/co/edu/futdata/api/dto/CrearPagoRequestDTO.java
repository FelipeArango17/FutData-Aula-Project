package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.TipoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Crear un cobro PENDIENTE.
 *
 * @param monto   pesos enteros, mayor que 0
 * @param periodo primer día del mes; obligatorio en MENSUALIDAD (si llega otro día del mes se normaliza al día 1)
 * @param concepto si es MENSUALIDAD y no llega, se arma ("Mensualidad octubre 2026")
 */
public record CrearPagoRequestDTO(
        UUID idDeportista,
        TipoPago tipo,
        @Schema(example = "Mensualidad octubre 2026") String concepto,
        @Schema(example = "90000") Long monto,
        @Schema(example = "2026-10-05") LocalDate fechaVencimiento,
        @Schema(example = "2026-10-01") LocalDate periodo) {
}
