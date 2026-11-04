package co.edu.futdata.api.dto;

import java.util.UUID;

/** Una categoría a cargo del entrenador, dentro de {@code GET /api/usuarios/yo}. */
public record CategoriaYoDTO(UUID idCategoria, String nombre) {
}
