package co.edu.futdata.domain.rules;

import co.edu.futdata.domain.entities.Deportista;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Validaciones de los datos personales de un deportista. */
public final class ReglasDeportista {

    public static final List<String> TIPOS_DOCUMENTO = List.of("CC", "TI", "CE", "PASAPORTE");
    private static final Pattern NOMBRE = Pattern.compile("\\p{L}[\\p{L} '’-]*");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");
    private static final Pattern ALFANUMERICO = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern TELEFONO = Pattern.compile("\\d{10}");

    private ReglasDeportista() {
    }

    public static Deportista limpiar(Deportista original) {
        Deportista d = new Deportista();
        d.setNombre(texto(original.getNombre()));
        d.setApellido(texto(original.getApellido()));
        String tipo = texto(original.getTipoDocumento());
        d.setTipoDocumento(tipo == null ? null : tipo.toUpperCase());
        String documento = texto(original.getDocumento());
        d.setDocumento("PASAPORTE".equals(d.getTipoDocumento()) ? documento.toUpperCase() : documento);
        d.setFechaNacimiento(original.getFechaNacimiento());
        d.setTelefono(texto(original.getTelefono()));
        d.setDireccion(texto(original.getDireccion()));
        d.setIdCategoria(original.getIdCategoria());
        return d;
    }

    public static Map<String, String> validar(Deportista d, LocalDate hoy) {
        Map<String, String> errores = new LinkedHashMap<>();
        obligatorio(errores, "nombre", d.getNombre(), "es obligatorio");
        obligatorio(errores, "apellido", d.getApellido(), "es obligatorio");
        if (!TIPOS_DOCUMENTO.contains(d.getTipoDocumento())) {
            errores.put("tipoDocumento", "debe ser CC, TI, CE o PASAPORTE");
        }
        String documento = d.getDocumento();
        if (documento == null || documento.isBlank()) {
            errores.put("documento", "es obligatorio");
        } else if (documento.length() < 6 || documento.length() > 40
                || (!"PASAPORTE".equals(d.getTipoDocumento()) && !SOLO_DIGITOS.matcher(documento).matches())
                || ("PASAPORTE".equals(d.getTipoDocumento()) && !ALFANUMERICO.matcher(documento).matches())) {
            errores.put("documento", "tiene un formato inválido");
        }
        if (d.getFechaNacimiento() == null) {
            errores.put("fechaNacimiento", "es obligatoria");
        } else if (d.getFechaNacimiento().isAfter(hoy)) {
            errores.put("fechaNacimiento", "no puede ser futura");
        }
        if (d.getTelefono() == null || !TELEFONO.matcher(d.getTelefono()).matches()) {
            errores.put("telefono", "debe tener exactamente 10 dígitos");
        }
        if (d.getDireccion() == null || d.getDireccion().length() < 5 || d.getDireccion().length() > 250) {
            errores.put("direccion", "debe tener entre 5 y 250 caracteres");
        }
        return errores;
    }

    private static void obligatorio(Map<String, String> errores, String campo, String valor, String mensaje) {
        if (valor == null || valor.isBlank()) errores.put(campo, mensaje);
    }

    private static String texto(String valor) {
        return valor == null ? null : valor.trim();
    }
}
