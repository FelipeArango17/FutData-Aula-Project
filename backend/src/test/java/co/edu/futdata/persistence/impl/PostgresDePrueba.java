package co.edu.futdata.persistence.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Un PostgreSQL en Docker (Testcontainers) con la migración {@code supabase/migrations/001_schema_inicial.sql}
 * aplicada tal cual desde el repositorio. Se arranca una sola vez para todas las clases de prueba que lo usan y
 * Testcontainers lo detiene al terminar.
 *
 * <p>La migración referencia {@code auth.users}, que en Supabase ya existe. Aquí se crea antes un esquema
 * {@code auth} mínimo con {@code auth.users(id uuid primary key)}, solo para que la migración se pueda aplicar.
 *
 * <p>La migración no crea categorías ni entrenadores: cada prueba inserta los suyos con {@link #categoria} y
 * {@link #entrenador}, después de {@link #limpiar()}.
 */
public final class PostgresDePrueba {

    /** Desde {@code backend/} (Maven) o desde la raíz del repositorio (algunas configuraciones de IntelliJ). */
    static final Path MIGRACION = Files.exists(Path.of("..", "supabase"))
            ? Path.of("..", "supabase", "migrations", "001_schema_inicial.sql")
            : Path.of("supabase", "migrations", "001_schema_inicial.sql");

    private static final Map<String, int[]> RANGOS = Map.of(
            "Sub-8", new int[] {0, 7}, "Sub-10", new int[] {8, 9}, "Sub-12", new int[] {10, 11},
            "Sub-14", new int[] {12, 13}, "Sub-16", new int[] {14, 15});

    private static PostgreSQLContainer<?> contenedor;
    private static DataSource dataSource;

    private PostgresDePrueba() {
    }

    /** Arranca el contenedor la primera vez y aplica la migración. */
    @SuppressWarnings("resource") // lo cierra Testcontainers (Ryuk) al terminar la JVM
    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            contenedor = new PostgreSQLContainer<>("postgres:17-alpine");
            contenedor.start();
            DriverManagerDataSource ds = new DriverManagerDataSource(
                    contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE SCHEMA auth; CREATE TABLE auth.users (id uuid PRIMARY KEY)");
            jdbc.execute(leerMigracion());
            dataSource = ds;
        }
        return dataSource;
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

    private static String leerMigracion() {
        try {
            return Files.readString(MIGRACION, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró la migración en " + MIGRACION.toAbsolutePath()
                    + ". Corre las pruebas desde backend/ o desde la raíz del repositorio.", e);
        }
    }
}
