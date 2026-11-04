package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.MiJugadorFichaResponseDTO;
import co.edu.futdata.api.dto.MiJugadorResponseDTO;
import co.edu.futdata.api.dto.PaginaDTO;
import java.util.List;
import java.util.UUID;

public interface IDeportistaService {

    /**
     * Lista paginada. Los parámetros llegan tal como los mandó el cliente ({@code null} = no vino) y se validan
     * aquí: un valor inválido lanza {@link DatosInvalidosException} (400).
     *
     * @param page            desde 0 (por defecto 0)
     * @param size            de 1 a 100 (por defecto 10)
     * @param sort            {@code campo} o {@code campo,asc|desc}; campos en {@code OrdenDeportista}
     * @param activo          {@code null} = todos
     * @param categoria       nombre exacto de una de las 9 categorías; {@code null} o vacío = todas
     * @param esAdministrador {@code false} (entrenador): sin teléfono, dirección ni {@code estadoInscripcion}
     */
    PaginaDTO<DeportistaResponseDTO> listar(Integer page, Integer size, String sort, Boolean activo, String categoria,
                                            boolean esAdministrador);

    /** @throws ConflictoException el número de documento ya está registrado (409) */
    DeportistaResponseDTO crear(DeportistaRequestDTO datos);

    /**
     * Reemplaza los campos editables (nombre, apellido, tipo y número de documento, fecha de nacimiento, teléfono,
     * dirección, posición, grupo y activo). No toca la foto.
     *
     * @throws RecursoNoEncontradoException no existe (404)
     * @throws DatosInvalidosException      datos inválidos (400)
     * @throws ConflictoException           el número de documento ya es de otro deportista (409)
     */
    DeportistaResponseDTO actualizar(UUID id, DeportistaRequestDTO datos);

    /**
     * Sube o reemplaza la foto (JPG o PNG por su contenido, hasta 5 MB) en el bucket privado {@code fotos}.
     *
     * @throws RecursoNoEncontradoException no existe (404)
     * @throws DatosInvalidosException      vacía, más de 5 MB o no es JPG ni PNG (400)
     */
    DeportistaResponseDTO subirFoto(UUID id, byte[] contenido);

    /**
     * Borra la foto del bucket y deja {@code foto} en NULL.
     *
     * @throws RecursoNoEncontradoException no existe (404)
     */
    DeportistaResponseDTO eliminarFoto(UUID id);
    DeportistaResponseDTO consultar(UUID id);

    /**
     * Los jugadores vinculados al usuario acudiente (el {@code sub} del token), con foto firmada y categoría derivada.
     * Lista vacía si no tiene fila de acudiente o no tiene hijos.
     */
    List<MiJugadorResponseDTO> misJugadores(UUID idUsuario);

    /**
     * La ficha de uno de sus jugadores.
     *
     * @throws RecursoNoEncontradoException no existe o no es de este acudiente, con el mismo mensaje (404)
     */
    MiJugadorFichaResponseDTO miJugador(UUID idUsuario, UUID idDeportista);
}
