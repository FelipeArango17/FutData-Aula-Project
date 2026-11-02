package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.FranjaHorarioRequestDTO;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import java.util.List;
import java.util.UUID;

/**
 * Fachada del horario fijo del club (bloque 4A).
 *
 * <p>Errores: {@link DatosInvalidosException} ({@code 400}: reglas de {@code ReglasFranja}, filtros inválidos,
 * categoría o entrenador inexistentes, entrenador inactivo), {@link RecursoNoEncontradoException} ({@code 404}) y
 * {@link ConflictoException} ({@code 409}: el entrenador ya tiene otra franja que se cruza).
 */
public interface IFranjaHorarioService {

    /**
     * Las franjas, ordenadas por día, hora de inicio y categoría. Cada filtro es opcional; vacío = sin filtro.
     *
     * @param categoria    nombre exacto de una de las 9 categorías
     * @param idEntrenador entrenador de la franja
     * @param sede         {@code Horizontes} o {@code UVA El Paraíso}
     */
    List<FranjaHorarioResponseDTO> listar(String categoria, UUID idEntrenador, String sede);

    FranjaHorarioResponseDTO consultar(UUID id);

    FranjaHorarioResponseDTO crear(FranjaHorarioRequestDTO datos);

    /**
     * Reemplaza todos los campos. El estado del entrenador solo se revisa si {@code idEntrenador} cambia: así se
     * pueden corregir las horas de una franja cuyo entrenador se desactivó después.
     */
    FranjaHorarioResponseDTO actualizar(UUID id, FranjaHorarioRequestDTO datos);

    void eliminar(UUID id);
}
