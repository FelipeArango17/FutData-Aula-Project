package co.edu.futdata.persistence.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Un PostgreSQL con <b>todas</b> las migraciones de {@code supabase/migrations/} aplicadas tal cual desde el
 * repositorio, en orden de nombre ({@code 001_...}, {@code 002_...}...). Se prepara una sola vez para todas las
 * clases de prueba que lo usan.
 *
 * <p>Por defecto es un contenedor de Docker (Testcontainers), que se detiene al terminar. Sin Docker se puede usar
 * un PostgreSQL <b>local y desechable</b> con la variable de entorno {@link #VARIABLE_URL} (y, si hace falta,
 * {@code FUTDATA_PRUEBA_DB_USER} y {@code FUTDATA_PRUEBA_DB_PASSWORD}): por ejemplo
 * {@code jdbc:postgresql://localhost:5432/futdata_pruebas}. <b>Se borran sus esquemas {@code public} y
 * {@code auth}</b>, así que solo se acepta un servidor en {@code localhost}, nunca el de Supabase.
 *
 * <p>Las migraciones referencian {@code auth.users}, que en Supabase ya existe. Aquí se crea antes un esquema
 * {@code auth} mínimo con {@code auth.users(id uuid primary key)}, solo para que se puedan aplicar.
 *
 * <p>Las migraciones 004 y 007 dejan las diez categorías del club ({@link #CATEGORIAS_DE_LA_MIGRACION} las registra antes de la
 * primera prueba). Para que cada prueba controle sus datos, {@link #limpiar()} borra categorías y entrenadores, y
 * cada prueba inserta los suyos con {@link #categoria} y {@link #entrenador}.
 */
public final class PostgresDePrueba {

    /** PostgreSQL local para las pruebas, en lugar de Docker. */
    public static final String VARIABLE_URL = "FUTDATA_PRUEBA_DB_URL";

    /** Desde {@code backend/} (Maven) o desde la raíz del repositorio (algunas configuraciones de IntelliJ). */
    static final Path MIGRACIONES = Files.exists(Path.of("..", "supabase"))
            ? Path.of("..", "supabase", "migrations")
            : Path.of("supabase", "migrations");

    /** Las migraciones del directorio, en orden de nombre: una migración nueva entra sola en las pruebas. */
    static List<Path> migraciones() {
        try (var archivos = Files.list(MIGRACIONES)) {
            List<Path> lista = archivos.filter(a -> a.getFileName().toString().endsWith(".sql")).sorted().toList();
            if (lista.isEmpty()) {
                throw new IllegalStateException("No hay migraciones en " + MIGRACIONES.toAbsolutePath());
            }
            return lista;
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró " + MIGRACIONES.toAbsolutePath()
                    + ". Corre las pruebas desde backend/ o desde la raíz del repositorio.", e);
        }
    }

    private static final Map<String, int[]> RANGOS = Map.of(
            "Párvulos", new int[] {4, 6}, "Cachorros", new int[] {7, 8}, "Sub-9", new int[] {9, 9},
            "Sub-10", new int[] {10, 10}, "Sub-11", new int[] {11, 11}, "Sub-12", new int[] {12, 12},
            "Sub-13", new int[] {13, 13}, "Sub-14", new int[] {14, 14}, "Sub-15", new int[] {15, 15},
            "Sub-16", new int[] {16, 16});

    private static PostgreSQLContainer<?> contenedor;
    private static DataSource dataSource;
    private static List<String> categoriasDeLaMigracion;

    private PostgresDePrueba() {
    }

    /** {@code true} si se configuró un PostgreSQL local con {@link #VARIABLE_URL}. */
    public static boolean usaBaseLocal() {
        String url = System.getenv(VARIABLE_URL);
        return url != null && !url.isBlank();
    }

    /** Prepara la base la primera vez (contenedor o base local) y aplica las migraciones. */
    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            DriverManagerDataSource ds = usaBaseLocal() ? baseLocal() : baseEnDocker();
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE SCHEMA auth; CREATE TABLE auth.users (id uuid PRIMARY KEY)");
            for (Path archivo : migraciones()) {
                jdbc.execute(leerMigracion(archivo));
            }
            categoriasDeLaMigracion = jdbc.queryForList(
                    "SELECT nombre || ' ' || edad_minima || '-' || edad_maxima || ' ' || coalesce(id_entrenador::text, 'sin entrenador') "
                            + "FROM categoria ORDER BY edad_minima", String.class);
            dataSource = ds;
        }
        return dataSource;
    }

    /**
     * Las categorías que dejó la migración, antes de cualquier prueba: {@code "Párvulos 4-6 sin entrenador"}, en el
     * orden oficial.
     */
    public static List<String> categoriasDeLaMigracion() {
        dataSource();
        return categoriasDeLaMigracion;
    }

    @SuppressWarnings("resource") // lo cierra Testcontainers (Ryuk) al terminar la JVM
    private static DriverManagerDataSource baseEnDocker() {
        contenedor = new PostgreSQLContainer<>("postgres:17-alpine");
        contenedor.start();
        return new DriverManagerDataSource(contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
    }

    private static DriverManagerDataSource baseLocal() {
        String url = System.getenv(VARIABLE_URL).strip();
        String servidor = url.toLowerCase(Locale.ROOT);
        if (!servidor.startsWith("jdbc:postgresql://localhost") && !servidor.startsWith("jdbc:postgresql://127.0.0.1")) {
            throw new IllegalStateException(VARIABLE_URL + " debe apuntar a un PostgreSQL en localhost: las pruebas "
                    + "borran los esquemas public y auth de esa base");
        }
        String usuario = System.getenv().getOrDefault("FUTDATA_PRUEBA_DB_USER", "postgres");
        String clave = System.getenv().getOrDefault("FUTDATA_PRUEBA_DB_PASSWORD", "");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, usuario, clave);
        new JdbcTemplate(ds).execute(
                "DROP SCHEMA IF EXISTS public CASCADE; DROP SCHEMA IF EXISTS auth CASCADE; CREATE SCHEMA public");
        return ds;
    }

    public static NamedParameterJdbcTemplate jdbc() {
        return new NamedParameterJdbcTemplate(dataSource());
    }

    /** Borra las categorías y los entrenadores de la prueba anterior. */
    public static void limpiar() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        jdbc.update("DELETE FROM categoria");
        jdbc.update("DELETE FROM entrenador");
    }

    /** Inserta un entrenador con datos mínimos válidos y devuelve su id. */
    public static UUID entrenador(String nombre, String apellido, String documento, String correo) {
        return jdbc().queryForObject("""
                INSERT INTO entrenador (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento,
                                        telefono, correo, especialidad)
                VALUES (:nombre, :apellido, 'CC', :documento, :fecha, '3001112233', :correo, 'Porteros')
                RETURNING id_entrenador""",
                Map.of("nombre", nombre, "apellido", apellido, "documento", documento, "correo", correo,
                        "fecha", LocalDate.of(1990, 5, 14)),
                UUID.class);
    }

    /** Inserta la categoría oficial {@code nombre} (con su rango de edades) dirigida por {@code idEntrenador}. */
    public static UUID categoria(String nombre, UUID idEntrenador) {
        int[] rango = RANGOS.get(nombre);
        return jdbc().queryForObject("""
                INSERT INTO categoria (nombre, edad_minima, edad_maxima, id_entrenador)
                VALUES (:nombre, :minima, :maxima, :entrenador)
                RETURNING id_categoria""",
                Map.of("nombre", nombre, "minima", rango[0], "maxima", rango[1], "entrenador", idEntrenador),
                UUID.class);
    }

    private static String leerMigracion(Path ruta) {
        try {
            return Files.readString(ruta, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró la migración en " + ruta.toAbsolutePath()
                    + ". Corre las pruebas desde backend/ o desde la raíz del repositorio.", e);
        }
    }
}
