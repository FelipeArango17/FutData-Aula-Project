package co.edu.futdata.datasource.external;

/** Ya existe una cuenta de Supabase Auth con ese correo; no se crea otra. */
public class CorreoYaRegistradoException extends AuthException {

    public CorreoYaRegistradoException(Integer codigoHttp) {
        super("Ya existe una cuenta de Supabase Auth con ese correo", codigoHttp);
    }
}
