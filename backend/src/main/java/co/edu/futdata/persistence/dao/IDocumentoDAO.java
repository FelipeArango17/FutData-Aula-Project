package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Documento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la tabla {@code documento} (documentos de la inscripción). El DAO no valida lo que ya valida
 * {@code ReglasDocumento} (tipo, tamaño, firma, comentario, fecha) ni decide quién puede hacer qué: eso es del servicio.
 *
 * <p>Los {@link Documento} se arman con {@code usuario} y {@code usuario_rol}: {@code rolCargadoPor} es
 * {@code ADMINISTRADOR} o {@code ACUDIENTE} (el primero si la cuenta tiene los dos) y es {@code null}, igual que los
 * correos, si la cuenta ya no existe ({@code on delete set null}).
 *
 * <p>No lee ni escribe la columna heredada {@code deportista.estado_inscripcion}: la inscripción se calcula por los
 * documentos APROBADOS en {@link IDeportistaDAO}, así que aprobar o rechazar aquí ya la actualiza.
 */
public interface IDocumentoDAO {

    /**
     * Guarda un documento PENDIENTE ({@code revisado_en} en {@code null}) y lo devuelve completo.
     *
     * @param ruta           ruta en el bucket; es única en toda la tabla
     * @param fechaVencimiento puede ser {@code null}
     * @param idUsuarioCarga {@code usuario.id_usuario} de quien lo sube (el {@code sub} del token)
     * @throws DatoDuplicadoException la ruta ya existe (campo {@code ruta})
     */
    Documento insertar(UUID idDeportista, String tipo, String ruta, String nombreOriginal, String tipoContenido,
                       int tamanoBytes, LocalDate fechaVencimiento, UUID idUsuarioCarga);

    /** Los documentos del deportista, del más reciente al más antiguo ({@code cargado_en desc}, luego {@code id_documento}). */
    List<Documento> listarPorDeportista(UUID idDeportista);

    Optional<Documento> buscarPorId(UUID idDocumento);

    /**
     * Bloquea la fila del deportista ({@code select ... for no key update}) para serializar a quienes suben documentos
     * del mismo deportista. Es el bloqueo más débil que sigue chocando con sí mismo: no impide que otros flujos inserten
     * filas que referencian al deportista (por ejemplo, cobros). <b>Solo tiene sentido dentro de una transacción</b>: el
     * bloqueo dura hasta su {@code commit} o {@code rollback}; fuera de una, se suelta al terminar la consulta y no
     * protege nada.
     *
     * @return {@code false} si el deportista no existe
     */
    boolean bloquearDeportista(UUID idDeportista);

    /** {@code true} si el deportista ya tiene un documento PENDIENTE o APROBADO de ese tipo (uno RECHAZADO no cuenta). */
    boolean existeActivoDelTipo(UUID idDeportista, String tipo);

    /**
     * Aprueba un documento que está PENDIENTE: pone {@code estado}, {@code revisado_por} y {@code revisado_en = now()}.
     *
     * @return {@code true} si actualizó una fila; {@code false} si no existe o ya no está PENDIENTE (el servicio decide
     *         entre 404 y 409)
     */
    boolean aprobar(UUID idDocumento, UUID idUsuarioRevisor);

    /**
     * Rechaza un documento que está PENDIENTE, con su comentario (ya validado y recortado por el servicio).
     *
     * @return {@code true} si actualizó una fila; {@code false} si no existe o ya no está PENDIENTE
     */
    boolean rechazar(UUID idDocumento, UUID idUsuarioRevisor, String comentario);

    /**
     * {@code true} si el deportista es de un jugador vinculado al acudiente de esa cuenta
     * ({@code acudiente_deportista} + {@code acudiente.id_usuario}, como en el 2C). {@code false} si {@code idUsuario}
     * es {@code null}.
     */
    boolean esAcudienteDe(UUID idUsuario, UUID idDeportista);
}
