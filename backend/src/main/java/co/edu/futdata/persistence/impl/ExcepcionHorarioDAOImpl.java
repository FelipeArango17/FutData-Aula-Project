package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.FranjaYaCanceladaException;
import co.edu.futdata.persistence.dao.IExcepcionHorarioDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code excepcion_horario} con {@link NamedParameterJdbcTemplate}. */
@Repository
public class ExcepcionHorarioDAOImpl implements IExcepcionHorarioDAO {

    /** Índice único parcial de la migración 010: una franja no se cancela dos veces el mismo día. */
    static final String CANCELACION_UNICA = "excepcion_horario_cancelacion_unica_idx";

    /** Restricción de exclusión de la migración 010: el mismo entrenador, dos extras que se solapan el mismo día. */
    static final String CRUCE_ENTRENADOR = "excepcion_horario_cruce_entrenador_ex";

    /**
     * Una cancelación toma de su franja la categoría, las horas, la sede, el entrenador y el grupo (en un extra, la
     * franja no existe y valen los propios). {@code dia_franja} es NULL en un extra.
     */
    private static final String CONSULTA = """
            SELECT x.id_excepcion, x.tipo, x.fecha, x.id_franja,
                   coalesce(x.id_categoria, f.id_categoria) AS id_categoria, c.nombre AS categoria,
                   f.dia AS dia_franja,
                   coalesce(x.hora_inicio, f.hora_inicio) AS hora_inicio, coalesce(x.hora_fin, f.hora_fin) AS hora_fin,
                   coalesce(x.sede, f.sede) AS sede,
                   coalesce(x.id_entrenador, f.id_entrenador) AS id_entrenador,
                   e.nombre || ' ' || e.apellido AS entrenador, e.activo AS entrenador_activo,
                   coalesce(x.grupo, f.grupo) AS grupo, x.motivo, x.creado_por
              FROM excepcion_horario x
              LEFT JOIN franja_horario f ON f.id_franja = x.id_franja
              JOIN categoria c ON c.id_categoria = coalesce(x.id_categoria, f.id_categoria)
              LEFT JOIN entrenador e ON e.id_entrenador = coalesce(x.id_entrenador, f.id_entrenador)""";

    private static final RowMapper<ExcepcionHorario> FILA_A_ENTIDAD = ExcepcionHorarioDAOImpl::aEntidad;

    private final NamedParameterJdbcTemplate jdbc;

