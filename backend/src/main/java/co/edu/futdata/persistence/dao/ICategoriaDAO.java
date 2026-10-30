package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Categoria;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Acceso a la tabla {@code categoria}. */
public interface ICategoriaDAO {

    /** Todas las categorías, en el orden oficial (por edad mínima). */
    List<Categoria> listar();

    /**
     * Igual que {@link #listar()}, pero bloquea las filas ({@code FOR UPDATE}) hasta que termine la transacción,
     * para que dos asignaciones al mismo tiempo no se pisen. Solo tiene sentido dentro de una transacción.
     */
    List<Categoria> listarParaActualizar();

    /** Pasa las categorías indicadas al entrenador {@code idEntrenador}. No hace nada si la lista está vacía. */
    void asignarEntrenador(Collection<UUID> idsCategorias, UUID idEntrenador);
}
