package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de rechazar un documento.
 *
 * @param documento         el documento ya actualizado (estado RECHAZADO, comentario recortado, quién lo revisó y cuándo)
 * @param estadoInscripcion {@code COMPLETA} o {@code PENDIENTE} del deportista <b>después</b> de este cambio
 * @param avisoEnviado      si se avisó por correo a los acudientes del deportista (evento {@code DOCUMENTO_RECHAZADO}, un
 *                          correo por acudiente, <b>después</b> de confirmar el rechazo). {@code true}: n8n aceptó todas las
 *                          llamadas (no garantiza que el correo llegue). {@code false}: no se pudo avisar (n8n falló o no
 *                          respondió, no se pudieron leer los destinatarios o falta {@code FRONTEND_URL}), pero el rechazo
 *                          <b>sí</b> quedó hecho y la respuesta sigue siendo {@code 200}. {@code null}: el deportista no
 *                          tiene acudientes con un correo válido y no se llamó a n8n. La clave va siempre en el JSON
 *                          (con {@code null}), igual que en las respuestas del horario
 */
public record RechazoDocumentoResponseDTO(
        DocumentoResponseDTO documento,
        String estadoInscripcion,
        @Schema(nullable = true, description = "true = n8n aceptó el aviso a todos los acudientes; false = no se pudo avisar "
                + "(el rechazo sí quedó hecho); null = sin acudientes con correo, no se llamó a n8n")
        Boolean avisoEnviado) {
}
