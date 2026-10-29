/**
 * <b>Persistencia: implementaciones JDBC de los DAO</b> (patrón DAO).
 *
 * <p>Clases {@code @Repository} llamadas {@code <Recurso>DAOImpl} que implementan la interfaz de
 * {@code persistence.dao} con SQL explícito sobre {@code JdbcTemplate} o
 * {@code NamedParameterJdbcTemplate} y un {@code RowMapper} que construye la entidad. Todo el SQL
 * del sistema vive aquí. Siempre con parámetros ({@code ?} o {@code :nombre}), nunca concatenando
 * valores en el SQL.
 *
 * <p>No se usa JPA, Hibernate ni Spring Data: el PPI define DAO con implementación JDBC.
 */
package co.edu.futdata.persistence.impl;
