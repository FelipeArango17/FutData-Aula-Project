package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.Acudiente;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IAcudienteDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@link IAcudienteDAO} con JDBC. Sin registros: ni correos, ni nombres, ni teléfonos. */
@Repository
public class AcudienteDAOImpl implements IAcudienteDAO {

    private static final String SELECT = """
            select id_acudiente, nombre, apellido, correo, telefono, id_usuario
              from acudiente
            """;

    private static final RowMapper<Acudiente> FILA = AcudienteDAOImpl::mapear;

    private final NamedParameterJdbcTemplate jdbc;

    public AcudienteDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Acudiente> buscarPorCorreo(String correo) {
        if (correo == null) {
            return Optional.empty();
        }
        return jdbc.query(SELECT + " where lower(correo) = lower(btrim(:correo))",
                new MapSqlParameterSource("correo", correo), FILA).stream().findFirst();
    }

    @Override
    public Optional<Acudiente> buscarPorUsuario(UUID idUsuario) {
        return jdbc.query(SELECT + " where id_usuario = :usuario",
                new MapSqlParameterSource("usuario", idUsuario), FILA).stream().findFirst();
    }

    @Override
    public Acudiente insertar(String nombre, String apellido, String correo, String telefono, UUID idUsuario) {
        try {
            return jdbc.queryForObject("""
                    insert into acudiente (nombre, apellido, correo, telefono, id_usuario)
                    values (:nombre, :apellido, lower(btrim(:correo)), :telefono, :usuario)
                    returning id_acudiente, nombre, apellido, correo, telefono, id_usuario""",
                    new MapSqlParameterSource("nombre", nombre).addValue("apellido", apellido)
                            .addValue("correo", correo).addValue("telefono", telefono).addValue("usuario", idUsuario),
                    FILA);
        } catch (DuplicateKeyException e) {
            throw new DatoDuplicadoException(campoRepetido(e), e);
        }
    }

    @Override
    public boolean vincular(UUID idAcudiente, UUID idDeportista, String parentesco) {
        return jdbc.update("""
                insert into acudiente_deportista (id_acudiente, id_deportista, parentesco)
                values (:acudiente, :deportista, :parentesco)
                on conflict (id_acudiente, id_deportista) do nothing""",
                new MapSqlParameterSource("acudiente", idAcudiente).addValue("deportista", idDeportista)
                        .addValue("parentesco", parentesco)) > 0;
    }

    @Override
    public boolean enlazarUsuario(UUID idAcudiente, UUID idUsuario) {
        try {
            return jdbc.update("update acudiente set id_usuario = :usuario where id_acudiente = :acudiente and id_usuario is null",
                    new MapSqlParameterSource("usuario", idUsuario).addValue("acudiente", idAcudiente)) > 0;
        } catch (DuplicateKeyException e) {
            throw new DatoDuplicadoException("cuenta", e);
        }
    }

    /** {@code cuenta} si se repitió la cuenta enlazada; en cualquier otro caso, el correo. */
    private static String campoRepetido(DuplicateKeyException e) {
        String mensaje = e.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains("acudiente_id_usuario_key") ? "cuenta" : "correo";
    }

    private static Acudiente mapear(ResultSet fila, int numero) throws SQLException {
        return new Acudiente(fila.getObject("id_acudiente", UUID.class), fila.getString("nombre"),
                fila.getString("apellido"), fila.getString("correo"), fila.getString("telefono"),
                fila.getObject("id_usuario", UUID.class));
    }
}
