/**
 * <b>Servicios: implementaciones de la fachada</b> (patrón Fachada).
 *
 * <p>Clases {@code @Service} llamadas {@code <Recurso>ServiceImpl} que implementan la interfaz de
 * {@code services.facade}. Coordinan el caso de uso: aplican las reglas de {@code domain.rules},
 * usan los DAO por su <b>interfaz</b> ({@code persistence.dao}), llaman a servicios externos por su
 * interfaz ({@code datasource.external}) y convierten con el mapper. Aquí van las transacciones
 * ({@code @Transactional}) cuando un caso de uso modifica varias tablas.
 */
package co.edu.futdata.services.impl;
