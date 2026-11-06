package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.domain.entities.Acudiente;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** {@link AcudienteDAOImpl} contra un PostgreSQL real (con la migración 011 aplicada, como las demás). */
@ExtendWith(DockerDisponible.class)
class AcudienteDAOImplTest {

    private static final String CORREO_PG = "acu-pg-";

    private NamedParameterJdbcTemplate jdbc;
    private AcudienteDAOImpl dao;
    private int contador;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        dao = new AcudienteDAOImpl(jdbc);
    }

    @AfterEach
    void limpiarTodo() {
        // La base de pruebas se comparte con otras clases, que dejan cobros y avisos de sus deportistas
        for (String tabla : List.of("auditoria", "notificacion_pago", "pago", "documento", "acudiente_deportista", "acudiente",
                "deportista")) {
            jdbc.getJdbcOperations().update("DELETE FROM " + tabla);
        }
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE '" + CORREO_PG + "%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
    }

    // --- insertar ------------------------------------------------------------------------------------------

    @Test
    void insertaSinDocumentoNiFechaYDevuelveLoGuardado() {
        Acudiente a = dao.insertar("María", "Torres", correo("maria"), "3001234567", null);

        assertThat(a.id()).isNotNull();
        assertThat(a.nombre()).isEqualTo("María");
        assertThat(a.apellido()).isEqualTo("Torres");
        assertThat(a.correo()).isEqualTo(correo("maria"));
        assertThat(a.telefono()).isEqualTo("3001234567");
        assertThat(a.idUsuario()).isNull();
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT count(*) FROM acudiente WHERE id_acudiente = ? AND tipo_documento IS NULL
                   AND documento_identidad IS NULL AND fecha_nacimiento IS NULL""", Long.class, a.id())).isEqualTo(1);
    }

    @Test
    void guardaElCorreoEnMinusculasYSinEspacios() {
        Acudiente a = dao.insertar("Ana", "Ríos", "  " + correo("ANA").toUpperCase() + " ", null, null);

        assertThat(a.correo()).isEqualTo(correo("ana"));
        assertThat(a.telefono()).isNull();
    }

    @Test
    void insertaConUnaCuentaYaExistente() {
        UUID cuenta = cuenta("con-cuenta", "ACUDIENTE");

        Acudiente a = dao.insertar("Luz", "Mejía", correo("con-cuenta"), "3000000000", cuenta);

        assertThat(a.idUsuario()).isEqualTo(cuenta);
        assertThat(dao.buscarPorUsuario(cuenta)).contains(a);
    }

    @Test
    void unCorreoRepetidoSeTraduceAUnDatoDuplicadoDeCorreo() {
        dao.insertar("Ana", "Ríos", correo("ana"), null, null);

        assertThatThrownBy(() -> dao.insertar("Otra", "Persona", correo("ana"), null, null))
                .isInstanceOf(DatoDuplicadoException.class)
                .satisfies(e -> assertThat(((DatoDuplicadoException) e).getCampo()).isEqualTo("correo"));
    }

    @Test
    void elCorreoRepetidoConOtrasMayusculasTambienEsDuplicado() {
        dao.insertar("Ana", "Ríos", correo("ana"), null, null);

        assertThatThrownBy(() -> dao.insertar("Otra", "Persona", correo("ana").toUpperCase(), null, null))
                .isInstanceOf(DatoDuplicadoException.class);
    }

    @Test
    void unaCuentaYaEnlazadaAOtroAcudienteSeTraduceAUnDatoDuplicadoDeCuenta() {
        UUID cuenta = cuenta("una", "ACUDIENTE");
        dao.insertar("Uno", "Primero", correo("uno"), null, cuenta);

        assertThatThrownBy(() -> dao.insertar("Dos", "Segundo", correo("dos"), null, cuenta))
                .isInstanceOf(DatoDuplicadoException.class)
                .satisfies(e -> assertThat(((DatoDuplicadoException) e).getCampo()).isEqualTo("cuenta"));
        assertThat(dao.buscarPorCorreo(correo("dos"))).isEmpty();
    }

    @Test
    void unaCuentaQueNoExisteEnUsuarioViolaLaClaveForanea() {
        assertThatThrownBy(() -> dao.insertar("Ana", "Ríos", correo("huerfana"), null, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void doblesInsercionesSimultaneasDejanUnoSoloYElOtroRecibeElDuplicado() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var listos = new java.util.concurrent.CountDownLatch(2);
        try {
            List<java.util.concurrent.Future<Boolean>> resultados = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(pool.submit(() -> {
                    listos.countDown();
                    listos.await();
                    try {
                        dao.insertar("Ana", "Ríos", correo("carrera"), null, null);
                        return true;
                    } catch (DatoDuplicadoException e) {
                        return false;
                    }
                }));
            }
            long creados = 0;
            for (var r : resultados) {
                if (r.get(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    creados++;
                }
            }
            assertThat(creados).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM acudiente", Long.class)).isEqualTo(1);
    }

    // --- buscar --------------------------------------------------------------------------------------------

    @Test
    void buscaPorCorreoSinDistinguirMayusculasNiEspacios() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);

        assertThat(dao.buscarPorCorreo(correo("ana").toUpperCase())).contains(a);
        assertThat(dao.buscarPorCorreo("  " + correo("ana") + "  ")).contains(a);
    }

    @Test
    void buscaUnaFilaCreadaAntesConElCorreoEnMayusculas() {
        // Filas que no pasaron por este DAO (la migración 004 pasó a minúsculas las existentes, pero por si acaso)
        jdbc.update("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo)
                VALUES ('Vieja', 'Fila', 'CC', :doc, DATE '1980-01-01', :correo)""",
                new MapSqlParameterSource("doc", "7" + (++contador)).addValue("correo", correo("vieja").toUpperCase()));

        assertThat(dao.buscarPorCorreo(correo("vieja"))).hasValueSatisfying(a -> assertThat(a.nombre()).isEqualTo("Vieja"));
    }

    @Test
    void buscarPorCorreoQueNoExisteOnuloDevuelveVacio() {
        assertThat(dao.buscarPorCorreo(correo("nadie"))).isEmpty();
        assertThat(dao.buscarPorCorreo(null)).isEmpty();
    }

    @Test
    void buscarPorUsuarioDevuelveVacioSinFichaOSiLaCuentaEsDeOtro() {
        UUID sinFicha = cuenta("sin-ficha", "ACUDIENTE");
        UUID conFicha = cuenta("con-ficha", "ACUDIENTE");
        dao.insertar("Ana", "Ríos", correo("con-ficha"), null, conFicha);

        assertThat(dao.buscarPorUsuario(sinFicha)).isEmpty();
        assertThat(dao.buscarPorUsuario(UUID.randomUUID())).isEmpty();
        assertThat(dao.buscarPorUsuario(conFicha)).hasValueSatisfying(a -> assertThat(a.correo()).isEqualTo(correo("con-ficha")));
    }

    // --- vincular ------------------------------------------------------------------------------------------

    @Test
    void vinculaConSuParentesco() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);
        UUID hijo = deportista();

        assertThat(dao.vincular(a.id(), hijo, "Madre")).isTrue();

        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT parentesco FROM acudiente_deportista WHERE id_acudiente = ? AND id_deportista = ?",
                String.class, a.id(), hijo)).isEqualTo("Madre");
    }

    @Test
    void elParentescoEsOpcionalYLosSieteSeAceptan() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);

        assertThat(dao.vincular(a.id(), deportista(), null)).isTrue();
        for (String p : List.of("Madre", "Padre", "Abuelo", "Abuela", "Tia", "Tio", "Otro")) {
            assertThat(dao.vincular(a.id(), deportista(), p)).as(p).isTrue();
        }
    }

    @Test
    void unVinculoRepetidoNoCambiaNadaNiElParentesco() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);
        UUID hijo = deportista();
        dao.vincular(a.id(), hijo, "Madre");

        assertThat(dao.vincular(a.id(), hijo, "Padre")).isFalse();

        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT parentesco FROM acudiente_deportista WHERE id_acudiente = ? AND id_deportista = ?",
                String.class, a.id(), hijo)).isEqualTo("Madre");
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM acudiente_deportista", Long.class)).isEqualTo(1);
    }

    @Test
    void unAcudienteTieneVariosHijosYUnHijoVariosAcudientes() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);
        Acudiente b = dao.insertar("Beto", "Ríos", correo("beto"), null, null);
        UUID h1 = deportista();
        UUID h2 = deportista();

        assertThat(dao.vincular(a.id(), h1, "Madre")).isTrue();
        assertThat(dao.vincular(a.id(), h2, "Madre")).isTrue();
        assertThat(dao.vincular(b.id(), h1, "Padre")).isTrue();
    }

    @Test
    void vincularConUnAcudienteOUnDeportistaQueNoExistenViolaLaClaveForanea() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);

        assertThatThrownBy(() -> dao.vincular(UUID.randomUUID(), deportista(), "Madre"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> dao.vincular(a.id(), UUID.randomUUID(), "Madre"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unParentescoFueraDeLaListaOConTildeSeRechaza() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);
        UUID hijo = deportista();

        for (String malo : List.of("Primo", "Tía", "madre")) {
            assertThatThrownBy(() -> dao.vincular(a.id(), hijo, malo)).as(malo)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("acudiente_deportista_parentesco_ck");
        }
    }

    // --- enlazarUsuario ------------------------------------------------------------------------------------

    @Test
    void enlazaLaCuentaSiElAcudienteNoTeniaNinguna() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);
        UUID cuenta = cuenta("ana", "ACUDIENTE");

        assertThat(dao.enlazarUsuario(a.id(), cuenta)).isTrue();

        assertThat(dao.buscarPorUsuario(cuenta)).hasValueSatisfying(x -> assertThat(x.id()).isEqualTo(a.id()));
        assertThat(dao.buscarPorCorreo(correo("ana"))).hasValueSatisfying(x -> assertThat(x.idUsuario()).isEqualTo(cuenta));
    }

    @Test
    void noReemplazaUnaCuentaYaEnlazada() {
        UUID primera = cuenta("primera", "ACUDIENTE");
        UUID segunda = cuenta("segunda", "ACUDIENTE");
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, primera);

        assertThat(dao.enlazarUsuario(a.id(), segunda)).isFalse();
        assertThat(dao.enlazarUsuario(a.id(), primera)).isFalse();

        assertThat(dao.buscarPorCorreo(correo("ana"))).hasValueSatisfying(x -> assertThat(x.idUsuario()).isEqualTo(primera));
        assertThat(dao.buscarPorUsuario(segunda)).isEmpty();
    }

    @Test
    void enlazarUnAcudienteQueNoExisteDevuelveFalso() {
        assertThat(dao.enlazarUsuario(UUID.randomUUID(), cuenta("x", "ACUDIENTE"))).isFalse();
    }

    @Test
    void enlazarUnaCuentaQueYaEsDeOtroAcudienteEsUnDatoDuplicadoDeCuenta() {
        UUID cuenta = cuenta("ocupada", "ACUDIENTE");
        dao.insertar("Uno", "Primero", correo("uno"), null, cuenta);
        Acudiente sinCuenta = dao.insertar("Dos", "Segundo", correo("dos"), null, null);

        assertThatThrownBy(() -> dao.enlazarUsuario(sinCuenta.id(), cuenta))
                .isInstanceOf(DatoDuplicadoException.class)
                .satisfies(e -> assertThat(((DatoDuplicadoException) e).getCampo()).isEqualTo("cuenta"));
        assertThat(dao.buscarPorCorreo(correo("dos"))).hasValueSatisfying(x -> assertThat(x.idUsuario()).isNull());
    }

    @Test
    void enlazarUnaCuentaQueNoExisteViolaLaClaveForanea() {
        Acudiente a = dao.insertar("Ana", "Ríos", correo("ana"), null, null);

        assertThatThrownBy(() -> dao.enlazarUsuario(a.id(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Ayudas --------------------------------------------------------------------------------------------

    private static String correo(String nombre) {
        return CORREO_PG + nombre + "@prueba.co";
    }

    /** Una cuenta (auth.users + usuario + un rol), con el correo de {@link #correo}. */
    private UUID cuenta(String nombre, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", new MapSqlParameterSource("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, :n, 'Prueba', :c)",
                new MapSqlParameterSource("id", id).addValue("n", nombre).addValue("c", correo(nombre)));
        jdbc.update("INSERT INTO usuario_rol (id_usuario, id_rol) SELECT :id, id_rol FROM rol WHERE nombre = :rol",
                new MapSqlParameterSource("id", id).addValue("rol", rol));
        return id;
    }

    private UUID deportista() {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento)
                VALUES ('Hijo', 'Prueba', 'TI', :doc, '2016-03-03') RETURNING id_deportista""",
                new MapSqlParameterSource("doc", "20" + (++contador) + System.nanoTime() % 100000), UUID.class);
    }
}
