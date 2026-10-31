package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** SesionVerificadaDAOImpl y la migración 006 contra PostgreSQL. Sin Docker ni base local, se omite. */
@ExtendWith(DockerDisponible.class)
class SesionVerificadaDAOImplTest {

    private static final Duration VIGENCIA = Duration.ofHours(12);

    private NamedParameterJdbcTemplate jdbc;
    private SesionVerificadaDAOImpl sesiones;
    private UUID ana;
    private UUID beto;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        sesiones = new SesionVerificadaDAOImpl(jdbc);
        ana = usuario();
        beto = usuario();
    }

    @AfterEach
    void limpiarBase() {
        for (UUID id : List.of(ana, beto)) {
            jdbc.update("DELETE FROM usuario WHERE id_usuario = :id", Map.of("id", id));
            jdbc.update("DELETE FROM auth.users WHERE id = :id", Map.of("id", id));
        }
    }

    private UUID usuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, 'Prueba', 'Usuario', :correo)",
                Map.of("id", id, "correo", id + "@pradofc.co"));
        return id;
    }

    private void verificar(UUID sesion, UUID usuario) {
        Instant ahora = Instant.now();
        sesiones.marcarVerificada(sesion, usuario, ahora, ahora.plus(VIGENCIA));
    }

    @Test
    void cadaSesionSeVerificaPorSeparado() {
        UUID celular = UUID.randomUUID();
        UUID computador = UUID.randomUUID();

        verificar(celular, ana);

        assertThat(sesiones.estaVerificada(celular, ana, Instant.now())).isTrue();
        assertThat(sesiones.estaVerificada(computador, ana, Instant.now())).isFalse();
    }

    @Test
    void laSesionDeUnUsuarioNoSirveConElSubDeOtro() {
        UUID sesion = UUID.randomUUID();
        verificar(sesion, ana);

        assertThat(sesiones.estaVerificada(sesion, beto, Instant.now())).isFalse();
    }

    @Test
    void otroUsuarioNoPuedeApropiarseDeUnaSesionVerificada() {
        UUID sesion = UUID.randomUUID();
        verificar(sesion, ana);

        verificar(sesion, beto);

        assertThat(sesiones.estaVerificada(sesion, ana, Instant.now())).isTrue();
        assertThat(sesiones.estaVerificada(sesion, beto, Instant.now())).isFalse();
    }

    @Test
    void unaSesionVencidaNoEstaVerificadaYSeLimpia() {
        UUID sesion = UUID.randomUUID();
        Instant hace13Horas = Instant.now().minus(Duration.ofHours(13));
        sesiones.marcarVerificada(sesion, ana, hace13Horas, hace13Horas.plus(VIGENCIA));

        assertThat(sesiones.estaVerificada(sesion, ana, Instant.now())).isFalse();
        sesiones.limpiarVencidas(ana, Instant.now());
        assertThat(filas(ana)).isZero();
    }

    @Test
    void eliminarBorraSoloEsaSesionYSoloSiEsDelUsuario() {
        UUID celular = UUID.randomUUID();
        UUID computador = UUID.randomUUID();
        verificar(celular, ana);
        verificar(computador, ana);

        assertThat(sesiones.eliminar(celular, beto)).isZero();
        assertThat(sesiones.eliminar(celular, ana)).isOne();

        assertThat(sesiones.estaVerificada(celular, ana, Instant.now())).isFalse();
        assertThat(sesiones.estaVerificada(computador, ana, Instant.now())).isTrue();
    }

    @Test
    void revocarElAccesoBorraTodasLasSesionesDelUsuarioYNingunaDeOtro() {
        verificar(UUID.randomUUID(), ana);
        verificar(UUID.randomUUID(), ana);
        UUID deBeto = UUID.randomUUID();
        verificar(deBeto, beto);

        assertThat(sesiones.eliminarDeUsuario(ana)).isEqualTo(2);

        assertThat(filas(ana)).isZero();
        assertThat(sesiones.estaVerificada(deBeto, beto, Instant.now())).isTrue();
    }

    @Test
    void laVigenciaDebeSerPosteriorALaVerificacion() {
        Instant ahora = Instant.now();
        assertThatThrownBy(() -> sesiones.marcarVerificada(UUID.randomUUID(), ana, ahora, ahora))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * La 006 sobre una base que ya tiene la 005 con filas: las descarta (solo obliga a verificar de nuevo) y deja la
     * tabla por sesión. Al final la tabla queda igual que con todas las migraciones aplicadas.
     */
    @Test
    void laMigracion006SeAplicaSobreUnaBaseConLa005YFilas() throws Exception {
        JdbcTemplate sql = new JdbcTemplate(PostgresDePrueba.dataSource());
        sql.execute(Files.readString(migracion("005_seguridad_sesion_2fa.sql")));
        jdbc.update("INSERT INTO sesion_verificada (id_usuario, verificada_hasta) VALUES (:id, now() + interval '1 hour')",
                Map.of("id", ana));

        sql.execute(Files.readString(migracion("006_sesion_verificada_por_sesion.sql")));

        assertThat(sql.queryForObject("SELECT count(*) FROM sesion_verificada", Integer.class)).isZero();
        assertThat(sql.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'sesion_verificada' ORDER BY ordinal_position""",
                String.class)).containsExactly("id_sesion", "id_usuario", "verificada_en", "expira_en");
        assertThat(sql.queryForObject("""
                SELECT a.attname FROM pg_index i
                  JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                 WHERE i.indrelid = 'public.sesion_verificada'::regclass AND i.indisprimary""", String.class))
                .isEqualTo("id_sesion");
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM pg_indexes
                 WHERE schemaname = 'public' AND tablename = 'sesion_verificada'
                   AND indexname = 'sesion_verificada_usuario_idx'""", Integer.class)).isOne();
        assertThat(sql.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE oid = 'public.sesion_verificada'::regclass", Boolean.class))
                .isTrue();
    }

    private int filas(UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM sesion_verificada WHERE id_usuario = :id",
                Map.of("id", usuario), Integer.class);
    }

    private static Path migracion(String nombre) {
        return PostgresDePrueba.MIGRACIONES.resolve(nombre);
    }
}
