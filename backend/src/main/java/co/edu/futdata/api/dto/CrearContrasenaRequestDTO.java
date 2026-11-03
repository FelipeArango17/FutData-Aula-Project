package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code POST /api/auth/crear-contrasena}. Los dos campos son <b>secretos</b>: {@link #toString()} no los
 * muestra, por si la petición termina en un log.
 *
 * @param token      el {@code token} del enlace del correo ({@code /crear-contrasena?token=...})
 * @param contrasena la contraseña nueva; política en {@code ReglasContrasena}
 */
public record CrearContrasenaRequestDTO(
        @Schema(example = "4f1c...e9") String token,
        @Schema(example = "MiClave2026") String contrasena) {

    @Override
    public String toString() {
        return "CrearContrasenaRequestDTO[token=****, contrasena=****]";
    }
}
