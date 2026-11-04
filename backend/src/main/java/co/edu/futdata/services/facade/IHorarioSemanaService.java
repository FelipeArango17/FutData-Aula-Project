package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.AccionSemanaResponseDTO;
import co.edu.futdata.api.dto.CancelacionRequestDTO;
import co.edu.futdata.api.dto.ExtraRequestDTO;
import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import java.util.UUID;

/**
 * El horario de una semana (lunes a sábado): las franjas fijas del club con sus cancelaciones por fecha (bloque 4B).
 * Las operaciones de escritura hacen su trabajo en una transacción que se confirma antes de devolver.
 */
public interface IHorarioSemanaService {

    /**
     * La semana pedida, con las franjas fijas y los extras. Una franja cancelada ese día sale con {@code estado}
     * CANCELADO, su {@code idExcepcion} y su {@code motivo}. {@code puedeGestionar} depende del rol y de la fecha: el
     * administrador, en todo; el entrenador, en las franjas de sus categorías o que él dirige y en los extras de sus
     * categorías (no por ser el entrenador asignado); el acudiente y el tesorero, nunca. En una fecha anterior a hoy es
     * siempre {@code false} (hoy no cuenta como pasada).
     *
     * @param fecha       {@code AAAA-MM-DD} de cualquier día de la semana pedida; {@code null} o vacío = hoy en Bogotá
     * @param solicitante el acudiente solo puede pedir la semana en curso
     * @throws DatosInvalidosException    si {@code fecha} no es una fecha válida
     * @throws SemanaNoPermitidaException si es un acudiente y la semana pedida no es la en curso
     */
    HorarioSemanaResponseDTO consultar(String fecha, Solicitante solicitante);

    /**
     * Cancela una franja en una fecha. Validaciones en este orden: cuerpo ({@code 400} con todos los campos), franja
     * inexistente ({@code 404}), fecha pasada y día de la semana distinto al de la franja ({@code 400}), permiso del
     * entrenador ({@link SinPermisoHorarioException}, {@code 403}) y, al insertar, franja ya cancelada ese día
     * ({@code 409}).
     */
    AccionSemanaResponseDTO cancelar(CancelacionRequestDTO datos, Solicitante solicitante);

    /**
     * Deshace una cancelación y devuelve la franja otra vez ACTIVA ese día.
     *
     * @throws RecursoNoEncontradoException si no existe o no es una cancelación
     * @throws DatosInvalidosException      si su fecha es pasada
     * @throws SinPermisoHorarioException   si el entrenador no es dueño
     */
    AccionSemanaResponseDTO deshacerCancelacion(UUID idExcepcion, Solicitante solicitante);

    /**
     * Crea un extra. Validaciones en este orden: cuerpo ({@code 400} con todos los campos), categoría inexistente
     * ({@code 404}), fecha pasada y fecha en domingo ({@code 400}), permiso del entrenador sobre la categoría
     * ({@link SinPermisoHorarioException}, {@code 403}), entrenador asignado inexistente o inactivo ({@code 400} en
     * {@code idEntrenador}) y, en una sola transacción, cruce del entrenador con una franja no cancelada ese día o con otro
     * extra ({@link ConflictoException}, {@code 409} en {@code idEntrenador}).
     */
    AccionSemanaResponseDTO crearExtra(ExtraRequestDTO datos, Solicitante solicitante);

    /**
     * Elimina un extra; responde con {@code sesion} {@code null}.
     *
     * @throws RecursoNoEncontradoException si no existe o no es un extra
     * @throws DatosInvalidosException      si su fecha es pasada
     * @throws SinPermisoHorarioException   si el entrenador no es dueño de su categoría
     */
    AccionSemanaResponseDTO eliminarExtra(UUID idExcepcion, Solicitante solicitante);
}
