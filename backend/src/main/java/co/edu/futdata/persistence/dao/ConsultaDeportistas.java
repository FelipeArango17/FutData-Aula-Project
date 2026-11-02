package co.edu.futdata.persistence.dao;

import java.time.LocalDate;

/**
 * Filtros, orden y página de la lista de deportistas, ya validados por el servicio.
 *
 * @param activo      {@code null} = todos
 * @param nacidoDesde junto con {@code nacidoHasta}, rango de fechas de nacimiento de una categoría (lo calcula el
 *                    servicio con el año del reloj); ambos {@code null} = todas las categorías
 * @param pagina      desde 0
 * @param tamano      de 1 a 100
 */
public record ConsultaDeportistas(Boolean activo, LocalDate nacidoDesde, LocalDate nacidoHasta,
                                  OrdenDeportista orden, boolean descendente, int pagina, int tamano) {
}
