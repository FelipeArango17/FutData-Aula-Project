package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.CodigoVerificacion;
import co.edu.futdata.persistence.dao.ICodigoVerificacionDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CodigoVerificacionDAOImpl implements ICodigoVerificacionDAO {
    private static final RowMapper<CodigoVerificacion> MAPEO = CodigoVerificacionDAOImpl::mapear;
    private final NamedParameterJdbcTemplate jdbc;

    public CodigoVerificacionDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CodigoVerificacion> buscarUltimo(UUID usuarioId) {
        return jdbc.query("""
                select id_codigo, id_usuario, codigo_hash, expira_en, intentos, bloqueado_hasta,
                       ultimo_envio, usado
                  from codigo_verificacion
                 where id_usuario = :usuario
                 order by creado_en desc
                 limit 1
                """, Map.of("usuario", usuarioId), MAPEO).stream().findFirst();
    }

    @Override
    public UUID guardar(CodigoVerificacion codigo) {
        return jdbc.queryForObject("""
                insert into codigo_verificacion
                    (id_usuario, codigo_hash, expira_en, intentos, bloqueado_hasta, ultimo_envio, usado)
                values (:usuario, :hash, :expira, 0, null, :envio, false)
                returning id_codigo
                """, parametros(codigo), UUID.class);
    }

    @Override
    public int incrementarIntentos(UUID id) {
        return jdbc.queryForObject("""
                update codigo_verificacion
                   set intentos = intentos + 1
                 where id_codigo = :id and not usado
                returning intentos
                """, Map.of("id", id), Integer.class);
    }

    @Override
    public void bloquear(UUID id, Instant hasta) {
        jdbc.update("update codigo_verificacion set bloqueado_hasta = :hasta where id_codigo = :id",
                Map.of("id", id, "hasta", hasta));
    }

    @Override
    public void marcarUsado(UUID id) {
        jdbc.update("update codigo_verificacion set usado = true where id_codigo = :id",
                Map.of("id", id));
    }

    private static MapSqlParameterSource parametros(CodigoVerificacion codigo) {
        return new MapSqlParameterSource()
                .addValue("usuario", codigo.getUsuarioId())
                .addValue("hash", codigo.getCodigoHash())
                .addValue("expira", codigo.getExpiraEn())
                .addValue("envio", codigo.getUltimoEnvio());
    }

    private static CodigoVerificacion mapear(ResultSet fila, int numero) throws SQLException {
        CodigoVerificacion c = new CodigoVerificacion();
        c.setId(fila.getObject("id_codigo", UUID.class));
        c.setUsuarioId(fila.getObject("id_usuario", UUID.class));
        c.setCodigoHash(fila.getString("codigo_hash"));
        c.setExpiraEn(fila.getObject("expira_en", Instant.class));
        c.setIntentos(fila.getInt("intentos"));
        c.setBloqueadoHasta(fila.getObject("bloqueado_hasta", Instant.class));
        c.setUltimoEnvio(fila.getObject("ultimo_envio", Instant.class));
        c.setUsado(fila.getBoolean("usado"));
        return c;
    }
}
