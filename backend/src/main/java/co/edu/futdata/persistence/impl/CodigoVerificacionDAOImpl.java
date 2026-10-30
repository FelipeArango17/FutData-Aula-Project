package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.CodigoVerificacion;
import co.edu.futdata.persistence.dao.ICodigoVerificacionDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
                new MapSqlParameterSource()
                        .addValue("id", id)
                .addValue("hasta", fechaJDBC(hasta), Types.TIMESTAMP_WITH_TIMEZONE));
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
                .addValue("expira", fechaJDBC(codigo.getExpiraEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("envio", fechaJDBC(codigo.getUltimoEnvio()), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static OffsetDateTime fechaJDBC(Instant fecha) {
        return OffsetDateTime.ofInstant(fecha, ZoneOffset.UTC);
    }

    private static CodigoVerificacion mapear(ResultSet fila, int numero) throws SQLException {
        CodigoVerificacion c = new CodigoVerificacion();
        c.setId(fila.getObject("id_codigo", UUID.class));
        c.setUsuarioId(fila.getObject("id_usuario", UUID.class));
        c.setCodigoHash(fila.getString("codigo_hash"));
        c.setExpiraEn(fechaInstant(fila, "expira_en"));
        c.setIntentos(fila.getInt("intentos"));
        c.setBloqueadoHasta(fechaInstant(fila, "bloqueado_hasta"));
        c.setUltimoEnvio(fechaInstant(fila, "ultimo_envio"));
        c.setUsado(fila.getBoolean("usado"));
        return c;
    }

    private static Instant fechaInstant(ResultSet fila, String columna) throws SQLException {
        OffsetDateTime fecha = fila.getObject(columna, OffsetDateTime.class);
        return fecha == null ? null : fecha.toInstant();
    }
}
