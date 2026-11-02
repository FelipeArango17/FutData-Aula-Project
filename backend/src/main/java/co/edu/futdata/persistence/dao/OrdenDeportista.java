package co.edu.futdata.persistence.dao;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lista blanca de órdenes para la lista de deportistas. El texto del parámetro {@code sort} solo se usa para
 * buscar una de estas constantes: las columnas SQL salen de aquí, nunca del texto del usuario. Siempre se agrega
 * {@code id_deportista} como desempate para que la paginación sea estable.
 */
public enum OrdenDeportista {

    NOMBRE("nombre", "d.nombre"),
    APELLIDO("apellido", "d.apellido", "d.nombre"),
    DOCUMENTO("documento", "d.documento_identidad"),
    FECHA_NACIMIENTO("fechaNacimiento", "d.fecha_nacimiento"),
    FECHA_REGISTRO("fechaRegistro", "d.fecha_registro");

    /** Orden por defecto: apellido y nombre, de la A a la Z. */
    public static final OrdenDeportista POR_DEFECTO = APELLIDO;

    private final String campo;
    private final List<String> columnas;

    OrdenDeportista(String campo, String... columnas) {
        this.campo = campo;
        this.columnas = List.of(columnas);
    }

    /** Nombre del campo en la API (el que se escribe en {@code sort}). */
    public String campo() {
        return campo;
    }

    public static Optional<OrdenDeportista> porCampo(String campo) {
        return Arrays.stream(values()).filter(o -> o.campo.equals(campo)).findFirst();
    }

    /** Los campos aceptados, para los mensajes de error: {@code nombre, apellido, ...}. */
    public static String camposAceptados() {
        return Arrays.stream(values()).map(OrdenDeportista::campo).collect(Collectors.joining(", "));
    }

    /** Cláusula {@code order by} (sin la palabra), con el desempate por id. */
    public String sql(boolean descendente) {
        String direccion = descendente ? " desc" : " asc";
        return columnas.stream().map(c -> c + direccion).collect(Collectors.joining(", "))
                + ", d.id_deportista asc";
    }
}
