package co.edu.futdata.persistence.impl;

import co.edu.futdata.persistence.dao.ISaludDAO;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Revisa la base de datos con {@code SELECT 1}. Obtener la conexión tarda como máximo
 * {@code spring.datasource.hikari.connection-timeout} (3 s) y la consulta, {@link #TIMEOUT_SEGUNDOS}.
 */
@Repository
public class SaludDAOImpl implements ISaludDAO {

    static final int TIMEOUT_SEGUNDOS = 2;
    private static final Logger log = LoggerFactory.getLogger(SaludDAOImpl.class);

    private final JdbcTemplate jdbc;

    public SaludDAOImpl(DataSource dataSource) {
        // JdbcTemplate propio para no cambiar el timeout del que usan los demás DAO.
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(TIMEOUT_SEGUNDOS);
    }

    @Override
    public boolean baseDeDatosDisponible() {
        try {
            return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (DataAccessException e) {
            log.warn("La base de datos no responde: {}", e.getMostSpecificCause().getMessage());
            return false;
        }
    }
}
