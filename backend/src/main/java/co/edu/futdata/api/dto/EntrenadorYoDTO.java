package co.edu.futdata.api.dto;

import java.util.List;
import java.util.UUID;

/** El entrenador ligado a la cuenta y sus categorías en el orden oficial, dentro de {@code GET /api/usuarios/yo}. */
public record EntrenadorYoDTO(UUID idEntrenador, List<CategoriaYoDTO> categorias) {
}
