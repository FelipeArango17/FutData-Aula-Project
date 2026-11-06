package co.edu.futdata.api.dto;

/**
 * Cómo descargar un documento: una URL firmada de vida corta. Quien tenga la URL puede descargar el archivo hasta que
 * venza, así que solo se entrega bajo demanda, no se guarda y no se registra.
 *
 * @param vigenciaSegundos cuánto dura la URL, desde que se pidió
 */
public record DescargaDocumentoResponseDTO(String url, long vigenciaSegundos) {
}
