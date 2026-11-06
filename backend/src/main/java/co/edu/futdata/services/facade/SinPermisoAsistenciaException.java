package co.edu.futdata.services.facade;

import co.edu.futdata.domain.rules.ReglasAsistencia;

/** El entrenador no es dueño de la categoría (ni dirige la franja) de la que quiere pasar lista: {@code 403} con este mensaje. */
public class SinPermisoAsistenciaException extends RuntimeException {

    public SinPermisoAsistenciaException() {
        super(ReglasAsistencia.SIN_PERMISO);
    }
}
