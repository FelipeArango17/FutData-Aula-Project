package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.EstadoDocumento;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link DocumentoDAOImpl} contra un PostgreSQL real. No usa el esquema {@code storage} (no existe en las pruebas): la
 * {@code ruta} es solo un texto único.
 */
@ExtendWith(DockerDisponible.class)
class DocumentoDAOImplTest {

    private static final String CORREO_PG = "doc-pg-";

    private NamedParameterJdbcTemplate jdbc;
    private DocumentoDAOImpl dao;
    private DataSourceTransactionManager administrador;
    private UUID deportista;
    private UUID admin;
    private UUID acudiente;
    private int contador;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        administrador = new DataSourceTransactionManager(PostgresDePrueba.dataSource());
        dao = new DocumentoDAOImpl(jdbc);
        deportista = deportista();
        admin = cuenta("admin", "ADMINISTRADOR");
        acudiente = cuenta("acudiente", "ACUDIENTE");
    }

    @AfterEach
    void limpiarTodo() {
        // La base de pruebas se comparte con otras clases, que dejan cobros y avisos de sus deportistas
        jdbc.getJdbcOperations().update("DELETE FROM auditoria");
        PostgresDePrueba.limpiarDatosDeDeportistas();
        jdbc.getJdbcOperations().update("DELETE FROM acudiente");
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE '" + CORREO_PG + "%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
    }

    // --- Insertar y leer ------------------------------------------------------------------------------------

    @Test
    void insertaPendienteYLoDevuelveCompletoConRolYCorreoDeQuienLoSubio() {
        Documento d = dao.insertar(deportista, "PAZ_Y_SALVO", ruta(), "paz.pdf", "application/pdf", 1234,
                LocalDate.of(2027, 1, 31), acudiente);

        assertThat(d.id()).isNotNull();
        assertThat(d.idDeportista()).isEqualTo(deportista);
        assertThat(d.tipo()).isEqualTo("PAZ_Y_SALVO");
        assertThat(d.ruta()).startsWith("ruta-");
        assertThat(d.nombreOriginal()).isEqualTo("paz.pdf");
        assertThat(d.tipoContenido()).isEqualTo("application/pdf");
        assertThat(d.tamanoBytes()).isEqualTo(1234);
        assertThat(d.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(d.comentario()).isNull();
        assertThat(d.fechaVencimiento()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(d.rolCargadoPor()).isEqualTo("ACUDIENTE");
        assertThat(d.cargadoPorCorreo()).isEqualTo(correo("acudiente"));
        assertThat(d.cargadoEn()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        assertThat(d.revisadoPor()).isNull();
        assertThat(d.revisadoEn()).isNull();
    }

    @Test
    void laFechaDeVencimientoEsOpcional() {
        Documento d = dao.insertar(deportista, "DOCUMENTO_IDENTIDAD", ruta(), "ti.png", "image/png", 10, null, admin);

        assertThat(d.fechaVencimiento()).isNull();
        assertThat(d.rolCargadoPor()).isEqualTo("ADMINISTRADOR");
    }

    @Test
    void siLaCuentaTieneAdministradorYAcudienteGanaAdministrador() {
        UUID ambos = cuenta("ambos", "ACUDIENTE");
        jdbc.update("INSERT INTO usuario_rol (id_usuario, id_rol) SELECT :u, id_rol FROM rol WHERE nombre = 'ADMINISTRADOR'",
                new MapSqlParameterSource("u", ambos));

        assertThat(subir("PAZ_Y_SALVO", ambos).rolCargadoPor()).isEqualTo("ADMINISTRADOR");
    }

    @Test
    void conUnRolQueNoEsAdministradorNiAcudienteElRolQuedaNulo() {
        UUID tesorero = cuenta("tesorero", "TESORERO");

        Documento d = subir("PAZ_Y_SALVO", tesorero);

        assertThat(d.rolCargadoPor()).isNull();
        assertThat(d.cargadoPorCorreo()).isEqualTo(correo("tesorero"));
    }

    @Test
    void siLaCuentaYaNoExisteQuedanNulosElRolYElCorreo() {
        Documento d = subir("PAZ_Y_SALVO", acudiente);
        jdbc.update("DELETE FROM usuario WHERE id_usuario = :u", new MapSqlParameterSource("u", acudiente));

        Documento leido = dao.buscarPorId(d.id()).orElseThrow();

        assertThat(leido.rolCargadoPor()).isNull();
        assertThat(leido.cargadoPorCorreo()).isNull();
        assertThat(leido.tipo()).isEqualTo("PAZ_Y_SALVO");
    }

    @Test
    void sinUsuarioDeCargaTambienSePuedeInsertar() {
        Documento d = dao.insertar(deportista, "PAZ_Y_SALVO", ruta(), "x.pdf", "application/pdf", 1, null, null);

        assertThat(d.rolCargadoPor()).isNull();
        assertThat(d.cargadoPorCorreo()).isNull();
    }

    @Test
    void buscarPorIdDevuelveElDocumentoOVacio() {
        Documento d = subir("PAZ_Y_SALVO", admin);

        assertThat(dao.buscarPorId(d.id())).contains(d);
        assertThat(dao.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void laRutaDuplicadaLaRechazaLaBase() {
        String ruta = ruta();
        dao.insertar(deportista, "PAZ_Y_SALVO", ruta, "a.pdf", "application/pdf", 1, null, admin);

        assertThatThrownBy(() -> dao.insertar(deportista, "CARTA_RENUNCIA", ruta, "b.pdf", "application/pdf", 1, null, admin))
                .isInstanceOf(DatoDuplicadoException.class)
                .extracting(e -> ((DatoDuplicadoException) e).getCampo()).isEqualTo("ruta");
        assertThat(dao.listarPorDeportista(deportista)).hasSize(1);
    }

    // --- Lista ----------------------------------------------------------------------------------------------

    @Test
    void listaDelMasRecienteAlMasAntiguo() {
        Documento primero = subir("DOCUMENTO_IDENTIDAD", admin);
        Documento segundo = subir("CERTIFICADO_ESTUDIO", admin);
        Documento tercero = subir("PAZ_Y_SALVO", admin);
        fecharCarga(primero, "2026-11-01T10:00:00Z");
        fecharCarga(segundo, "2026-11-03T10:00:00Z");
        fecharCarga(tercero, "2026-11-02T10:00:00Z");

        assertThat(dao.listarPorDeportista(deportista)).extracting(Documento::id)
                .containsExactly(segundo.id(), tercero.id(), primero.id());
    }

    @Test
    void conLaMismaFechaDeCargaDesempataPorId() {
        List<Documento> tres = new ArrayList<>(List.of(subir("DOCUMENTO_IDENTIDAD", admin), subir("CERTIFICADO_ESTUDIO", admin),
                subir("PAZ_Y_SALVO", admin)));
        tres.forEach(d -> fecharCarga(d, "2026-11-01T10:00:00Z"));

        assertThat(dao.listarPorDeportista(deportista)).extracting(Documento::id)
                .containsExactlyElementsOf(tres.stream().map(Documento::id)
                        // Postgres ordena los uuid por bytes sin signo; UUID.compareTo de Java los compara con signo.
                        // El texto hexadecimal en minúsculas ordena igual que la base.
                        .sorted(java.util.Comparator.comparing(UUID::toString)).toList());
    }

    @Test
    void listaSoloLosDelDeportistaYTraeElCorreoDeQuienLosRevisoYDeQuienLosSubio() {
        UUID otro = deportista();
        Documento mio = subir("PAZ_Y_SALVO", acudiente);
        dao.insertar(otro, "PAZ_Y_SALVO", ruta(), "otro.pdf", "application/pdf", 1, null, admin);
        dao.aprobar(mio.id(), admin);

        List<Documento> lista = dao.listarPorDeportista(deportista);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).cargadoPorCorreo()).isEqualTo(correo("acudiente"));
        assertThat(lista.get(0).revisadoPor()).isEqualTo(correo("admin"));
        assertThat(dao.listarPorDeportista(UUID.randomUUID())).isEmpty();
    }

    // --- Un activo por tipo ---------------------------------------------------------------------------------

    @Test
    void existeActivoDelTipoParaPendienteYAprobadoPeroNoParaRechazado() {
        assertThat(dao.existeActivoDelTipo(deportista, "PAZ_Y_SALVO")).isFalse();

        Documento d = subir("PAZ_Y_SALVO", admin);
        assertThat(dao.existeActivoDelTipo(deportista, "PAZ_Y_SALVO")).as("PENDIENTE").isTrue();

        dao.aprobar(d.id(), admin);
        assertThat(dao.existeActivoDelTipo(deportista, "PAZ_Y_SALVO")).as("APROBADO").isTrue();

        Documento rechazado = subir("CARTA_RENUNCIA", admin);
        dao.rechazar(rechazado.id(), admin, "Borroso");
        assertThat(dao.existeActivoDelTipo(deportista, "CARTA_RENUNCIA")).as("RECHAZADO").isFalse();
    }

    @Test
    void unRechazadoYUnoNuevoDelMismoTipoConviven() {
        Documento viejo = subir("DOCUMENTO_IDENTIDAD", admin);
        dao.rechazar(viejo.id(), admin, "Ilegible");

        Documento nuevo = subir("DOCUMENTO_IDENTIDAD", acudiente);

        assertThat(dao.existeActivoDelTipo(deportista, "DOCUMENTO_IDENTIDAD")).isTrue();
        assertThat(dao.listarPorDeportista(deportista)).extracting(Documento::id).containsExactlyInAnyOrder(viejo.id(), nuevo.id());
    }

    @Test
    void elTipoYElDeportistaSeDistinguen() {
        subir("PAZ_Y_SALVO", admin);

        assertThat(dao.existeActivoDelTipo(deportista, "CARTA_RENUNCIA")).isFalse();
        assertThat(dao.existeActivoDelTipo(deportista(), "PAZ_Y_SALVO")).isFalse();
    }

    // --- Aprobar y rechazar ---------------------------------------------------------------------------------

    @Test
    void aprobarPoneEstadoRevisorYFecha() {
        Documento d = subir("PAZ_Y_SALVO", acudiente);

        assertThat(dao.aprobar(d.id(), admin)).isTrue();

        Documento leido = dao.buscarPorId(d.id()).orElseThrow();
        assertThat(leido.estado()).isEqualTo(EstadoDocumento.APROBADO);
        assertThat(leido.revisadoPor()).isEqualTo(correo("admin"));
        assertThat(leido.revisadoEn()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        assertThat(leido.comentario()).isNull();
        assertThat(leido.cargadoPorCorreo()).as("no toca quién lo subió").isEqualTo(correo("acudiente"));
    }

    @Test
    void rechazarPoneEstadoRevisorFechaYComentario() {
        Documento d = subir("PAZ_Y_SALVO", acudiente);

        assertThat(dao.rechazar(d.id(), admin, "La imagen está borrosa.")).isTrue();

        Documento leido = dao.buscarPorId(d.id()).orElseThrow();
        assertThat(leido.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(leido.comentario()).isEqualTo("La imagen está borrosa.");
        assertThat(leido.revisadoPor()).isEqualTo(correo("admin"));
        assertThat(leido.revisadoEn()).isNotNull();
    }

    @Test
    void laSegundaRevisionDelMismoDocumentoDevuelveFalseYNoCambiaNada() {
        Documento aprobado = subir("PAZ_Y_SALVO", acudiente);
        assertThat(dao.aprobar(aprobado.id(), admin)).isTrue();
        Documento despuesDeAprobar = dao.buscarPorId(aprobado.id()).orElseThrow();

        UUID otroAdmin = cuenta("admin2", "ADMINISTRADOR");
        assertThat(dao.aprobar(aprobado.id(), otroAdmin)).as("aprobar otra vez").isFalse();
        assertThat(dao.rechazar(aprobado.id(), otroAdmin, "Tarde")).as("rechazar uno ya aprobado").isFalse();
        assertThat(dao.buscarPorId(aprobado.id())).contains(despuesDeAprobar);

        Documento rechazado = subir("CARTA_RENUNCIA", acudiente);
        assertThat(dao.rechazar(rechazado.id(), admin, "Borroso")).isTrue();
        Documento despuesDeRechazar = dao.buscarPorId(rechazado.id()).orElseThrow();

        assertThat(dao.rechazar(rechazado.id(), otroAdmin, "Otro motivo")).as("rechazar otra vez").isFalse();
        assertThat(dao.aprobar(rechazado.id(), otroAdmin)).as("aprobar uno ya rechazado").isFalse();
        assertThat(dao.buscarPorId(rechazado.id())).contains(despuesDeRechazar);
        assertThat(despuesDeRechazar.comentario()).isEqualTo("Borroso");
        assertThat(despuesDeRechazar.revisadoPor()).isEqualTo(correo("admin"));
    }

    @Test
    void revisarUnDocumentoQueNoExisteDevuelveFalse() {
        assertThat(dao.aprobar(UUID.randomUUID(), admin)).isFalse();
        assertThat(dao.rechazar(UUID.randomUUID(), admin, "x")).isFalse();
    }

    @Test
    void dosRevisionesAlMismoTiempoSoloUnaGana() throws Exception {
        Documento d = subir("PAZ_Y_SALVO", acudiente);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Future<Boolean> aprobar = hilos.submit(() -> {
                salida.await();
                return dao.aprobar(d.id(), admin);
            });
            Future<Boolean> rechazar = hilos.submit(() -> {
                salida.await();
                return dao.rechazar(d.id(), admin, "Borroso");
            });
            salida.countDown();

            assertThat(List.of(aprobar.get(20, TimeUnit.SECONDS), rechazar.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            hilos.shutdownNow();
        }
    }

    // --- Propiedad ------------------------------------------------------------------------------------------

    @Test
    void esAcudienteDeEsVerdaderoParaElDuenoYFalsoParaOtro() {
        UUID otroAcudiente = cuenta("otro-acudiente", "ACUDIENTE");
        vincular(acudiente, deportista);
        vincular(otroAcudiente, deportista());

        assertThat(dao.esAcudienteDe(acudiente, deportista)).isTrue();
        assertThat(dao.esAcudienteDe(otroAcudiente, deportista)).isFalse();
        assertThat(dao.esAcudienteDe(admin, deportista)).as("una cuenta sin ficha de acudiente").isFalse();
    }

    @Test
    void esAcudienteDeEsFalsoConUsuarioNuloODeportistaInexistente() {
        vincular(acudiente, deportista);

        assertThat(dao.esAcudienteDe(null, deportista)).isFalse();
        assertThat(dao.esAcudienteDe(acudiente, null)).isFalse();
        assertThat(dao.esAcudienteDe(acudiente, UUID.randomUUID())).isFalse();
        assertThat(dao.esAcudienteDe(UUID.randomUUID(), deportista)).isFalse();
    }

    @Test
    void unAcudienteConVariosHijosEsDeTodos() {
        UUID otroHijo = deportista();
        vincular(acudiente, deportista);
        vincular(acudiente, otroHijo);

        assertThat(dao.esAcudienteDe(acudiente, deportista)).isTrue();
        assertThat(dao.esAcudienteDe(acudiente, otroHijo)).isTrue();
    }

    // --- Bloqueo y concurrencia -----------------------------------------------------------------------------

    @Test
    void bloquearDeportistaDevuelveFalseSiNoExiste() {
        boolean existente = Boolean.TRUE.equals(
                new TransactionTemplate(administrador).execute(s -> dao.bloquearDeportista(deportista)));
        boolean inexistente = Boolean.TRUE.equals(
                new TransactionTemplate(administrador).execute(s -> dao.bloquearDeportista(UUID.randomUUID())));
        assertThat(existente).isTrue();
        assertThat(inexistente).isFalse();
    }

    @Test
    void bloquearDeportistaEsperaAQuienYaLoTieneBloqueado() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        try {
            Future<?> a = hilos.submit(() -> new TransactionTemplate(administrador).executeWithoutResult(estado -> {
                dao.bloquearDeportista(deportista);
                bloqueado.countDown();
                try {
                    soltar.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> b = hilos.submit(() -> new TransactionTemplate(administrador).execute(s -> dao.bloquearDeportista(deportista)));
            Thread.sleep(1500);
            assertThat(b.isDone()).as("B espera a que A termine su transacción").isFalse();

            soltar.countDown();
            a.get(10, TimeUnit.SECONDS);
            assertThat(b.get(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            soltar.countDown();
            hilos.shutdownNow();
        }
    }

    /**
     * Dos subidas del mismo tipo para el mismo deportista, cada una en su transacción: bloquear, ver si ya hay uno
     * activo e insertar. La pausa entre la revisión y el insert abre la ventana de la carrera: sin el bloqueo las dos
     * verían "no hay" e insertarían (la tabla no tiene unicidad por deportista y tipo).
     */
    @Test
    void dosSubidasConcurrentesDelMismoTipoDejanExactamenteUnDocumentoActivo() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Callable<Boolean> subida = () -> {
                salida.await();
                return new TransactionTemplate(administrador).execute(estado -> {
                    dao.bloquearDeportista(deportista);
                    if (dao.existeActivoDelTipo(deportista, "DOCUMENTO_IDENTIDAD")) {
                        return false;
                    }
                    pausa(300);
                    dao.insertar(deportista, "DOCUMENTO_IDENTIDAD", ruta(), "ti.pdf", "application/pdf", 10, null, acudiente);
                    return true;
                });
            };
            Future<Boolean> a = hilos.submit(subida);
            Future<Boolean> b = hilos.submit(subida);
            salida.countDown();

            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM documento
                     WHERE id_deportista = :d AND tipo = 'DOCUMENTO_IDENTIDAD' AND estado IN ('PENDIENTE', 'APROBADO')""",
                    new MapSqlParameterSource("d", deportista), Long.class)).isEqualTo(1L);
        } finally {
            hilos.shutdownNow();
        }
    }

    // --- Datos ----------------------------------------------------------------------------------------------

    private static void pausa(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String ruta() {
        return "ruta-" + UUID.randomUUID() + "/" + (++contador) + ".pdf";
    }

    private Documento subir(String tipo, UUID usuario) {
        return dao.insertar(deportista, tipo, ruta(), "archivo.pdf", "application/pdf", 100, null, usuario);
    }

    private void fecharCarga(Documento d, String instante) {
        jdbc.update("UPDATE documento SET cargado_en = CAST(:t AS timestamptz) WHERE id_documento = :id",
                new MapSqlParameterSource("t", instante).addValue("id", d.id()));
    }

    private static String correo(String nombre) {
        return CORREO_PG + nombre + "@pradofc.co";
    }

    /** Una cuenta (auth.users + usuario + un rol). */
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
                new MapSqlParameterSource("doc", "10" + (++contador) + System.nanoTime() % 100000), UUID.class);
    }

    /** Vincula la cuenta (creándole su ficha de acudiente) con el deportista. */
    private void vincular(UUID usuario, UUID idDeportista) {
        UUID ficha = jdbc.query("SELECT id_acudiente FROM acudiente WHERE id_usuario = :u",
                new MapSqlParameterSource("u", usuario), (r, n) -> r.getObject(1, UUID.class)).stream().findFirst()
                .orElseGet(() -> jdbc.queryForObject("""
                        INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo, id_usuario)
                        SELECT 'Acu', 'Diente', 'CC', :doc, DATE '1985-05-05', u.correo, u.id_usuario
                          FROM usuario u WHERE u.id_usuario = :u RETURNING id_acudiente""",
                        new MapSqlParameterSource("doc", "9" + (++contador) + System.nanoTime() % 100000).addValue("u", usuario),
                        UUID.class));
        jdbc.update("INSERT INTO acudiente_deportista (id_acudiente, id_deportista) VALUES (:a, :d)",
                new MapSqlParameterSource("a", ficha).addValue("d", idDeportista));
    }
}
