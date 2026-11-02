package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.FranjaHorario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la tabla {@code franja_horario}. Las franjas que devuelve traen el nombre de su categoría y, si tienen
 * entrenador, su nombre completo y si está activo.
 */
public interface IFranjaHorarioDAO {

    /**
     * Las franjas que cumplen los filtros (cada uno {@code null} = sin filtro), ordenadas por día, hora de inicio y
     * categoría (orden oficial).
     *
     * @param categoria    nombre exacto de la categoría
     * @param idEntrenador entrenador de la franja
     * @param sede         nombre exacto de la sede
     */
    List<FranjaHorario> listar(String categoria, UUID idEntrenador, String sede);

    Optional<FranjaHorario> buscarPorId(UUID id);

    /** Nombre de la categoría con ese id; vacío si no existe. */
    Optional<String> nombreDeCategoria(UUID idCategoria);

    /**
     * Inserta la franja y devuelve el id que asignó la base de datos.
     *
     * @throws CruceDeHorarioException si el entrenador ya tiene otra franja que se solapa ese día
     */
    UUID crear(FranjaHorario franja);

    /**
     * Reemplaza todos los campos de la franja con {@code franja.getId()}. La franja no choca consigo misma.
     *
     * @return {@code false} si no existe
     * @throws CruceDeHorarioException si el entrenador ya tiene otra franja que se solapa ese día
     */
    boolean actualizar(FranjaHorario franja);

    /** @return {@code false} si no existe */
    boolean eliminar(UUID id);
}