    public ExcepcionHorarioDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID crearCancelacion(UUID idFranja, LocalDate fecha, String motivo, UUID creadoPor) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO excepcion_horario (tipo, fecha, id_franja, motivo, creado_por)
                    VALUES ('CANCELACION', :fecha, :idFranja, :motivo, :creadoPor)
                    RETURNING id_excepcion""",
                    new MapSqlParameterSource().addValue("fecha", fecha).addValue("idFranja", idFranja)
                            .addValue("motivo", motivo).addValue("creadoPor", creadoPor),
                    UUID.class);
        } catch (DataIntegrityViolationException ex) {
            if (nombraRestriccion(ex, CANCELACION_UNICA)) {
                throw new FranjaYaCanceladaException(ex);
            }
            throw ex;
        }
    }

    @Override
    public UUID crearExtra(ExcepcionHorario e) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO excepcion_horario (tipo, fecha, id_categoria, hora_inicio, hora_fin, sede, id_entrenador,
                                                   grupo, motivo, creado_por)
                    VALUES ('EXTRA', :fecha, :idCategoria, :horaInicio, :horaFin, :sede, :idEntrenador, :grupo, :motivo,
                            :creadoPor)
                    RETURNING id_excepcion""",
                    new MapSqlParameterSource().addValue("fecha", e.fecha()).addValue("idCategoria", e.idCategoria())
                            .addValue("horaInicio", e.horaInicio()).addValue("horaFin", e.horaFin())
                            .addValue("sede", e.sede()).addValue("idEntrenador", e.idEntrenador())
                            .addValue("grupo", e.grupo()).addValue("motivo", e.motivo())
                            .addValue("creadoPor", e.creadoPor()),
                    UUID.class);
        } catch (DataIntegrityViolationException ex) {
            if (nombraRestriccion(ex, CRUCE_ENTRENADOR)) {
                throw new CruceDeHorarioException(ex);
            }
            throw ex;
        }
    }

    @Override
    public Optional<ExcepcionHorario> buscarPorId(UUID id) {
        return jdbc.query(CONSULTA + " WHERE x.id_excepcion = :id", new MapSqlParameterSource("id", id), FILA_A_ENTIDAD)
                .stream().findFirst();
    }

    @Override
    public boolean eliminar(UUID id) {
        return jdbc.update("DELETE FROM excepcion_horario WHERE id_excepcion = :id", new MapSqlParameterSource("id", id)) > 0;
    }

    @Override
    public List<ExcepcionHorario> listar(LocalDate desde, LocalDate hasta) {
        return jdbc.query(CONSULTA + " WHERE x.fecha BETWEEN :desde AND :hasta"
                        + " ORDER BY x.fecha, coalesce(x.hora_inicio, f.hora_inicio), c.edad_minima, x.id_excepcion",
                new MapSqlParameterSource("desde", desde).addValue("hasta", hasta), FILA_A_ENTIDAD);
    }

    @Override
    public boolean esDuenoDeFranja(UUID idUsuario, UUID idFranja) {
        return existe("""
                SELECT EXISTS (
                    SELECT 1 FROM franja_horario f
                      JOIN categoria c ON c.id_categoria = f.id_categoria
                      LEFT JOIN entrenador dueno ON dueno.id_entrenador = c.id_entrenador
                      LEFT JOIN entrenador de_la_franja ON de_la_franja.id_entrenador = f.id_entrenador
                     WHERE f.id_franja = :franja
                       AND (dueno.id_usuario = :usuario OR de_la_franja.id_usuario = :usuario))""",
                new MapSqlParameterSource("franja", idFranja).addValue("usuario", idUsuario));
    }

    @Override
    public boolean esDuenoDeCategoria(UUID idUsuario, UUID idCategoria) {
        return existe("""
                SELECT EXISTS (
                    SELECT 1 FROM categoria c
                      JOIN entrenador e ON e.id_entrenador = c.id_entrenador
                     WHERE c.id_categoria = :categoria AND e.id_usuario = :usuario)""",
                new MapSqlParameterSource("categoria", idCategoria).addValue("usuario", idUsuario));
    }

    @Override
    public boolean esDuenoDeExcepcion(UUID idUsuario, UUID idExcepcion) {
        // Un extra no tiene franja (el LEFT JOIN queda en NULL y solo cuenta la categoría); una cancelación toma la
        // categoría de su franja y también cuenta el entrenador de la franja.
        return existe("""
                SELECT EXISTS (
                    SELECT 1 FROM excepcion_horario x
                      LEFT JOIN franja_horario f ON f.id_franja = x.id_franja
                      JOIN categoria c ON c.id_categoria = coalesce(x.id_categoria, f.id_categoria)
                      LEFT JOIN entrenador dueno ON dueno.id_entrenador = c.id_entrenador
                      LEFT JOIN entrenador de_la_franja ON de_la_franja.id_entrenador = f.id_entrenador
                     WHERE x.id_excepcion = :excepcion
                       AND (dueno.id_usuario = :usuario OR de_la_franja.id_usuario = :usuario))""",
                new MapSqlParameterSource("excepcion", idExcepcion).addValue("usuario", idUsuario));
    }

    @Override
    public boolean hayFranjaQueSeCruza(UUID idEntrenador, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        jdbc.queryForList("SELECT id_entrenador FROM entrenador WHERE id_entrenador = :id FOR UPDATE",
                new MapSqlParameterSource("id", idEntrenador), UUID.class);
        // [inicio, fin) contra [hora_inicio, hora_fin): tocarse no es cruzarse, igual que tsrange(...) &&
        return existe("""
                SELECT EXISTS (
                    SELECT 1 FROM franja_horario f
                     WHERE f.id_entrenador = :entrenador
                       AND f.dia = :dia
                       AND f.hora_inicio < :fin AND :inicio < f.hora_fin
                       AND NOT EXISTS (SELECT 1 FROM excepcion_horario c
                                        WHERE c.tipo = 'CANCELACION' AND c.id_franja = f.id_franja AND c.fecha = :fecha))""",
                new MapSqlParameterSource("entrenador", idEntrenador).addValue("dia", fecha.getDayOfWeek().getValue())
                        .addValue("inicio", inicio).addValue("fin", fin).addValue("fecha", fecha));
    }

    private boolean existe(String sql, MapSqlParameterSource parametros) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, parametros, Boolean.class));
    }

    /** PostgreSQL pone el nombre de la restricción o del índice entre comillas en el mensaje, en cualquier idioma. */
    private static boolean nombraRestriccion(DataIntegrityViolationException ex, String nombre) {
        return String.valueOf(ex.getMostSpecificCause().getMessage()).contains("\"" + nombre + "\"");
    }

    private static ExcepcionHorario aEntidad(ResultSet fila, int numero) throws SQLException {
        return new ExcepcionHorario(
                fila.getObject("id_excepcion", UUID.class),
                fila.getString("tipo"),
                fila.getObject("fecha", LocalDate.class),
                fila.getObject("id_franja", UUID.class),
                fila.getObject("id_categoria", UUID.class),
                fila.getString("categoria"),
                fila.getObject("dia_franja", Integer.class),
                fila.getObject("hora_inicio", LocalTime.class),
                fila.getObject("hora_fin", LocalTime.class),
                fila.getString("sede"),
                fila.getObject("id_entrenador", UUID.class),
                fila.getString("entrenador"),
                fila.getObject("entrenador_activo", Boolean.class),
                fila.getString("grupo"),
                fila.getString("motivo"),
                fila.getObject("creado_por", UUID.class));
    }
}
