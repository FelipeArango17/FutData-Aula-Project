package co.edu.futdata.persistence.dao;

/**
 * La base de datos rechazó una escritura por una restricción {@code unique}. La lanza el DAO al traducir la
 * excepción de JDBC, para que el servicio sepa qué campo se repite sin conocer los nombres de las
 * restricciones. Cubre la carrera de dos peticiones que pasan la revisión de duplicados al mismo tiempo.
 */
public class DatoDuplicadoException extends RuntimeException {

    private final String campo;

    public DatoDuplicadoException(String campo, Throwable causa) {
        super("Valor duplicado en " + campo, causa);
        this.campo = campo;
    }

    /** Campo de la API que se repite, por ejemplo {@code documento} o {@code correo}. */
    public String getCampo() {
        return campo;
    }
}
