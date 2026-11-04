package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.rules.ReglasExcepcion;

/**
 * La base de datos rechazó una cancelación por el índice único {@code excepcion_horario_cancelacion_unica_idx}: esa
 * franja ya está cancelada en esa fecha. La lanza el DAO al traducir la excepción de JDBC, para que el servicio
 * responda {@code 409} sin conocer el nombre del índice. El mensaje ya es el que ve el usuario.
 */
public class FranjaYaCanceladaException extends RuntimeException {

    public FranjaYaCanceladaException(Throwable causa) {
        super(ReglasExcepcion.YA_CANCELADA, causa);
    }
}
