package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
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
     * Deja al entrenador con exactamente las categorías pedidas, en una sola transacción. Según la decisión D1
     * cada categoría tiene un solo entrenador, así que:
     * <ul>
     *   <li>una categoría de otro entrenador pasa a este solo si {@code reasignar} es {@code true};</li>
     *   <li>no se le puede quitar una categoría sin dársela a otro: eso se hace asignándosela al otro.</li>
     * </ul>
     */
    EntrenadorResponseDTO asignarCategorias(UUID id, AsignarCategoriasRequestDTO peticion);

    /** Las categorías en el orden oficial, cada una con el entrenador que la dirige. */
    List<CategoriaResponseDTO> listarCategorias();
}
