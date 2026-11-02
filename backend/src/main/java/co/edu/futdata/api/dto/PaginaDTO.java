package co.edu.futdata.api.dto;

import java.util.List;

/**
 * Una página de resultados.
 *
 * @param content       los elementos de la página (vacía si {@code page} pasa de la última)
 * @param page          número de página, desde 0
 * @param size          tamaño de página pedido
 * @param totalElements total de elementos que cumplen los filtros
 * @param totalPages    total de páginas con ese tamaño
 */
public record PaginaDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PaginaDTO<T> de(List<T> content, int page, int size, long totalElements) {
        return new PaginaDTO<>(content, page, size, totalElements, (int) ((totalElements + size - 1) / size));
    }
}
