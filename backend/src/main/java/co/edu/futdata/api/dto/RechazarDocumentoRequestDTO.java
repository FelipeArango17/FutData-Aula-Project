package co.edu.futdata.api.dto;

/**
 * Rechazo de un documento PENDIENTE.
 *
 * @param comentario el motivo, obligatorio: de 1 a 300 caracteres una vez recortado. Se guarda recortado y le llega al
 *                   acudiente por correo
 */
public record RechazarDocumentoRequestDTO(String comentario) {
}
