package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code categoria} con {@link NamedParameterJdbcTemplate}. */
@Repository
public class CategoriaDAOImpl implements ICategoriaDAO {

    private static final String SELECT = """
            SELECT id_categoria, nombre, edad_minima, edad_maxima, id_entrenador
              FROM categoria
             ORDER BY edad_minima""";

    private static final RowMapper<Categoria> FILA_A_ENTIDAD = (fila, numero) -> new Categoria(
            fila.getObject("id_categoria", UUID.class),
            fila.getString("nombre"),
            fila.getInt("edad_minima"),
            fila.getInt("edad_maxima"),
            fila.getObject("id_entrenador", UUID.class));

    private final NamedParameterJdbcTemplate jdbc;

    public CategoriaDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Categoria> listar() {
        return jdbc.query(SELECT, FILA_A_ENTIDAD);
    }

    @Override
    public List<Categoria> listarParaActualizar() {
        return jdbc.query(SELECT + " FOR UPDATE", FILA_A_ENTIDAD);
    }

    @Override
    public void asignarEntrenador(Collection<UUID> idsCategorias, UUID idEntrenador) {
        if (idsCategorias.isEmpty()) {
            return;
        }
        // MapSqlParameterSource y no Map.of: idEntrenador puede ser null (dejar la categoría sin entrenador)
        jdbc.update("UPDATE categoria SET id_entrenador = :idEntrenador WHERE id_categoria IN (:ids)",
                new MapSqlParameterSource().addValue("idEntrenador", idEntrenador).addValue("ids", idsCategorias));
    }
}
