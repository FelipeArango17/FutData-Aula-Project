package co.edu.futdata.domain.rules;

import co.edu.futdata.domain.entities.Entrenador;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reglas de los datos personales de un entrenador (HU-025). Son las mismas que valida el formulario del
 * frontend ({@code FormularioEntrenador.jsx}), más las que exige la tabla {@code entrenador} de la migración
 * 001 (largos de las columnas y {@code fecha_nacimiento} obligatoria).
 *
 * <p>Primero se {@link #limpiar limpia} y después se {@link #validar valida}: los largos se cuentan sin los
 * espacios sobrantes. Los mensajes nombran la regla, nunca el valor recibido.
 */
public final class ReglasEntrenador {

    /** Nombres de las categorías del club en el orden oficial (de menor a mayor edad). */
    public static final List<String> CATEGORIAS = List.of("Sub-8", "Sub-10", "Sub-12", "Sub-14", "Sub-16");

    /** Códigos de {@code entrenador_tipo_documento_ck}. */
    public static final List<String> TIPOS_DOCUMENTO = List.of("CC", "TI", "CE", "PASAPORTE");

    public static final int EDAD_MINIMA = 18;

    // Largos de las columnas en la migración 001
    public static final int MAX_NOMBRE = 100;
    public static final int MAX_APELLIDO = 100;
    public static final int MAX_CORREO = 320;
    public static final int MAX_ESPECIALIDAD = 100;
    public static final int MAX_DIRECCION = 250;
    public static final int MAX_HORARIO_TRABAJO = 250;

    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");
    private static final Pattern TELEFONO = Pattern.compile("\\d{10}");
    // Igual que esCorreoValido del frontend: algo@dominio.ext, sin espacios
    private static final Pattern CORREO = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private ReglasEntrenador() {
    }

    /**
     * Copia del entrenador con los textos como se guardan: sin espacios al principio ni al final, sin espacios
     * repetidos en los textos de una línea, el tipo de documento en mayúsculas, el correo en minúsculas y los
     * campos opcionales vacíos como {@code null}. No toca el id ni las categorías.
     */
    public static Entrenador limpiar(Entrenador datos) {
        Entrenador limpio = new Entrenador();
        limpio.setId(datos.getId());
        limpio.setNombre(texto(datos.getNombre()));
        limpio.setApellido(texto(datos.getApellido()));
        String tipo = recortar(datos.getTipoDocumento());
        limpio.setTipoDocumento(tipo == null ? null : tipo.toUpperCase(Locale.ROOT));
        limpio.setDocumento(recortar(datos.getDocumento()));
        limpio.setFechaNacimiento(datos.getFechaNacimiento());
        limpio.setTelefono(recortar(datos.getTelefono()));
        String correo = recortar(datos.getCorreo());
        // En minúsculas para que la restricción unique (correo) de la tabla no distinga mayúsculas
        limpio.setCorreo(correo == null ? null : correo.toLowerCase(Locale.ROOT));
        limpio.setDireccion(texto(datos.getDireccion()));
        limpio.setHorarioTrabajo(texto(datos.getHorarioTrabajo()));
        limpio.setEspecialidad(texto(datos.getEspecialidad()));
        limpio.setCategorias(datos.getCategorias());
        return limpio;
    }

    /**
     * Errores de un entrenador ya {@link #limpiar limpio}, uno por campo ({@code campo -> mensaje}). Vacío si
     * todo es válido.
     *
     * @param hoy fecha de hoy en Colombia, para la fecha de nacimiento
     */
    public static Map<String, String> validar(Entrenador e, LocalDate hoy) {
        Map<String, String> errores = new LinkedHashMap<>();
        obligatorio(errores, "nombre", e.getNombre(), MAX_NOMBRE, "es obligatorio");
        obligatorio(errores, "apellido", e.getApellido(), MAX_APELLIDO, "es obligatorio");
        if (e.getTipoDocumento() == null) {
            errores.put("tipoDocumento", "es obligatorio");
        } else if (!TIPOS_DOCUMENTO.contains(e.getTipoDocumento())) {
            errores.put("tipoDocumento", "debe ser CC, TI, CE o PASAPORTE");
        }
        if (e.getDocumento() == null) {
            errores.put("documento", "es obligatorio");
        } else if (!SOLO_DIGITOS.matcher(e.getDocumento()).matches()) {
            errores.put("documento", "solo puede tener números, sin puntos ni espacios");
        } else if (e.getDocumento().length() < 6 || e.getDocumento().length() > 12) {
            errores.put("documento", "debe tener entre 6 y 12 dígitos");
        }
        if (e.getTelefono() == null) {
            errores.put("telefono", "es obligatorio");
        } else if (!TELEFONO.matcher(e.getTelefono()).matches()) {
            errores.put("telefono", "debe tener exactamente 10 dígitos, sin espacios");
        }
        if (e.getCorreo() == null) {
            errores.put("correo", "es obligatorio");
        } else if (largo(e.getCorreo()) > MAX_CORREO) {
            errores.put("correo", "debe tener como máximo " + MAX_CORREO + " caracteres");
        } else if (!CORREO.matcher(e.getCorreo()).matches()) {
            errores.put("correo", "no es válido");
        }
        obligatorio(errores, "especialidad", e.getEspecialidad(), MAX_ESPECIALIDAD, "es obligatoria");
        validarFechaNacimiento(errores, e.getFechaNacimiento(), hoy);
        maximo(errores, "direccion", e.getDireccion(), MAX_DIRECCION);
        maximo(errores, "horarioTrabajo", e.getHorarioTrabajo(), MAX_HORARIO_TRABAJO);
        return errores;
    }

    /** {@code true} si es el nombre de una de las {@link #CATEGORIAS} oficiales (distingue mayúsculas). */
    public static boolean esCategoria(String nombre) {
        return CATEGORIAS.contains(nombre);
    }

    /** Los nombres en el orden oficial; los que no son oficiales van al final, en el orden recibido. */
    public static List<String> enOrdenOficial(Collection<String> nombres) {
        return nombres.stream()
                .sorted(Comparator.comparingInt(ReglasEntrenador::posicion))
                .toList();
    }

    private static int posicion(String nombre) {
        int i = CATEGORIAS.indexOf(nombre);
        return i < 0 ? CATEGORIAS.size() : i;
    }

    private static void validarFechaNacimiento(Map<String, String> errores, LocalDate fecha, LocalDate hoy) {
        if (fecha == null) {
            errores.put("fechaNacimiento", "es obligatoria");
        } else if (fecha.isAfter(hoy)) {
            errores.put("fechaNacimiento", "no puede ser una fecha futura");
        } else if (fecha.plusYears(EDAD_MINIMA).isAfter(hoy)) {
            errores.put("fechaNacimiento", "el entrenador debe tener al menos " + EDAD_MINIMA + " años");
        }
    }

    private static void obligatorio(Map<String, String> errores, String campo, String valor, int maximo, String mensaje) {
        if (valor == null) {
            errores.put(campo, mensaje);
        } else {
            maximo(errores, campo, valor, maximo);
        }
    }

    private static void maximo(Map<String, String> errores, String campo, String valor, int maximo) {
        if (valor != null && largo(valor) > maximo) {
            errores.put(campo, "debe tener como máximo " + maximo + " caracteres");
        }
    }

    /** Caracteres como los cuenta {@code varchar(n)} de PostgreSQL (puntos de código, no unidades UTF-16). */
    private static int largo(String valor) {
        return valor.codePointCount(0, valor.length());
    }

    private static String recortar(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.strip();
        return recortado.isEmpty() ? null : recortado;
    }

    private static String texto(String valor) {
        String recortado = recortar(valor);
        return recortado == null ? null : ESPACIOS.matcher(recortado).replaceAll(" ");
    }
}
