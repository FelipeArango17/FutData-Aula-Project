package co.edu.futdata.persistence.dao;

/**
 * La base de datos rechazó una franja por la restricción de exclusión {@code franja_cruce_entrenador_ex}: el mismo
 * entrenador ya tiene otra franja que se solapa el mismo día. La lanza el DAO al traducir la excepción de JDBC, para
 * que el servicio responda {@code 409} sin conocer el nombre de la restricción. La base decide el cruce: no hay
 * consulta previa, así que dos peticiones al mismo tiempo tampoco pueden dejar un cruce.
 */
public class CruceDeHorarioException extends RuntimeException {

    public CruceDeHorarioException(Throwable causa) {
        super("La franja se cruza con otra del mismo entrenador", causa);
    }
}
