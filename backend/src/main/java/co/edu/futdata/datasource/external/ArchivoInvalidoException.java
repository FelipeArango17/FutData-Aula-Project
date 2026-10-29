package co.edu.futdata.datasource.external;

/**
 * El archivo o la ruta no cumplen las reglas del cliente (tipo, tamaño, bucket o ruta). Es un error de
 * programación: no se llega a llamar a Storage.
 */
public class ArchivoInvalidoException extends AlmacenamientoException {

    public ArchivoInvalidoException(String mensaje, Integer codigoHttp, Throwable causa) {
        super(mensaje, codigoHttp, causa);
    }
}
