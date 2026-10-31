package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeportistaDAOImpl implements IDeportistaDAO {

    private final NamedParameterJdbcTemplate jdbc;
    private static final RowMapper<Deportista> FILA = DeportistaDAOImpl::mapear;

    public DeportistaDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeDocumento(String documento) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from deportista where documento_identidad = :documento)",
                Map.of("documento", documento), Boolean.class));
    }

    @Override
    public List<Deportista> listar() {
        return jdbc.query("""
                select id_deportista, nombre, apellido, tipo_documento, documento_identidad,
                       fecha_nacimiento, telefono, direccion, id_categoria
                  from deportista order by apellido, nombre
                """, FILA);
    }

    @Override
    public UUID crear(Deportista d) {
        try {
            return jdbc.queryForObject("""
                    insert into deportista (nombre, apellido, tipo_documento, documento_identidad,
                        fecha_nacimiento, telefono, direccion, id_categoria)
                    values (:nombre, :apellido, :tipoDocumento, :documento, :fechaNacimiento,
                        :telefono, :direccion, :idCategoria)
                    returning id_deportista
                    """, parametros(d), UUID.class);
        } catch (DuplicateKeyException e) {
            throw new DatoDuplicadoException("documento", e);
        }
    }

    @Override
    public Optional<Deportista> buscarPorId(UUID id) {
        return jdbc.query("""
                select id_deportista, nombre, apellido, tipo_documento, documento_identidad,
                       fecha_nacimiento, telefono, direccion, id_categoria
                  from deportista where id_deportista = :id
                """, Map.of("id", id), FILA).stream().findFirst();
    }

    private static MapSqlParameterSource parametros(Deportista d) {
        return new MapSqlParameterSource()
                .addValue("nombre", d.getNombre()).addValue("apellido", d.getApellido())
                .addValue("tipoDocumento", d.getTipoDocumento()).addValue("documento", d.getDocumento())
                .addValue("fechaNacimiento", d.getFechaNacimiento()).addValue("telefono", d.getTelefono())
                .addValue("direccion", d.getDireccion()).addValue("idCategoria", d.getIdCategoria());
    }

    private static Deportista mapear(ResultSet r, int n) throws SQLException {
        Deportista d = new Deportista();
        d.setId(r.getObject("id_deportista", UUID.class));
        d.setNombre(r.getString("nombre")); d.setApellido(r.getString("apellido"));
        d.setTipoDocumento(r.getString("tipo_documento")); d.setDocumento(r.getString("documento_identidad"));
        d.setFechaNacimiento(r.getObject("fecha_nacimiento", LocalDate.class));
        d.setTelefono(r.getString("telefono")); d.setDireccion(r.getString("direccion"));
        d.setIdCategoria(r.getObject("id_categoria", UUID.class));
        return d;
    }
}
