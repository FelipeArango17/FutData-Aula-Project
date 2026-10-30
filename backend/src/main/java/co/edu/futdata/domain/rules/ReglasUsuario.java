package co.edu.futdata.domain.rules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ReglasUsuario {
    public static final Set<String> ROLES = Set.of("ADMINISTRADOR", "TESORERO", "ENTRENADOR", "ACUDIENTE");
    private static final Pattern NOMBRE = Pattern.compile("\\p{L}[\\p{L} '’-]*");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private ReglasUsuario() { }

    public static Map<String, String> validar(String nombre, String apellido, String correo, String rol) {
        Map<String, String> errores = new LinkedHashMap<>();
        obligatorio(errores, "nombre", nombre);
        obligatorio(errores, "apellido", apellido);
        if (nombre != null && !nombre.isBlank() && (nombre.length() > 100 || !NOMBRE.matcher(nombre).matches())) {
            errores.put("nombre", "solo puede contener letras, espacios, apóstrofes o guiones");
        }
        if (apellido != null && !apellido.isBlank() && (apellido.length() > 100 || !NOMBRE.matcher(apellido).matches())) {
            errores.put("apellido", "solo puede contener letras, espacios, apóstrofes o guiones");
        }
        if (correo == null || correo.isBlank()) errores.put("correo", "es obligatorio");
        else if (correo.length() > 320 || !CORREO.matcher(correo).matches()) errores.put("correo", "tiene un formato inválido");
        if (rol == null || !ROLES.contains(rol)) errores.put("rol", "debe ser ADMINISTRADOR, TESORERO, ENTRENADOR o ACUDIENTE");
        return errores;
    }

    private static void obligatorio(Map<String, String> errores, String campo, String valor) {
        if (valor == null || valor.isBlank()) errores.put(campo, "es obligatorio");
    }
}
