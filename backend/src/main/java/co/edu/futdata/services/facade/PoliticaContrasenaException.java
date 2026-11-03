package co.edu.futdata.services.facade;

import co.edu.futdata.domain.rules.ReglasContrasena;
import java.util.List;

/**
 * La contraseña no cumple {@link ReglasContrasena}. {@code 400} con el mensaje de la política tal cual (el mismo de
 * {@code validaciones.js}, sin el prefijo {@code campo:} de {@link DatosInvalidosException}, porque el frontend lo
 * muestra debajo del campo) y {@code campos: ["contrasena"]}. Se lanza antes de verificar el enlace: no lo consume.
 */
public class PoliticaContrasenaException extends RuntimeException {

    public PoliticaContrasenaException() {
        super(ReglasContrasena.MENSAJE);
    }

    public List<String> getCampos() {
        return List.of(ReglasContrasena.CAMPO);
    }
}
