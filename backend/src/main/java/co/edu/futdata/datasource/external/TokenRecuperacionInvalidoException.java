package co.edu.futdata.datasource.external;

/**
 * Supabase Auth no aceptó el token del enlace de crear contraseña: no existe, venció, ya se usó o lo reemplazó un
 * enlace más nuevo. Supabase no distingue bien los casos y al usuario se le responde con un mensaje genérico único.
 */
public class TokenRecuperacionInvalidoException extends AuthException {

    public TokenRecuperacionInvalidoException(Integer codigoHttp) {
        super("Supabase Auth rechazó el token del enlace de crear contraseña", codigoHttp);
    }
}
