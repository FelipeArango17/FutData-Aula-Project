package co.edu.futdata.datasource.external;

/**
 * Supabase Auth respondió {@code 404} ("User not found"): la cuenta de ese id no existe en Auth. En la base real
 * {@code usuario.id_usuario} apunta a {@code auth.users(id)} con {@code ON DELETE RESTRICT}, así que lo más probable
 * no es que se haya borrado, sino que {@code SUPABASE_URL} sea de un proyecto distinto al de {@code DB_URL}.
 */
public class CuentaAuthNoEncontradaException extends AuthException {

    public CuentaAuthNoEncontradaException(Integer codigoHttp) {
        super("Supabase Auth no encontró la cuenta (404)", codigoHttp);
    }
}
