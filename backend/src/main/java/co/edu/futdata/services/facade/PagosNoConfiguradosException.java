package co.edu.futdata.services.facade;

/**
 * Faltan (o no son del ambiente de la URL) las llaves de Wompi. El manejo global de errores responde
 * {@code 503} con el código {@value #CODIGO}. El mensaje no dice cuál variable falta: eso va al log.
 */
public class PagosNoConfiguradosException extends RuntimeException {

    public static final String CODIGO = "PAGOS_NO_CONFIGURADOS";

    public PagosNoConfiguradosException() {
        super("Los pagos en línea no están configurados. Contacta al administrador.");
    }
}
