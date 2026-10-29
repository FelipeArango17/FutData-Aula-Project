package co.edu.futdata.datasource.external;

/**
 * No hay un archivo en esa ruta (o el bucket no existe) al pedir la URL firmada.
 */
public class ArchivoNoEncontradoException extends AlmacenamientoException {

    public ArchivoNoEncontradoException(String mensaje, Integer codigoHttp, Throwable causa) {
        super(mensaje, codigoHttp, causa);
    }
}
