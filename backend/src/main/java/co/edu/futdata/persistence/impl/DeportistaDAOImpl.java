package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.rules.ReglasDeportista;
import co.edu.futdata.persistence.dao.ConsultaDeportistas;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * La categoría del deportista se calcula en la misma consulta con un join por rango de edad:
 * {@code :anio - año(fecha_nacimiento)} entre {@code edad_minima} y {@code edad_maxima}. El año llega como
 * parámetro (del {@code Clock} del servicio, en hora de Colombia); las consultas no usan {@code current_date}.
 *
 * <p>{@code estadoInscripcion} también se calcula en la misma consulta, con una subconsulta sobre
 * {@code documento}: COMPLETA si cada uno de los 4 tipos obligatorios tiene al menos un documento APROBADO.
 *
 * <p>La columna {@code deportista.id_categoria} es un <b>valor heredado</b> (admite NULL desde la 002): este DAO no
 * la lee ni la escribe. Tampoco usa {@code estado_inscripcion}, también heredada.
 */
@Repository
public class DeportistaDAOImpl implements IDeportistaDAO {

    private final NamedParameterJdbcTemplate jdbc;
    private static final RowMapper<Deportista> FILA = DeportistaDAOImpl::mapear;

    /** COMPLETA si los 4 tipos obligatorios tienen al menos un documento APROBADO (uno rechazado no resta). */
    private static final String ESTADO_INSCRIPCION = """
            case when (select count(distinct doc.tipo) from documento doc
                        where doc.id_deportista = d.id_deportista
                          and doc.estado = 'APROBADO'
                          and doc.tipo in (:obligatorios)) = :cantidadObligatorios
                 then 'COMPLETA' else 'PENDIENTE' end""";

    /** Para el entrenador: no se consultan los documentos. */
    private static final String SIN_ESTADO_INSCRIPCION = "cast(null as varchar)";

    /** Columnas del deportista y de su categoría calculada; {@code c.*} es NULL fuera de 4 a 16 años. */
    private static final String SELECT = """
            select d.id_deportista, d.nombre, d.apellido, d.tipo_documento, d.documento_identidad,
                   d.fecha_nacimiento, d.telefono, d.direccion, d.posicion, d.grupo, d.activo, d.foto,
                   c.id_categoria as id_categoria_calculada, c.nombre as categoria_calculada,
                   %s as estado_inscripcion_calculado
              from deportista d
              left join categoria c
                on :anio - extract(year from d.fecha_nacimiento)::int between c.edad_minima and c.edad_maxima
            """;

    public DeportistaDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Deportista> listar(ConsultaDeportistas consulta, int anioActual, boolean conEstadoInscripcion) {
        MapSqlParameterSource p = filtros(consulta)
                .addValue("anio", anioActual)
                .addValue("limite", consulta.tamano())
                .addValue("desplazamiento", (long) consulta.pagina() * consulta.tamano());
        if (conEstadoInscripcion) agregarObligatorios(p);
        String sql = SELECT.formatted(conEstadoInscripcion ? ESTADO_INSCRIPCION : SIN_ESTADO_INSCRIPCION)
                + where(consulta)
                + " order by " + consulta.orden().sql(consulta.descendente())
                + " limit :limite offset :desplazamiento";
        return jdbc.query(sql, p, FILA);
    }

    @Override
    public long contar(ConsultaDeportistas consulta) {
        Long total = jdbc.queryForObject("select count(*) from deportista d" + where(consulta), filtros(consulta), Long.class);
        return total == null ? 0 : total;
    }

    /** Solo fragmentos fijos: los valores van como parámetros. */
    private static String where(ConsultaDeportistas consulta) {
        StringBuilder sql = new StringBuilder(" where true");
        if (consulta.activo() != null) sql.append(" and d.activo = :activo");
        if (consulta.nacidoDesde() != null) sql.append(" and d.fecha_nacimiento between :nacidoDesde and :nacidoHasta");
        return sql.toString();
    }

