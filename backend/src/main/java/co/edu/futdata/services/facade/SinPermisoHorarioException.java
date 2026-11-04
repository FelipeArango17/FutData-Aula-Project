package co.edu.futdata.services.facade;

import co.edu.futdata.domain.rules.ReglasExcepcion;

/**
 * El entrenador no es dueño de la categoría (ni dirige la franja) sobre la que quiere cambiar el horario. El manejo
 * global la responde con {@code 403} y este mensaje.
 */
public class SinPermisoHorarioException extends RuntimeException {

    public SinPermisoHorarioException() {
        super(ReglasExcepcion.SIN_PERMISO);
    }
}
