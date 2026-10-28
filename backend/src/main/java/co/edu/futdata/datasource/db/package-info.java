/**
 * <b>Fuente de datos: base de datos</b> ({@code ConexionPool} en los diagramas del PPI).
 *
 * <p>La conexión a PostgreSQL de Supabase es un pool <b>HikariCP</b> que Spring Boot crea a partir
 * de {@code spring.datasource.*} (variables {@code DB_URL}, {@code DB_USER} y {@code DB_PASSWORD},
 * con {@code sslmode=require}). Spring expone ese pool como el {@code DataSource} que reciben los
 * DAO a través de {@code JdbcTemplate}; por eso hoy este paquete no necesita clases.
 *
 * <p>Aquí irían ajustes propios de la fuente de datos si algún día hacen falta (por ejemplo, un
 * segundo {@code DataSource} o tipos personalizados de PostgreSQL).
 */
package co.edu.futdata.datasource.db;
