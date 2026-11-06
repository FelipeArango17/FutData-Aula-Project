package co.edu.futdata.api.dto;

/**
 * Resultado de aprobar un documento.
 *
 * @param documento         el documento ya actualizado (estado, quién lo revisó y cuándo)
 * @param estadoInscripcion {@code COMPLETA} o {@code PENDIENTE} del deportista <b>después</b> de este cambio, leído de lo
 *                          que ya calcula {@code IDeportistaDAO}
 */
public record RevisionDocumentoResponseDTO(DocumentoResponseDTO documento, String estadoInscripcion) {
}
