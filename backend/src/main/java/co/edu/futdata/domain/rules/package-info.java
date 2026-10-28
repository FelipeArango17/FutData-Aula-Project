/**
 * <b>Dominio: reglas de negocio</b>.
 *
 * <p>Clases {@code Reglas<Recurso>} con las validaciones y constantes del negocio que no dependen
 * de la base de datos ni de HTTP, por ejemplo {@code ReglasVerificacion} ({@code VIGENCIA_CODIGO},
 * {@code MAX_INTENTOS}). Las usa la implementación del servicio. Al ser código Java puro se prueban
 * con pruebas unitarias sin Spring.
 */
package co.edu.futdata.domain.rules;
