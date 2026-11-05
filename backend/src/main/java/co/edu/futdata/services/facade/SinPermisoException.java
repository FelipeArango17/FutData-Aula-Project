package co.edu.futdata.services.facade;

/**
 * El solicitante no tiene un rol que pueda hacer la operación (por ejemplo, un rol que no es ADMINISTRADOR ni ENTRENADOR
 * pidiendo la lista de deportistas). El servicio falla cerrado: nunca trata un rol desconocido como administrador. El
 * manejo global la responde con {@code 403}.
 */
public class SinPermisoException extends RuntimeException {

    public SinPermisoException() {
        super("No tienes permiso para esta operación.");
    }
}
