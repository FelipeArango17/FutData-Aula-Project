package co.edu.futdata.persistence.impl;

import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SesionVerificadaDAOImpl implements ISesionVerificadaDAO {
    private final NamedParameterJdbcTemplate jdbc;

    public SesionVerificadaDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Una sesión de otro usuario con el mismo id no se toca: el {@code where} del {@code do update} lo impide. */
    @Override
    public void marcarVerificada(UUID idSesion, UUID idUsuario, Instant verificadaEn, Instant expiraEn) {
        jdbc.update("""
                insert into sesion_verificada (id_sesion, id_usuario, verificada_en, expira_en)
                values (:sesion, :usuario, :verificada, :expira)
                on conflict (id_sesion) do update
                   set verificada_en = excluded.verificada_en, expira_en = excluded.expira_en
                 where sesion_verificada.id_usuario = excluded.id_usuario
                """, new MapSqlParameterSource()
                .addValue("sesion", idSesion)
                .addValue("usuario", idUsuario)
                .addValue("verificada", fechaJDBC(verificadaEn), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("expira", fechaJDBC(expiraEn), Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public boolean estaVerificada(UUID idSesion, UUID idUsuario, Instant ahora) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from sesion_verificada
                     where id_sesion = :sesion and id_usuario = :usuario and expira_en > :ahora
                )
                """, new MapSqlParameterSource()
                        .addValue("sesion", idSesion)
                        .addValue("usuario", idUsuario)
                        .addValue("ahora", fechaJDBC(ahora), Types.TIMESTAMP_WITH_TIMEZONE), Boolean.class));
    }

    @Override
    public int eliminar(UUID idSesion, UUID idUsuario) {
        return jdbc.update("delete from sesion_verificada where id_sesion = :sesion and id_usuario = :usuario",
                Map.of("sesion", idSesion, "usuario", idUsuario));
    }

    @Override
    public int eliminarDeUsuario(UUID idUsuario) {
        return jdbc.update("delete from sesion_verificada where id_usuario = :usuario", Map.of("usuario", idUsuario));
    }

    @Override
    public void limpiarVencidas(UUID idUsuario, Instant ahora) {
        jdbc.update("delete from sesion_verificada where id_usuario = :usuario and expira_en <= :ahora",
                new MapSqlParameterSource()
                        .addValue("usuario", idUsuario)
                        .addValue("ahora", fechaJDBC(ahora), Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private static OffsetDateTime fechaJDBC(Instant fecha) {
        return OffsetDateTime.ofInstant(fecha, ZoneOffset.UTC);
    }
}
