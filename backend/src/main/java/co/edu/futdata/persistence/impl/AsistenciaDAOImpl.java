package co.edu.futdata.persistence.impl;

import co.edu.futdata.persistence.dao.DeportistaDeSesion;
import co.edu.futdata.persistence.dao.IAsistenciaDAO;
import co.edu.futdata.persistence.dao.RegistroAsistencia;
import co.edu.futdata.persistence.dao.SolapeDeEntrenamientoException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tablas {@code entrenamiento} y {@code asistencia} con {@link NamedParameterJdbcTemplate}. */
@Repository
public class AsistenciaDAOImpl implements IAsistenciaDAO {

    private final NamedParameterJdbcTemplate jdbc;

    public AsistenciaDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean franjaCancelada(UUID idFranja, LocalDate fecha) {
        return existe("""
                SELECT EXISTS (SELECT 1 FROM excepcion_horario c
                                WHERE c.tipo = 'CANCELACION' AND c.id_franja = :franja AND c.fecha = :fecha)""",
                new MapSqlParameterSource("franja", idFranja).addValue("fecha", fecha));
    }

    @Override
    public Optional<UUID> buscarEntrenamiento(UUID idCategoria, LocalDate fecha, LocalTime horaInicio, String sede) {
        return jdbc.queryForList("""
                SELECT id_entrenamiento FROM entrenamiento
                 WHERE id_categoria = :categoria AND fecha = :fecha AND hora_inicio = :inicio AND lugar = :sede""",
                new MapSqlParameterSource("categoria", idCategoria).addValue("fecha", fecha)
                        .addValue("inicio", horaInicio).addValue("sede", sede),
                UUID.class).stream().findFirst();
    }

    @Override
    public UUID buscarOCrearEntrenamiento(UUID idCategoria, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                          String sede, UUID idEntrenador) {
        Optional<UUID> existente = buscarEntrenamiento(idCategoria, fecha, horaInicio, sede);
        if (existente.isPresent()) {
            return existente.get();
        }
        // ON CONFLICT DO NOTHING (sin objetivo) cubre también las restricciones de EXCLUSIÓN (SQLState 23P01): el conflicto
        // no lanza error, así que la transacción del servicio no queda abortada y se puede volver a leer. Sin fila
        // devuelta: si la sesión ya existe fue una carrera (otra petición la acaba de crear) y se sigue con ella; si no,
        // otro entrenamiento se cruza de verdad con este horario.
        List<UUID> creado = jdbc.queryForList("""
                INSERT INTO entrenamiento (id_categoria, id_entrenador, fecha, hora_inicio, hora_fin, lugar)
                VALUES (:categoria, :entrenador, :fecha, :inicio, :fin, :sede)
                ON CONFLICT DO NOTHING
                RETURNING id_entrenamiento""",
                new MapSqlParameterSource("categoria", idCategoria).addValue("entrenador", idEntrenador)
                        .addValue("fecha", fecha).addValue("inicio", horaInicio).addValue("fin", horaFin)
                        .addValue("sede", sede),
                UUID.class);
        if (!creado.isEmpty()) {
            return creado.get(0);
        }
        return buscarEntrenamiento(idCategoria, fecha, horaInicio, sede).orElseThrow(SolapeDeEntrenamientoException::new);
    }

    @Override
    public List<DeportistaDeSesion> listarDeportistas(UUID idCategoria, String grupo, int anio, UUID idEntrenamiento) {
        // El mismo criterio de categoría calculada que DeportistaDAOImpl (no hay un fragmento compartido: allí es privado)
        return jdbc.query("""
                SELECT d.id_deportista, d.nombre, d.apellido, a.estado, a.observaciones
                  FROM deportista d
                  JOIN categoria c ON c.id_categoria = :categoria
                   AND :anio - extract(year from d.fecha_nacimiento)::int BETWEEN c.edad_minima AND c.edad_maxima
                  LEFT JOIN asistencia a ON a.id_deportista = d.id_deportista AND a.id_entrenamiento = :entrenamiento
                 WHERE d.activo AND (CAST(:grupo AS varchar) IS NULL OR d.grupo = :grupo)
                 ORDER BY d.apellido, d.nombre, d.id_deportista""",
                new MapSqlParameterSource("categoria", idCategoria).addValue("grupo", grupo).addValue("anio", anio)
                        .addValue("entrenamiento", idEntrenamiento),
                (fila, n) -> new DeportistaDeSesion(fila.getObject("id_deportista", UUID.class), fila.getString("nombre"),
                        fila.getString("apellido"), fila.getString("estado"), fila.getString("observaciones")));
    }

    @Override
    public void guardar(UUID idEntrenamiento, UUID idEntrenador, List<RegistroAsistencia> registros) {
        if (registros.isEmpty()) {
            return;
        }
        MapSqlParameterSource p = new MapSqlParameterSource("entrenamiento", idEntrenamiento)
                .addValue("entrenador", idEntrenador);
        // Una sola sentencia con todas las filas: es atómica por sí misma. Solo se arma con fragmentos fijos y nombres de
        // parámetro numerados; los valores van como parámetros.
        StringBuilder filas = new StringBuilder();
        for (int i = 0; i < registros.size(); i++) {
            RegistroAsistencia r = registros.get(i);
            filas.append(i == 0 ? "" : ", ")
                    .append("(:d").append(i).append(", CAST(:e").append(i).append(" AS varchar), CAST(:o").append(i)
                    .append(" AS text))");
            p.addValue("d" + i, r.idDeportista()).addValue("e" + i, r.estado()).addValue("o" + i, r.observaciones());
        }
        jdbc.update("""
                INSERT INTO asistencia (id_deportista, id_entrenamiento, id_entrenador, estado, observaciones)
                SELECT v.id_deportista, CAST(:entrenamiento AS uuid), CAST(:entrenador AS uuid), v.estado, v.observaciones
                  FROM (VALUES %s) AS v (id_deportista, estado, observaciones)
                ON CONFLICT (id_deportista, id_entrenamiento) DO UPDATE
                   SET estado = excluded.estado, observaciones = excluded.observaciones,
                       id_entrenador = excluded.id_entrenador, registrado_en = now()""".formatted(filas), p);
    }

    private boolean existe(String sql, MapSqlParameterSource parametros) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, parametros, Boolean.class));
    }
}
