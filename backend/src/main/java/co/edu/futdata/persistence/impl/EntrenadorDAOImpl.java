package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code entrenador} con {@link NamedParameterJdbcTemplate}. Las categorías se leen de {@code categoria}. */
@Repository
public class EntrenadorDAOImpl implements IEntrenadorDAO {

    // Nombres de las restricciones unique: la de documento es de la migración 001; la de correo es el índice
    // único sobre lower(correo) de la migración 004, que conserva el nombre de la restricción que reemplazó
    static final String UNICO_DOCUMENTO = "entrenador_tipo_documento_documento_identidad_key";
    static final String UNICO_CORREO = "entrenador_correo_key";

    private static final String COLUMNAS = """
            id_entrenador, nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, telefono,
            correo, direccion, horario_trabajo, especialidad, activo, id_usuario""";

    private static final RowMapper<Entrenador> FILA_A_ENTIDAD = EntrenadorDAOImpl::aEntidad;

    private final NamedParameterJdbcTemplate jdbc;

    public EntrenadorDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Entrenador> listar() {
        List<Entrenador> entrenadores = jdbc.query(
                "SELECT " + COLUMNAS + " FROM entrenador ORDER BY apellido, nombre, id_entrenador", FILA_A_ENTIDAD);
        Map<UUID, List<String>> categorias = new HashMap<>();
        jdbc.query("SELECT id_entrenador, nombre FROM categoria ORDER BY edad_minima", fila -> {
            categorias.computeIfAbsent(fila.getObject("id_entrenador", UUID.class), id -> new ArrayList<>())
                    .add(fila.getString("nombre"));
        });
        entrenadores.forEach(e -> e.setCategorias(categorias.getOrDefault(e.getId(), List.of())));
        return entrenadores;
    }

    @Override
    public Optional<Entrenador> buscarPorId(UUID id) {
        return buscarUno("id_entrenador = :id", id);
    }

    @Override
    public Optional<Entrenador> buscarPorIdUsuario(UUID idUsuario) {
        return buscarUno("id_usuario = :id", idUsuario);
    }

    private Optional<Entrenador> buscarUno(String condicion, UUID valor) {
        Optional<Entrenador> encontrado = jdbc.query(
                "SELECT " + COLUMNAS + " FROM entrenador WHERE " + condicion, Map.of("id", valor), FILA_A_ENTIDAD)
                .stream().findFirst();
        encontrado.ifPresent(e -> e.setCategorias(jdbc.queryForList(
                "SELECT nombre FROM categoria WHERE id_entrenador = :id ORDER BY edad_minima",
                Map.of("id", e.getId()), String.class)));
        return encontrado;
    }

    @Override
    public boolean existeDocumento(String tipoDocumento, String documento, UUID excluirId) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("tipo", tipoDocumento)
                .addValue("documento", documento);
        return existe("tipo_documento = :tipo AND documento_identidad = :documento", parametros, excluirId);
    }

    @Override
    public boolean existeCorreo(String correo, UUID excluirId) {
        return existe("lower(correo) = lower(:correo)", new MapSqlParameterSource("correo", correo), excluirId);
    }

    @Override
    public UUID crear(Entrenador e) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO entrenador (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento,
                                            telefono, correo, direccion, horario_trabajo, especialidad)
                    VALUES (:nombre, :apellido, :tipoDocumento, :documento, :fechaNacimiento,
                            :telefono, :correo, :direccion, :horarioTrabajo, :especialidad)
                    RETURNING id_entrenador""", parametros(e), UUID.class);
        } catch (DuplicateKeyException ex) {
            throw traducir(ex);
        }
    }

    @Override
    public boolean actualizar(Entrenador e) {
        try {
            return jdbc.update("""
                    UPDATE entrenador
                       SET nombre = :nombre, apellido = :apellido, tipo_documento = :tipoDocumento,
                           documento_identidad = :documento, fecha_nacimiento = :fechaNacimiento,
                           telefono = :telefono, correo = :correo, direccion = :direccion,
                           horario_trabajo = :horarioTrabajo, especialidad = :especialidad
                     WHERE id_entrenador = :id""", parametros(e).addValue("id", e.getId())) > 0;
        } catch (DuplicateKeyException ex) {
            throw traducir(ex);
        }
    }

    @Override
    public boolean cambiarActivo(UUID id, boolean activo) {
        return jdbc.update("UPDATE entrenador SET activo = :activo WHERE id_entrenador = :id",
                new MapSqlParameterSource().addValue("activo", activo).addValue("id", id)) > 0;
    }

    @Override
    public boolean vincularUsuario(UUID id, UUID idUsuario) {
        return jdbc.update("UPDATE entrenador SET id_usuario = :idUsuario WHERE id_entrenador = :id AND id_usuario IS NULL",
                new MapSqlParameterSource().addValue("idUsuario", idUsuario).addValue("id", id)) > 0;
    }

    private boolean existe(String condicion, MapSqlParameterSource parametros, UUID excluirId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM entrenador WHERE " + condicion;
        if (excluirId != null) {
            sql += " AND id_entrenador <> :excluirId";
            parametros.addValue("excluirId", excluirId);
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(sql + ")", parametros, Boolean.class));
    }

    private static MapSqlParameterSource parametros(Entrenador e) {
        return new MapSqlParameterSource()
                .addValue("nombre", e.getNombre())
                .addValue("apellido", e.getApellido())
                .addValue("tipoDocumento", e.getTipoDocumento())
                .addValue("documento", e.getDocumento())
                .addValue("fechaNacimiento", e.getFechaNacimiento())
                .addValue("telefono", e.getTelefono())
                .addValue("correo", e.getCorreo())
                .addValue("direccion", e.getDireccion())
                .addValue("horarioTrabajo", e.getHorarioTrabajo())
                .addValue("especialidad", e.getEspecialidad());
    }

    /**
     * Elige el campo según el nombre de la restricción, que PostgreSQL pone entre comillas en el mensaje en
     * cualquier idioma. El mensaje trae también el valor repetido: no se registra ni sale por la API.
     */
    private static RuntimeException traducir(DuplicateKeyException ex) {
        String mensaje = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (mensaje.contains("\"" + UNICO_DOCUMENTO + "\"")) {
            return new DatoDuplicadoException("documento", ex);
        }
        if (mensaje.contains("\"" + UNICO_CORREO + "\"")) {
            return new DatoDuplicadoException("correo", ex);
        }
        return ex;
    }

    private static Entrenador aEntidad(ResultSet fila, int numero) throws SQLException {
        Entrenador e = new Entrenador();
        e.setId(fila.getObject("id_entrenador", UUID.class));
        e.setNombre(fila.getString("nombre"));
        e.setApellido(fila.getString("apellido"));
        e.setTipoDocumento(fila.getString("tipo_documento"));
        e.setDocumento(fila.getString("documento_identidad"));
        e.setFechaNacimiento(fila.getObject("fecha_nacimiento", LocalDate.class));
        e.setTelefono(fila.getString("telefono"));
        e.setCorreo(fila.getString("correo"));
        e.setDireccion(fila.getString("direccion"));
        e.setHorarioTrabajo(fila.getString("horario_trabajo"));
        e.setEspecialidad(fila.getString("especialidad"));
        e.setActivo(fila.getBoolean("activo"));
        e.setIdUsuario(fila.getObject("id_usuario", UUID.class));
        return e;
    }
}
