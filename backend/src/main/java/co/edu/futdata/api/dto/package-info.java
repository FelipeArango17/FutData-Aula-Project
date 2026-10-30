/**
 * <b>Presentación: objetos de transferencia de datos</b> (patrón DTO).
 *
 * <p>Lo que viaja por la API: cuerpos de entrada ({@code <Recurso>RequestDTO}) y de salida
 * ({@code <Recurso>ResponseDTO}). Un RequestDTO lleva anotaciones de Bean Validation cuando basta con revisar
 * el formato; si los datos se deben limpiar antes de validarlos (quitar espacios, pasar a minúsculas), los valida
 * el servicio con {@code domain.rules}, como {@code EntrenadorRequestDTO}. Preferir {@code record} inmutables.
 * La entidad de dominio nunca se expone por la API; los DTO definen el contrato con el frontend.
 *
 * <p>Los <b>mappers</b> ({@code <Recurso>Mapper}) convierten entre DTO y entidad
 * ({@code toEntity}, {@code toDTO}). Según la leyenda de los diagramas del PPI pertenecen a esta capa
 * (DTO / Mapper) y los usa la implementación del servicio.
 */
package co.edu.futdata.api.dto;
