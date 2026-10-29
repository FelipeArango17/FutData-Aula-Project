package co.edu.futdata.persistence.dao;

/** Acceso a datos para revisar el estado de la base de datos. */
public interface ISaludDAO {

    /** {@code true} si la base de datos responde a una consulta simple en poco tiempo. Nunca lanza excepciones. */
    boolean baseDeDatosDisponible();
}