    private static MapSqlParameterSource filtros(ConsultaDeportistas consulta) {
        return new MapSqlParameterSource()
                .addValue("activo", consulta.activo())
                .addValue("nacidoDesde", consulta.nacidoDesde())
                .addValue("nacidoHasta", consulta.nacidoHasta());
    }

    private static void agregarObligatorios(MapSqlParameterSource p) {
        p.addValue("obligatorios", ReglasDeportista.DOCUMENTOS_OBLIGATORIOS)
                .addValue("cantidadObligatorios", ReglasDeportista.DOCUMENTOS_OBLIGATORIOS.size());
    }

    @Override
    public UUID crear(Deportista d) {
        try {
            return jdbc.queryForObject("""
                    insert into deportista (nombre, apellido, tipo_documento, documento_identidad,
                        fecha_nacimiento, telefono, direccion, posicion, grupo)
                    values (:nombre, :apellido, :tipoDocumento, :documento, :fechaNacimiento,
                        :telefono, :direccion, :posicion, :grupo)
                    returning id_deportista
                    """, parametros(d), UUID.class);
        } catch (DuplicateKeyException e) {
            throw new DatoDuplicadoException("documento", e);
        }
    }

    @Override
    public boolean actualizar(UUID id, Deportista d) {
        try {
            return jdbc.update("""
                    update deportista
                       set nombre = :nombre, apellido = :apellido, tipo_documento = :tipoDocumento,
                           documento_identidad = :documento, fecha_nacimiento = :fechaNacimiento,
                           telefono = :telefono, direccion = :direccion, posicion = :posicion,
                           grupo = :grupo, activo = :activo
                     where id_deportista = :id
                    """, parametros(d).addValue("id", id).addValue("activo", d.isActivo())) == 1;
        } catch (DuplicateKeyException e) {
            throw new DatoDuplicadoException("documento", e);
        }
    }

    @Override
    public boolean actualizarFoto(UUID id, String ruta) {
        return jdbc.update("update deportista set foto = :foto where id_deportista = :id",
                new MapSqlParameterSource("foto", ruta).addValue("id", id)) == 1;
    }

    @Override
    public Optional<Deportista> buscarPorId(UUID id, int anioActual) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("anio", anioActual);
        agregarObligatorios(p);
        return jdbc.query(SELECT.formatted(ESTADO_INSCRIPCION) + " where d.id_deportista = :id", p, FILA)
                .stream().findFirst();
    }

    private static MapSqlParameterSource parametros(Deportista d) {
        return new MapSqlParameterSource()
                .addValue("nombre", d.getNombre()).addValue("apellido", d.getApellido())
                .addValue("tipoDocumento", d.getTipoDocumento()).addValue("documento", d.getDocumento())
                .addValue("fechaNacimiento", d.getFechaNacimiento()).addValue("telefono", d.getTelefono())
                .addValue("direccion", d.getDireccion())
                .addValue("posicion", d.getPosicion()).addValue("grupo", d.getGrupo());
    }

    private static Deportista mapear(ResultSet r, int n) throws SQLException {
        Deportista d = new Deportista();
        d.setId(r.getObject("id_deportista", UUID.class));
        d.setNombre(r.getString("nombre")); d.setApellido(r.getString("apellido"));
        d.setTipoDocumento(r.getString("tipo_documento")); d.setDocumento(r.getString("documento_identidad"));
        d.setFechaNacimiento(r.getObject("fecha_nacimiento", LocalDate.class));
        d.setTelefono(r.getString("telefono")); d.setDireccion(r.getString("direccion"));
        d.setPosicion(r.getString("posicion"));
        d.setFoto(r.getString("foto"));
        d.setGrupo(r.getString("grupo"));
        d.setActivo(r.getBoolean("activo"));
        d.setIdCategoria(r.getObject("id_categoria_calculada", UUID.class));
        d.setCategoria(r.getString("categoria_calculada"));
        d.setEstadoInscripcion(r.getString("estado_inscripcion_calculado"));
        return d;
    }
}
