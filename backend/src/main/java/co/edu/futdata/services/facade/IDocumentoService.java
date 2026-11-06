package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.DescargaDocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentosDeportistaResponseDTO;
import co.edu.futdata.api.dto.RechazoDocumentoResponseDTO;
import co.edu.futdata.api.dto.RevisionDocumentoResponseDTO;
import co.edu.futdata.domain.entities.Documento;
import java.util.UUID;

/**
 * Documentos de la inscripción: subir, listar, descargar, aprobar y rechazar. Todos reciben el {@link Solicitante} y <b>autorizan
 * primero</b>, antes de validar nada, y fallan cerrado:
 *
 * <ul>
 *   <li>ADMINISTRADOR: todo.</li>
 *   <li>ACUDIENTE: solo los deportistas vinculados a su cuenta; de otro, {@link RecursoNoEncontradoException} con el
 *       mismo mensaje que un recurso inexistente (nunca 403).</li>
 *   <li>Cualquier otro rol, rol vacío o solicitante sin identificar: {@link SinPermisoException}.</li>
 * </ul>
 */
public interface IDocumentoService {

    /**
     * Sube un documento nuevo (PENDIENTE). El tipo de contenido sale de la firma del archivo, nunca de su nombre.
     *
     * @param nombreOriginal       el nombre que mandó el cliente; solo informativo
     * @param fechaVencimientoTexto {@code AAAA-MM-DD}, opcional (nulo o en blanco = sin vencimiento)
     * @throws RecursoNoEncontradoException el deportista no existe, o no es de este acudiente
     * @throws DatosInvalidosException      tipo, archivo o fecha inválidos (todos los errores juntos)
     * @throws ConflictoException           ya hay un documento PENDIENTE o APROBADO de ese tipo
     */
    Documento subir(Solicitante solicitante, UUID idDeportista, String tipo, String nombreOriginal, byte[] contenido,
                    String fechaVencimientoTexto);

    /** Los documentos del deportista con su {@code estadoInscripcion} y los obligatorios que faltan. */
    DocumentosDeportistaResponseDTO listar(Solicitante solicitante, UUID idDeportista);

    /** Una URL firmada de vida corta para descargar el documento. */
    DescargaDocumentoResponseDTO descargar(Solicitante solicitante, UUID idDocumento);

    /**
     * Aprueba un documento PENDIENTE. <b>Solo ADMINISTRADOR</b>: cualquier otro rol (incluido ACUDIENTE), rol vacío o
     * solicitante sin identificar es {@link SinPermisoException}, sin tocar la base.
     *
     * @throws RecursoNoEncontradoException el documento no existe
     * @throws ConflictoException           ya no está PENDIENTE (lo revisó otro administrador o ya estaba revisado)
     */
    RevisionDocumentoResponseDTO aprobar(Solicitante solicitante, UUID idDocumento);

    /**
     * Rechaza un documento PENDIENTE con un comentario obligatorio (1 a 300 caracteres, recortado). <b>Solo
     * ADMINISTRADOR</b>. Rechazar uno ya APROBADO es un conflicto: reemplazar un aprobado no entra en esta versión.
     *
     * <p>Después de confirmar el rechazo se avisa por correo (evento {@code DOCUMENTO_RECHAZADO}) a cada acudiente del
     * deportista con un correo válido. El resultado va en {@code avisoEnviado}: {@code true} (n8n aceptó todo),
     * {@code false} (no se pudo avisar; el rechazo queda hecho igual) o {@code null} (nadie a quien avisar). Aprobar no
     * avisa.
     *
     * @throws RecursoNoEncontradoException el documento no existe
     * @throws DatosInvalidosException      comentario vacío o de más de 300 caracteres (campo {@code comentario})
     * @throws ConflictoException           ya no está PENDIENTE
     */
    RechazoDocumentoResponseDTO rechazar(Solicitante solicitante, UUID idDocumento, String comentario);
}
