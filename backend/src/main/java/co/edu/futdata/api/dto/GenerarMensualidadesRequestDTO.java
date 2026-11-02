package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;

/** Periodo (mes) del que se generan las mensualidades. */
public record GenerarMensualidadesRequestDTO(@Schema(example = "2026-11", type = "string") YearMonth periodo) {
}
