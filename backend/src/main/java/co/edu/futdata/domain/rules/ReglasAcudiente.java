package co.edu.futdata.domain.rules;

import co.edu.futdata.domain.entities.DatosAcudiente;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Limpieza y validación de los datos del acudiente en la inscripción única. Los largos son los de las columnas de
 * {@code acudiente} (migración 001); el correo, además, no pasa de 254 caracteres ni de la forma que acepta n8n, porque
 * es el que recibirá el enlace para crear la contraseña.
 */
public final class ReglasAcudiente {

    /** Los parentescos que guarda la base (sin tilde). */
    public static final List<String> PARENTESCOS = List.of("Madre", "Padre", "Abuelo", "Abuela", "Tia", "Tio", "Otro");
    public static final int MAX_NOMBRE = 100;
    public static final int MAX_CORREO = 254;

    /** La misma forma que valida n8n; un correo que no la cumple haría fallar el envío del enlace. */
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** El mismo del teléfono del deportista. */
    private static final Pattern TELEFONO = Pattern.compile("\\d{10}");

    private ReglasAcudiente() {
    }

    /**
     * Quita espacios de los bordes, pasa el correo a minúsculas (el único de la base es sobre {@code lower(correo)}) y
     * normaliza {@code Tía} y {@code Tío} a {@code Tia} y {@code Tio}. Un campo en blanco queda {@code null}.
     */
    public static DatosAcudiente limpiar(DatosAcudiente original) {
        if (original == null) {
            return new DatosAcudiente(null, null, null, null, null);
        }
        String correo = recortar(original.correo());
        return new DatosAcudiente(recortar(original.nombre()), recortar(original.apellido()),
                correo == null ? null : correo.toLowerCase(Locale.ROOT), recortar(original.telefono()),
                normalizarParentesco(recortar(original.parentesco())));
    }

    /** Errores por campo ({@code nombre}, {@code apellido}, {@code correo}, {@code telefono}, {@code parentesco}). */
    public static Map<String, String> validar(DatosAcudiente a) {
        Map<String, String> errores = new LinkedHashMap<>();
        nombre(errores, "nombre", a.nombre());
        nombre(errores, "apellido", a.apellido());
        if (a.correo() == null) {
            errores.put("correo", "es obligatorio");
        } else if (a.correo().length() > MAX_CORREO) {
            errores.put("correo", "debe tener como máximo " + MAX_CORREO + " caracteres");
        } else if (!CORREO.matcher(a.correo()).matches()) {
            errores.put("correo", "no es válido");
        }
        if (a.telefono() == null) {
            errores.put("telefono", "es obligatorio");
        } else if (!TELEFONO.matcher(a.telefono()).matches()) {
            errores.put("telefono", "debe tener exactamente 10 dígitos");
        }
        if (a.parentesco() == null) {
            errores.put("parentesco", "es obligatorio");
        } else if (!PARENTESCOS.contains(a.parentesco())) {
            errores.put("parentesco", "debe ser Madre, Padre, Abuelo, Abuela, Tia, Tio u Otro");
        }
        return errores;
    }

    private static void nombre(Map<String, String> errores, String campo, String valor) {
        if (valor == null) {
            errores.put(campo, "es obligatorio");
        } else if (valor.length() > MAX_NOMBRE) {
            errores.put(campo, "debe tener como máximo " + MAX_NOMBRE + " caracteres");
        }
    }

    private static String normalizarParentesco(String parentesco) {
        if ("Tía".equals(parentesco)) {
            return "Tia";
        }
        return "Tío".equals(parentesco) ? "Tio" : parentesco;
    }

    private static String recortar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
