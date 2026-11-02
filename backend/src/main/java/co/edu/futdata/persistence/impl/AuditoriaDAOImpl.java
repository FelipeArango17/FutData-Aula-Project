package co.edu.futdata.persistence.impl;

import co.edu.futdata.persistence.dao.IAuditoriaDAO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code auditoria} (migración 004): fecha, usuario, evento y detalle JSON. */
@Repository
public class AuditoriaDAOImpl implements IAuditoriaDAO {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaDAOImpl(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void registrar(UUID idUsuario, String evento, Map<String, Object> detalle) {
        String texto;
        try {
            texto = json.writeValueAsString(detalle);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El detalle de la auditoría no se puede convertir a JSON");
        }
        jdbc.update("INSERT INTO auditoria (id_usuario, evento, detalle) VALUES (:usuario, :evento, CAST(:detalle AS jsonb))",
                new MapSqlParameterSource().addValue("usuario", idUsuario).addValue("evento", evento).addValue("detalle", texto));
    }
}
