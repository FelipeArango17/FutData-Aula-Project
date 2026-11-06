package co.edu.futdata.api.dto;

import java.util.List;

/**
 * Los documentos de un deportista con el resumen de su inscripción.
 *
 * @param documentos          del más reciente al más antiguo, incluidos los rechazados (historial)
 * @param estadoInscripcion   {@code COMPLETA} o {@code PENDIENTE}, calculado desde los documentos APROBADOS
 * @param documentosFaltantes los tipos obligatorios que aún no tienen un documento APROBADO; vacía si y solo si
 *                            {@code estadoInscripcion} es {@code COMPLETA}
 */
public record DocumentosDeportistaResponseDTO(
        List<DocumentoResponseDTO> documentos,
        String estadoInscripcion,
        List<String> documentosFaltantes) {
}
