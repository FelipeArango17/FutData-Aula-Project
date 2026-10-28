/**
 * <b>Presentación: controladores REST</b> (patrón Fachada, lado del cliente).
 *
 * <p>Clases {@code @RestController} que reciben las peticiones HTTP en JSON bajo {@code /api}.
 * Un controlador solo valida la entrada ({@code @Valid}), llama a la <b>interfaz</b> del servicio
 * ({@code services.facade}) y devuelve un DTO ({@code api.dto}). No contiene reglas de negocio,
 * no usa DAO ni entidades y nunca conoce la implementación del servicio.
 *
 * <p>Aquí también vive el manejo global de errores ({@code @RestControllerAdvice}), que convierte las
 * excepciones en la respuesta JSON uniforme de error.
 *
 * <p>Nombre: {@code <Recurso>Controller}, por ejemplo {@code AuthController}.
 */
package co.edu.futdata.api.controllers;
