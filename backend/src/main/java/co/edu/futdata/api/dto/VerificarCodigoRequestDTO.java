package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code POST /api/auth/2fa/verificar}. No lleva el correo: el usuario sale del JWT.
 *
 * @param codigo los 6 dígitos que llegaron por correo
 */
public record VerificarCodigoRequestDTO(
        @Schema(example = "012345", requiredMode = Schema.RequiredMode.REQUIRED) String codigo) {
}
