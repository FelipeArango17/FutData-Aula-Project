package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code franja_horario} con {@link NamedParameterJdbcTemplate}, unida a {@code categoria} y {@code entrenador}. */
@Repository
public class FranjaHorarioDAOImpl implements IFranjaHorarioDAO {

    /** Restricción de exclusión de la migración 007: el mismo entrenador, el mismo día, con horas que se solapan. */
    static final String CRUCE_ENTRENADOR = "franja_cruce_entrenador_ex";

    private static final String CONSULTA = """
            SELECT f.id_franja, f.id_categoria, c.nombre AS categoria, f.dia, f.hora_inicio, f.hora_fin, f.sede,
                   f.id_entrenador, e.nombre || ' ' || e.apellido AS entrenador, e.activo AS entrenador_activo, f.grupo
              FROM franja_horario f
              JOIN categoria c ON c.id_categoria = f.id_categoria
              LEFT JOIN entrenador e ON e.id_entrenador = f.id_entrenador""";

    private static final String ORDEN = " ORDER BY f.dia, f.hora_inicio, c.edad_minima, f.id_franja";

    private static final RowMapper<FranjaHorario> FILA_A_ENTIDAD = FranjaHorarioDAOImpl::aEntidad;

    private final NamedParameterJdbcTemplate jdbc;

    public FranjaHorarioDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FranjaHorario> listar(String categoria, UUID idEntrenador, String sede) {
        List<String> condiciones = new ArrayList<>();
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        if (categoria != null) {
            condiciones.add("c.nombre = :categoria");
            parametros.addValue("categoria", categoria);
        }
        if (idEntrenador != null) {
            condiciones.add("f.id_entrenador = :entrenador");
            parametros.addValue("entrenador", idEntrenador);
        }
        if (sede != null) {
            condiciones.add("f.sede = :sede");
            parametros.addValue("sede", sede);
        }
        String donde = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
        return jdbc.query(CONSULTA + donde + ORDEN, parametros, FILA_A_ENTIDAD);
    }

    @Override
    public Optional<FranjaHorario> buscarPorId(UUID id) {
        return jdbc.query(CONSULTA + " WHERE f.id_franja = :id", new MapSqlParameterSource("id", id), FILA_A_ENTIDAD)
                .stream().findFirst();
    }

    @Override
    public Optional<String> nombreDeCategoria(UUID idCategoria) {
        return jdbc.queryForList("SELECT nombre FROM categoria WHERE id_categoria = :id",
                new MapSqlParameterSource("id", idCategoria), String.class).stream().findFirst();
    }

    @Override
    public UUID crear(FranjaHorario f) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, id_entrenador, grupo)
                    VALUES (:idCategoria, :dia, :horaInicio, :horaFin, :sede, :idEntrenador, :grupo)
                    RETURNING id_franja""", parametros(f), UUID.class);
        } catch (DataIntegrityViolationException ex) {
            throw traducir(ex);
        }
    }

    @Override
    public boolean actualizar(FranjaHorario f) {
        // La restricción de exclusión compara la fila nueva contra las demás: la franja no choca consigo misma
        try {
            return jdbc.update("""
                    UPDATE franja_horario
                       SET id_categoria = :idCategoria, dia = :dia, hora_inicio = :horaInicio, hora_fin = :horaFin,
                           sede = :sede, id_entrenador = :idEntrenador, grupo = :grupo
                     WHERE id_franja = :id""", parametros(f).addValue("id", f.getId())) > 0;
        } catch (DataIntegrityViolationException ex) {
            throw traducir(ex);
        }
    }

    @Override
    public boolean eliminar(UUID id) {
        return jdbc.update("DELETE FROM franja_horario WHERE id_franja = :id", new MapSqlParameterSource("id", id)) > 0;
    }

    private static MapSqlParameterSource parametros(FranjaHorario f) {
        return new MapSqlParameterSource()
                .addValue("idCategoria", f.getIdCategoria())
                .addValue("dia", f.getDia())
                .addValue("horaInicio", f.getHoraInicio())
                .addValue("horaFin", f.getHoraFin())
                .addValue("sede", f.getSede())
                .addValue("idEntrenador", f.getIdEntrenador())
                .addValue("grupo", f.getGrupo());
    }

    /**
     * Reconoce el cruce por el nombre de la restricción, que PostgreSQL pone entre comillas en el mensaje en
     * cualquier idioma (SQLState 23P01). Las demás violaciones siguen al manejador global ({@code 409} genérico).
     */
    private static RuntimeException traducir(DataIntegrityViolationException ex) {
        String mensaje = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (mensaje.contains("\"" + CRUCE_ENTRENADOR + "\"")) {
            return new CruceDeHorarioException(ex);
        }
        return ex;
    }

    private static FranjaHorario aEntidad(ResultSet fila, int numero) throws SQLException {
        FranjaHorario f = new FranjaHorario();
        f.setId(fila.getObject("id_franja", UUID.class));
        f.setIdCategoria(fila.getObject("id_categoria", UUID.class));
        f.setCategoria(fila.getString("categoria"));
        f.setDia(fila.getInt("dia"));
        f.setHoraInicio(fila.getObject("hora_inicio", LocalTime.class));
        f.setHoraFin(fila.getObject("hora_fin", LocalTime.class));
        f.setSede(fila.getString("sede"));
        f.setIdEntrenador(fila.getObject("id_entrenador", UUID.class));
        f.setEntrenador(fila.getString("entrenador"));
        f.setEntrenadorActivo(fila.getObject("entrenador_activo", Boolean.class));
        f.setGrupo(fila.getString("grupo"));
        return f;
    }
}
