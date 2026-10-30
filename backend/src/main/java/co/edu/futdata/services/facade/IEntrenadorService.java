package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.api.dto.EntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import java.util.List;
import java.util.UUID;

/**
 * Fachada de entrenadores y de la asignación de sus categorías (HU-025, CU-10).
 *
 * <p>Errores: {@link DatosInvalidosException} ({@code 400}), {@link RecursoNoEncontradoException} ({@code 404}) y
 * {@link ConflictoException} ({@code 409}).
 */
public interface IEntrenadorService {

    /** Todos los entrenadores, ordenados por apellido y nombre, con sus categorías. */
    List<EntrenadorResponseDTO> listar();

    EntrenadorResponseDTO consultar(UUID id);

    /** Valida, revisa duplicados de documento y correo, y registra los datos personales. Sin categorías. */
    EntrenadorResponseDTO crear(EntrenadorRequestDTO datos);

    /** Igual que {@link #crear}, sobre un entrenador existente. No cambia sus categorías. */
    EntrenadorResponseDTO editar(UUID id, EntrenadorRequestDTO datos);

    /**
     * Deja al entrenador con exactamente las categorías pedidas, en una sola transacción. Según las decisiones D1
     * y D7, cada categoría tiene como máximo un entrenador, así que:
     * <ul>
     *   <li>una categoría sin entrenador pasa a este directamente;</li>
     *   <li>una categoría de otro entrenador pasa a este solo si {@code reasignar} es {@code true};</li>
     *   <li>una categoría que tenía y no está en la lista queda sin entrenador.</li>
     * </ul>
     * Un entrenador inactivo no puede recibir categorías ({@code 409}).
     */
    EntrenadorResponseDTO asignarCategorias(UUID id, AsignarCategoriasRequestDTO peticion);

    /**
     * Activa o desactiva al entrenador (CU-10, camino alternativo 2; decisión D8). Desactivarlo deja sin
     * entrenador las categorías que dirigía, en la misma transacción; su historial se conserva. Pedir el estado
     * que ya tiene no cambia nada.
     */
    EntrenadorResponseDTO cambiarEstado(UUID id, CambiarEstadoEntrenadorRequestDTO peticion);

    /** Las categorías en el orden oficial, cada una con el entrenador que la dirige. */
    List<CategoriaResponseDTO> listarCategorias();
}
