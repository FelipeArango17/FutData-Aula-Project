package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de crear la contraseña: solo un mensaje. No trae tokens ni abre sesión: el usuario inicia sesión después. */
public record CrearContrasenaResponseDTO(
        @Schema(example = "Tu contraseña quedó creada. Ya puedes iniciar sesión.") String mensaje) {

    public static final String CREADA = "Tu contraseña quedó creada. Ya puedes iniciar sesión.";
}
