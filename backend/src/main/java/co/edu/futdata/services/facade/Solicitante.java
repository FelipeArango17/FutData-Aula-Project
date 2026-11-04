package co.edu.futdata.services.facade;

import java.util.UUID;

/**
 * Quién hace la petición: el {@code sub} del JWT ({@code usuario.id_usuario}) y su rol, en mayúsculas
 * ({@code ADMINISTRADOR}, {@code ENTRENADOR}, {@code TESORERO} o {@code ACUDIENTE}). Lo arma el controlador desde la
 * autenticación; el servicio no depende de Spring Security.
 */
public record Solicitante(UUID idUsuario, String rol) {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String ENTRENADOR = "ENTRENADOR";
    public static final String ACUDIENTE = "ACUDIENTE";

    public boolean esAdministrador() {
        return ADMINISTRADOR.equals(rol);
    }

    public boolean esEntrenador() {
        return ENTRENADOR.equals(rol);
    }

    public boolean esAcudiente() {
        return ACUDIENTE.equals(rol);
    }
}
