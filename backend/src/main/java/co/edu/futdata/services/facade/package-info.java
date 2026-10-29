/**
 * <b>Servicios: interfaces de la fachada</b> (patrón Fachada).
 *
 * <p>Una interfaz por módulo ({@code I<Recurso>Service}, por ejemplo {@code IAutenticacionService})
 * con las operaciones de negocio que ofrece al controlador. Oculta la coordinación de reglas de
 * dominio, DAO y servicios externos: el controlador depende solo de esta interfaz.
 *
 * <p>Las excepciones que la fachada puede lanzar al controlador (por ejemplo
 * {@link co.edu.futdata.services.facade.RecursoNoEncontradoException}) también se declaran aquí.
 */
package co.edu.futdata.services.facade;
