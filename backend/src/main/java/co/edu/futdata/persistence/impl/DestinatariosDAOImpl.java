package co.edu.futdata.persistence.impl;

import co.edu.futdata.persistence.dao.Destinatario;
import co.edu.futdata.persistence.dao.IDestinatariosDAO;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Destinatarios de los avisos de horario, leídos de {@code acudiente}, {@code acudiente_deportista} y {@code deportista}. */
@Repository
public class DestinatariosDAOImpl implements IDestinatariosDAO {

    private final NamedParameterJdbcTemplate jdbc;

    public DestinatariosDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Destinatario> acudientesDeCategoria(UUID idCategoria, int anioActual) {
        // DISTINCT ON deja un solo acudiente por correo (el primero por apellido y nombre) y obliga a ordenar primero por
        // correo; la consulta de afuera devuelve el resultado por apellido y nombre.
        return jdbc.query("""
                SELECT correo, nombre FROM (
                    SELECT DISTINCT ON (lower(btrim(a.correo)))
                           btrim(a.correo) AS correo, nullif(btrim(a.nombre || ' ' || a.apellido), '') AS nombre,
                           a.apellido, a.nombre AS nombre_pila, a.id_acudiente
                      FROM categoria c
                      JOIN deportista d
                        ON d.activo AND :anio - extract(year from d.fecha_nacimiento)::int BETWEEN c.edad_minima AND c.edad_maxima
                      JOIN acudiente_deportista ad ON ad.id_deportista = d.id_deportista
                      JOIN acudiente a ON a.id_acudiente = ad.id_acudiente
                     WHERE c.id_categoria = :categoria AND a.correo IS NOT NULL AND btrim(a.correo) <> ''
                     ORDER BY lower(btrim(a.correo)), a.apellido, a.nombre, a.id_acudiente) unicos
                 ORDER BY apellido, nombre_pila, id_acudiente""",
                new MapSqlParameterSource("categoria", idCategoria).addValue("anio", anioActual),
                (fila, n) -> new Destinatario(fila.getString("correo"), fila.getString("nombre")));
    }

    @Override
    public List<Destinatario> acudientesDeDeportista(UUID idDeportista) {
        // Mismo DISTINCT ON que arriba: un solo acudiente por correo, y la consulta de afuera ordena por apellido y nombre.
        return jdbc.query("""
                SELECT correo, nombre FROM (
                    SELECT DISTINCT ON (lower(btrim(a.correo)))
                           btrim(a.correo) AS correo, nullif(btrim(a.nombre || ' ' || a.apellido), '') AS nombre,
                           a.apellido, a.nombre AS nombre_pila, a.id_acudiente
                      FROM acudiente_deportista ad
                      JOIN acudiente a ON a.id_acudiente = ad.id_acudiente
                     WHERE ad.id_deportista = :deportista AND a.correo IS NOT NULL AND btrim(a.correo) <> ''
                     ORDER BY lower(btrim(a.correo)), a.apellido, a.nombre, a.id_acudiente) unicos
                 ORDER BY apellido, nombre_pila, id_acudiente""",
                new MapSqlParameterSource("deportista", idDeportista),
                (fila, n) -> new Destinatario(fila.getString("correo"), fila.getString("nombre")));
    }
}
