package co.edu.futdata.datasource.external;

/**
 * No se puede usar Storage por configuración: falta {@code SUPABASE_URL} o {@code SUPABASE_SERVICE_ROLE_KEY},
 * la llave no sirve ({@code 401} o {@code 403}) o el bucket no existe al subir. Reintentar no sirve.
 */
public class AlmacenamientoNoConfiguradoException extends AlmacenamientoException {

    public AlmacenamientoNoConfiguradoException(String mensaje, Integer codigoHttp, Throwable causa) {
        super(mensaje, codigoHttp, causa);
    }
}
