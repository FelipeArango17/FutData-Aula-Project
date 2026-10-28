/**
 * <b>Presentación: objetos de transferencia de datos</b> (patrón DTO).
 *
 * <p>Lo que viaja por la API: cuerpos de entrada ({@code <Recurso>RequestDTO}, con anotaciones de
 * Bean Validation) y de salida ({@code <Recurso>ResponseDTO}). Preferir {@code record} inmutables.
 * La entidad de dominio nunca se expone por la API; los DTO definen el contrato con el frontend.
 *
 * <p>Los <b>mappers</b> ({@code <Recurso>Mapper}) convierten entre DTO y entidad
 * ({@code toEntity}, {@code toDTO}). Según la leyenda de los diagramas del PPI pertenecen a esta capa
 * (DTO / Mapper) y los usa la implementación del servicio.
 */
package co.edu.futdata.api.dto;
