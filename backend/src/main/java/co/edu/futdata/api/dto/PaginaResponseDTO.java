package co.edu.futdata.api.dto;

import java.util.List;

/** Página de resultados con la forma de Spring Page que espera el frontend ({@code content, totalElements, number, size}). */
public record PaginaResponseDTO<T>(List<T> content, long totalElements, int number, int size) {
}
