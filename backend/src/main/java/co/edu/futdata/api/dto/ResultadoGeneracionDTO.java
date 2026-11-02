package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuántas mensualidades se crearon y cuántos deportistas ya la tenían. */
public record ResultadoGeneracionDTO(@Schema(example = "2026-11") String periodo, int creadas, int yaExistentes) {
}
