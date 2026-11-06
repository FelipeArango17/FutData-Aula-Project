package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.AsistenciaRequestDTO;
import co.edu.futdata.api.dto.SesionAsistenciaResponseDTO;

/**
 * Asistencia a una sesión del horario: una franja en una fecha o un extra (bloque 4, versión mínima). Solo el
 * ADMINISTRADOR y el ENTRENADOR dueño de la categoría (o que dirige la franja); los demás roles fallan cerrados.
 *
 * <p>Orden de las validaciones, para no revelar nada a quien no tiene permiso: rol ({@link SinPermisoException}), forma
 * de la entrada ({@code 400} con todos los campos), sesión inexistente ({@code 404}) o fecha que no es la de la sesión
 * ({@code 400} en {@code fecha}), permiso sobre la categoría ({@link SinPermisoAsistenciaException}, {@code 403}), fecha
 * futura ({@code 400} en {@code fecha}), sesión cancelada ({@link ConflictoException}, {@code 409}) y, al guardar,
 * deportistas que no están en la lista de la sesión ({@code 400} en {@code registros}).
 */
public interface IAsistenciaService {

    /**
     * La sesión y su lista de deportistas con lo ya guardado. Solo lee: no crea el entrenamiento; si aún no existe, nadie
     * trae estado.
     *
     * @param fecha       {@code AAAA-MM-DD}
     * @param idFranja    UUID de la franja, o {@code null} si es un extra (exactamente uno de los dos)
     * @param idExcepcion UUID del extra, o {@code null} si es una franja
     */
    SesionAsistenciaResponseDTO consultar(String fecha, String idFranja, String idExcepcion, Solicitante solicitante);

    /**
     * Guarda las asistencias (upsert por deportista y sesión) en una sola transacción: o queda todo o nada. Crea el
     * entrenamiento de la sesión si no existe. Devuelve lo mismo que {@link #consultar} ya guardado.
     *
     * @throws ConflictoException si la sesión está cancelada ese día, o si se cruza con otro entrenamiento ya registrado
     */
    SesionAsistenciaResponseDTO registrar(AsistenciaRequestDTO datos, Solicitante solicitante);
}
