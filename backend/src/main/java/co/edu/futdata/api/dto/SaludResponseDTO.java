package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de {@code GET /api/health}.
 *
 * @param estado      estado de la aplicación; si responde, siempre es {@code UP}
 * @param baseDeDatos {@code UP} si la base de datos respondió a {@code SELECT 1}, si no {@code DOWN}
 * @param version     versión del backend (la del {@code pom.xml})
 */
public record SaludResponseDTO(
        @Schema(example = "UP") String estado,
        @Schema(example = "UP", allowableValues = {"UP", "DOWN"}) String baseDeDatos,
        @Schema(example = "0.1.0-SNAPSHOT") String version) {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    public boolean baseDeDatosDisponible() {
        return UP.equals(baseDeDatos);
    }
}
