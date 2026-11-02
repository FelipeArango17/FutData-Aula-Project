package co.edu.futdata.persistence.impl;

import co.edu.futdata.persistence.dao.INotificacionPagoDAO;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code notificacion_pago}. */
@Repository
public class NotificacionPagoDAOImpl implements INotificacionPagoDAO {

    private final NamedParameterJdbcTemplate jdbc;

    public NotificacionPagoDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void crear(UUID idAcudiente, String mensaje) {
        jdbc.update("INSERT INTO notificacion_pago (id_acudiente, mensaje) VALUES (:acudiente, :mensaje)",
                Map.of("acudiente", idAcudiente, "mensaje", mensaje));
    }
}
