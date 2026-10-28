/**
 * <b>Dominio: entidades</b>.
 *
 * <p>Clases del modelo de dominio del PPI (diagrama de dominio y diccionario de datos), por ejemplo
 * {@code Usuario}, {@code Deportista} o {@code Entrenamiento}. Son objetos Java simples: sin
 * anotaciones de Spring, de JPA ni de JSON. Las construye el DAO a partir de las filas de la base de
 * datos y nunca salen por la API (para eso están los DTO).
 *
 * <p>Este proyecto base no define entidades: cada historia de usuario agrega las suyas.
 */
package co.edu.futdata.domain.entities;
