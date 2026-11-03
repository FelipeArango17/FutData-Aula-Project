package co.edu.futdata.services.facade;

/**
 * El enlace para crear la contraseña no sirve: falta el token, no existe, venció o ya se usó. El mensaje es el mismo en
 * todos los casos (no se dice cuál) y el {@code 400} va <b>sin</b> {@code campos}, para que el frontend no lo confunda
 * con el error de política de la contraseña.
 */
public class EnlaceInvalidoException extends RuntimeException {

    public static final String MENSAJE = "El enlace no es válido o venció; pide al administrador que te lo reenvíe";

    public EnlaceInvalidoException() {
        super(MENSAJE);
    }
}
