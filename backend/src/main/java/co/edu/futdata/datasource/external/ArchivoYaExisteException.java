package co.edu.futdata.datasource.external;

/**
 * Ya hay un archivo en esa ruta. El cliente nunca sobrescribe: un comprobante con ese número ya se guardó.
 */
public class ArchivoYaExisteException extends AlmacenamientoException {

    public ArchivoYaExisteException(String mensaje, Integer codigoHttp, Throwable causa) {
        super(mensaje, codigoHttp, causa);
    }
}
