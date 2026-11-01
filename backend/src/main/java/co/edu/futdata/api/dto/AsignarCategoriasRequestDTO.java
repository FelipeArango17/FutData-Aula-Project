package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Cuerpo de {@code PUT /api/entrenadores/{id}/categorias}: la lista completa de categorías que debe dirigir el
 * entrenador.
 *
 * @param categorias nombres de las categorías, por ejemplo {@code ["Semillero", "Sub-12"]}. Obligatoria; el orden y los
 *                   repetidos no importan
 * @param reasignar  {@code true} para confirmar que las categorías que hoy dirige otro entrenador pasan a este. Sin
 *                   él (o en {@code false}), si alguna tiene otro entrenador la respuesta es {@code 409}
 */
public record AsignarCategoriasRequestDTO(
        @Schema(example = "[\"Semillero\", \"Sub-12\"]", requiredMode = Schema.RequiredMode.REQUIRED) List<String> categorias,
        @Schema(example = "false", defaultValue = "false") Boolean reasignar) {

    public boolean reasignacionConfirmada() {
        return Boolean.TRUE.equals(reasignar);
    }
}
