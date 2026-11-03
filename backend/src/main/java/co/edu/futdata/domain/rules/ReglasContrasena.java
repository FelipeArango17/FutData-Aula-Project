package co.edu.futdata.domain.rules;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Política única de contraseña (regla 9 de la presentación técnica): entre 8 y 72 caracteres, con al menos una
 * letra y un número. Es la misma de {@code cumplePoliticaContrasena} en {@code frontend/src/utils/validaciones.js},
 * con el mismo mensaje. Los espacios cuentan (no se recorta) y el largo se mide como en JavaScript
 * ({@code String.length}, unidades UTF-16).
 *
 * <p>Además, como mucho {@value #MAXIMO_BYTES} bytes en UTF-8, con el mismo mensaje: Supabase Auth (bcrypt) rechaza
 * las de más de 72 bytes, y eso pasaría después de que la verificación consumió el enlace. Una ñ o una letra con
 * tilde ocupa 2 bytes, así que con ellas caben menos de 72 caracteres. El frontend todavía no revisa los bytes:
 * en ese caso raro el formulario la acepta y el backend responde el 400 de política.
 *
 * <p>Se valida en el backend <b>antes</b> de verificar el enlace con Supabase, porque la verificación consume el
 * enlace: una contraseña inválida no debe gastarlo. El mensaje nunca repite la contraseña.
 */
public final class ReglasContrasena {

    public static final int MINIMO = 8;
    public static final int MAXIMO = 72;
    /** Límite de bcrypt en Supabase Auth. */
    public static final int MAXIMO_BYTES = 72;
    /** Nombre del campo en el cuerpo de {@code POST /api/auth/crear-contrasena} y en {@code campos} del 400. */
    public static final String CAMPO = "contrasena";
    public static final String MENSAJE =
            "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número.";

    // Igual que /\p{L}/u y /\d/ del frontend: cualquier letra Unicode y un dígito ASCII (0-9)
    private static final Pattern LETRA = Pattern.compile("\\p{L}");
    private static final Pattern DIGITO = Pattern.compile("[0-9]");

    private ReglasContrasena() {
    }

    /** {@code true} si la contraseña cumple la política. {@code null} no la cumple. */
    public static boolean cumple(String contrasena) {
        return contrasena != null
                && contrasena.length() >= MINIMO
                && contrasena.length() <= MAXIMO
                && cabeEnBytes(contrasena)
                && LETRA.matcher(contrasena).find()
                && DIGITO.matcher(contrasena).find();
    }

    /** {@code true} si ocupa como mucho {@value #MAXIMO_BYTES} bytes en UTF-8. */
    static boolean cabeEnBytes(String contrasena) {
        return contrasena.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES;
    }

    /** {@code {"contrasena": MENSAJE}} si no cumple la política; vacío si la cumple. */
    public static Map<String, String> validar(String contrasena) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (!cumple(contrasena)) {
            errores.put(CAMPO, MENSAJE);
        }
        return errores;
    }
}
