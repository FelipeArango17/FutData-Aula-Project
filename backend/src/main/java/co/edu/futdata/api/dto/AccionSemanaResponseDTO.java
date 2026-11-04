package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de las operaciones que cambian el horario de la semana: la sesión resultante y si se avisó a los acudientes.
 *
 * @param sesion        la sesión después del cambio; {@code null} si se borró (por ejemplo, un extra eliminado)
 * @param avisoEnviado  {@code true}: n8n recibió el aviso (no garantiza que el correo llegue). {@code false}: no se pudo
 *                      avisar, pero la operación <b>sí</b> quedó hecha. {@code null}: no había a quién avisar
 */
public record AccionSemanaResponseDTO(
        @Schema(nullable = true) SesionSemanaResponseDTO sesion,
        @Schema(nullable = true, description = "true = aviso recibido por n8n; false = no se pudo avisar; null = sin destinatarios")
        Boolean avisoEnviado) {
}
