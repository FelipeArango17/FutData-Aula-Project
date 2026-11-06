package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import co.edu.futdata.api.dto.DocumentosDeportistaResponseDTO;
import co.edu.futdata.config.DocumentosProperties;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.persistence.impl.DestinatariosDAOImpl;
import co.edu.futdata.datasource.external.ArchivoNoEncontradoException;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.EstadoDocumento;
import co.edu.futdata.persistence.impl.DeportistaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.DocumentoDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.Solicitante;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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
 * Subir, listar y descargar de punta a punta con los DAO y el servicio reales sobre PostgreSQL, y un Storage en memoria
 * (el esquema {@code storage} no existe en las pruebas). La propiedad y el "un activo por tipo" se deciden en la base.
 */
@ExtendWith(DockerDisponible.class)
class DocumentoServiceConPostgresTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final String CORREO_PG = "docsvc-pg-";
    private static final byte[] PDF = "%PDF-1.7\ncontenido".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    /** Storage en memoria: guarda los archivos por ruta; firma con una URL de ejemplo. */
    private static final class StorageEnMemoria implements IAlmacenamientoArchivos {
        final Map<String, byte[]> archivos = new ConcurrentHashMap<>();

        @Override
        public String subir(String bucket, String ruta, byte[] contenido, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String subir(String bucket, String ruta, byte[] contenido, String contentType, List<String> tipos) {
            archivos.put(bucket + "/" + ruta, contenido);
            return ruta;
        }

        @Override
        public void eliminar(String bucket, String ruta) {
            if (archivos.remove(bucket + "/" + ruta) == null) {
                throw new ArchivoNoEncontradoException("no está", 404, null);
            }
        }

        @Override
        public String urlFirmada(String bucket, String ruta, Duration vigencia) {
            if (!archivos.containsKey(bucket + "/" + ruta)) {
                throw new ArchivoNoEncontradoException("no está", 404, null);
            }
            return "https://firmada.example/" + bucket + "/" + ruta + "?exp=" + vigencia.toSeconds();
        }
    }

    private NamedParameterJdbcTemplate jdbc;
    private DocumentoDAOImpl documentosDao;
    private StorageEnMemoria storage;
    private INotificador notificador;
    private DocumentoServiceImpl servicio;
    private UUID deportista;
    private Solicitante admin;
    private Solicitante dueno;
    private Solicitante ajeno;
    private int contador;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        documentosDao = new DocumentoDAOImpl(jdbc);
        storage = new StorageEnMemoria();
        notificador = mock(INotificador.class);
        servicio = new DocumentoServiceImpl(documentosDao, new DeportistaDAOImpl(jdbc), storage,
                new DocumentosProperties("documentos", Duration.ofSeconds(300)), RELOJ,
                new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource())),
                new DestinatariosDAOImpl(jdbc), notificador, new FrontendProperties("https://futdata.test"));
        deportista = deportista();
        admin = new Solicitante(cuenta("admin", "ADMINISTRADOR"), Solicitante.ADMINISTRADOR);
        dueno = new Solicitante(cuenta("dueno", "ACUDIENTE"), Solicitante.ACUDIENTE);
        ajeno = new Solicitante(cuenta("ajeno", "ACUDIENTE"), Solicitante.ACUDIENTE);
        vincular(dueno.idUsuario(), deportista);
        vincular(ajeno.idUsuario(), deportista());
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

    @Test
    void subirGuardaElArchivoYLaFilaPendiente() {
        Documento d = servicio.subir(dueno, deportista, "PAZ_Y_SALVO", "mi paz y salvo.pdf", PDF, "2027-01-31");

        assertThat(d.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(d.tipoContenido()).isEqualTo("application/pdf");
        assertThat(d.tamanoBytes()).isEqualTo(PDF.length);
        assertThat(d.nombreOriginal()).isEqualTo("mi paz y salvo.pdf");
        assertThat(d.rolCargadoPor()).isEqualTo("ACUDIENTE");
        assertThat(d.cargadoPorCorreo()).isEqualTo(CORREO_PG + "dueno@pradofc.co");
        assertThat(d.ruta()).matches(deportista + "/[0-9a-f-]{36}\\.pdf");
        assertThat(storage.archivos).containsOnlyKeys("documentos/" + d.ruta());
    }

    @Test
    void subirDosVecesElMismoTipoEs201YLuego409SinDejarUnSegundoArchivo() {
        servicio.subir(dueno, deportista, "DOCUMENTO_IDENTIDAD", "ti.png", PNG, null);

        assertThatThrownBy(() -> servicio.subir(dueno, deportista, "DOCUMENTO_IDENTIDAD", "ti2.png", PNG, null))
                .isInstanceOf(ConflictoException.class);

        assertThat(storage.archivos).hasSize(1);
        assertThat(documentosDao.listarPorDeportista(deportista)).hasSize(1);
    }

    @Test
    void tambienEsConflictoSiElPrimeroYaEstaAprobado() {
        Documento primero = servicio.subir(dueno, deportista, "DOCUMENTO_IDENTIDAD", "ti.png", PNG, null);
        documentosDao.aprobar(primero.id(), admin.idUsuario());

        assertThatThrownBy(() -> servicio.subir(admin, deportista, "DOCUMENTO_IDENTIDAD", "ti.png", PNG, null))
                .isInstanceOf(ConflictoException.class);
        assertThat(storage.archivos).hasSize(1);
    }

    @Test
    void unRechazadoSePuedeVolverASubirYQuedaElHistorial() {
        Documento primero = servicio.subir(dueno, deportista, "CERTIFICADO_ESTUDIO", "cert.pdf", PDF, null);
        documentosDao.rechazar(primero.id(), admin.idUsuario(), "Está cortado.");

        Documento segundo = servicio.subir(dueno, deportista, "CERTIFICADO_ESTUDIO", "cert-bien.pdf", PDF, null);

        assertThat(segundo.id()).isNotEqualTo(primero.id());
        assertThat(segundo.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        DocumentosDeportistaResponseDTO lista = servicio.listar(dueno, deportista);
        assertThat(lista.documentos()).extracting(d -> d.id()).containsExactlyInAnyOrder(primero.id(), segundo.id());
        assertThat(lista.documentos()).filteredOn(d -> d.id().equals(primero.id())).singleElement().satisfies(d -> {
            assertThat(d.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
            assertThat(d.comentario()).isEqualTo("Está cortado.");
            assertThat(d.revisadoPor()).isEqualTo(CORREO_PG + "admin@pradofc.co");
        });
        assertThat(storage.archivos).hasSize(2);
    }

    @Test
    void elAcudienteQueNoEsDuenoRecibe404EnLasTresOperacionesYNoSeTocaStorage() {
        Documento d = servicio.subir(dueno, deportista, "PAZ_Y_SALVO", "p.pdf", PDF, null);

        assertThatThrownBy(() -> servicio.subir(ajeno, deportista, "CARTA_RENUNCIA", "c.pdf", PDF, null))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe un deportista con ese id.");
        assertThatThrownBy(() -> servicio.listar(ajeno, deportista))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe un deportista con ese id.");
        assertThatThrownBy(() -> servicio.descargar(ajeno, d.id()))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe un documento con ese id.");
        assertThat(storage.archivos).hasSize(1);
        assertThat(documentosDao.listarPorDeportista(deportista)).hasSize(1);
    }

    @Test
    void unAdministradorVeLosDocumentosDeCualquierDeportista() {
        servicio.subir(dueno, deportista, "PAZ_Y_SALVO", "p.pdf", PDF, null);

        assertThat(servicio.listar(admin, deportista).documentos()).hasSize(1);
    }

    @Test
    void unDeportistaInexistenteEs404() {
        assertThatThrownBy(() -> servicio.subir(admin, UUID.randomUUID(), "PAZ_Y_SALVO", "p.pdf", PDF, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(storage.archivos).isEmpty();
    }

    @Test
    void aprobarLosCuatroObligatoriosCompletaLaInscripcionSinTocarMasQueLosDocumentos() {
        DocumentosDeportistaResponseDTO inicial = servicio.listar(dueno, deportista);
        assertThat(inicial.estadoInscripcion()).isEqualTo("PENDIENTE");
        assertThat(inicial.documentosFaltantes()).containsExactly("DOCUMENTO_IDENTIDAD", "CERTIFICADO_ESTUDIO",
                "FORMULARIO_INSCRIPCION", "AUTORIZACION_HABEAS_DATA");

        for (String tipo : List.of("DOCUMENTO_IDENTIDAD", "CERTIFICADO_ESTUDIO", "FORMULARIO_INSCRIPCION")) {
            documentosDao.aprobar(servicio.subir(dueno, deportista, tipo, tipo + ".pdf", PDF, null).id(), admin.idUsuario());
        }
        DocumentosDeportistaResponseDTO casi = servicio.listar(dueno, deportista);
        assertThat(casi.estadoInscripcion()).isEqualTo("PENDIENTE");
        assertThat(casi.documentosFaltantes()).containsExactly("AUTORIZACION_HABEAS_DATA");

        documentosDao.aprobar(servicio.subir(dueno, deportista, "AUTORIZACION_HABEAS_DATA", "h.pdf", PDF, null).id(),
                admin.idUsuario());
        DocumentosDeportistaResponseDTO completa = servicio.listar(dueno, deportista);
        assertThat(completa.estadoInscripcion()).isEqualTo("COMPLETA");
        assertThat(completa.documentosFaltantes()).isEmpty();
        // y la columna heredada no se escribió
        assertThat(jdbc.queryForObject("SELECT estado_inscripcion FROM deportista WHERE id_deportista = :d",
                new MapSqlParameterSource("d", deportista), String.class)).isEqualTo("PENDIENTE");
    }

    @Test
    void unRechazadoNoCuentaParaLaInscripcion() {
        Documento d = servicio.subir(dueno, deportista, "DOCUMENTO_IDENTIDAD", "ti.pdf", PDF, null);
        documentosDao.rechazar(d.id(), admin.idUsuario(), "Borroso");

        assertThat(servicio.listar(dueno, deportista).documentosFaltantes()).contains("DOCUMENTO_IDENTIDAD");
    }

    @Test
    void descargarDevuelveLaUrlFirmadaDeLosCincoMinutos() {
        Documento d = servicio.subir(dueno, deportista, "PAZ_Y_SALVO", "p.pdf", PDF, null);

        var descarga = servicio.descargar(dueno, d.id());

        assertThat(descarga.url()).isEqualTo("https://firmada.example/documentos/" + d.ruta() + "?exp=300");
        assertThat(descarga.vigenciaSegundos()).isEqualTo(300);
        assertThat(servicio.descargar(admin, d.id()).url()).isEqualTo(descarga.url());
    }

    @Test
    void descargarUnDocumentoInexistenteEs404() {
        assertThatThrownBy(() -> servicio.descargar(admin, UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void dosSubidasConcurrentesDelMismoTipoDejanUnDocumentoYUnSoloArchivo() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Callable<String> subida = () -> {
                salida.await();
                try {
                    servicio.subir(dueno, deportista, "DOCUMENTO_IDENTIDAD", "ti.png", PNG, null);
                    return "creado";
                } catch (ConflictoException e) {
                    return "conflicto";
                }
            };
            Future<String> a = hilos.submit(subida);
            Future<String> b = hilos.submit(subida);
            salida.countDown();

            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("creado", "conflicto");
            assertThat(documentosDao.listarPorDeportista(deportista)).hasSize(1);
            // El que perdió la carrera borró su archivo: no queda ninguno huérfano
            assertThat(storage.archivos).hasSize(1);
            assertThat(storage.archivos.keySet()).containsExactly("documentos/" + documentosDao.listarPorDeportista(deportista).get(0).ruta());
        } finally {
            hilos.shutdownNow();
        }
    }

    // --- Aprobar y rechazar ---------------------------------------------------------------------------------

    private static final List<String> OBLIGATORIOS = List.of("DOCUMENTO_IDENTIDAD", "CERTIFICADO_ESTUDIO",
            "FORMULARIO_INSCRIPCION", "AUTORIZACION_HABEAS_DATA");

    private Documento subirTipo(String tipo) {
        return servicio.subir(dueno, deportista, tipo, tipo + ".pdf", PDF, null);
    }

    private String estadoHeredado() {
        return jdbc.queryForObject("SELECT estado_inscripcion FROM deportista WHERE id_deportista = :d",
                new MapSqlParameterSource("d", deportista), String.class);
    }

    @Test
    void aprobarLosCuatroObligatoriosUnoAUnoDejaPendienteHastaElCuartoYLuegoCompleta() {
        String heredadaAntes = estadoHeredado();
        List<String> estados = new java.util.ArrayList<>();
        for (String tipo : OBLIGATORIOS) {
            estados.add(servicio.aprobar(admin, subirTipo(tipo).id()).estadoInscripcion());
        }

        assertThat(estados).containsExactly("PENDIENTE", "PENDIENTE", "PENDIENTE", "COMPLETA");
        DocumentosDeportistaResponseDTO lista = servicio.listar(dueno, deportista);
        assertThat(lista.estadoInscripcion()).isEqualTo("COMPLETA");
        assertThat(lista.documentosFaltantes()).isEmpty();
        assertThat(estadoHeredado()).as("la columna heredada no se escribe").isEqualTo(heredadaAntes);
    }

    @Test
    void aprobarPoneRevisadoPorYRevisadoEn() {
        Documento d = subirTipo("PAZ_Y_SALVO");

        var respuesta = servicio.aprobar(admin, d.id());

        assertThat(respuesta.documento().estado()).isEqualTo(EstadoDocumento.APROBADO);
        assertThat(respuesta.documento().revisadoPor()).isEqualTo(CORREO_PG + "admin@pradofc.co");
        assertThat(respuesta.documento().revisadoEn()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT revisado_por FROM documento WHERE id_documento = :d",
                new MapSqlParameterSource("d", d.id()), UUID.class)).isEqualTo(admin.idUsuario());
        assertThat(jdbc.queryForObject("SELECT revisado_en IS NOT NULL FROM documento WHERE id_documento = :d",
                new MapSqlParameterSource("d", d.id()), Boolean.class)).isTrue();
    }

    @Test
    void rechazarGuardaElComentarioRecortadoYElRevisor() {
        Documento d = subirTipo("PAZ_Y_SALVO");

        var respuesta = servicio.rechazar(admin, d.id(), "   La imagen está borrosa.  ");

        assertThat(respuesta.documento().estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(respuesta.documento().comentario()).isEqualTo("La imagen está borrosa.");
        assertThat(respuesta.documento().revisadoPor()).isEqualTo(CORREO_PG + "admin@pradofc.co");
        assertThat(respuesta.documento().revisadoEn()).isNotNull();
        assertThat(respuesta.avisoEnviado()).as("el deportista tiene un acudiente con correo y n8n (simulado) aceptó").isTrue();
    }

    @Test
    void rechazarUnAprobadoEs409YNoCambiaNada() {
        Documento d = subirTipo("DOCUMENTO_IDENTIDAD");
        servicio.aprobar(admin, d.id());

        assertThatThrownBy(() -> servicio.rechazar(admin, d.id(), "Me equivoqué"))
                .isInstanceOf(ConflictoException.class);

        Documento leido = documentosDao.buscarPorId(d.id()).orElseThrow();
        assertThat(leido.estado()).isEqualTo(EstadoDocumento.APROBADO);
        assertThat(leido.comentario()).isNull();
    }

    @Test
    void aprobarOtraVezOAprobarUnRechazadoEs409() {
        Documento aprobado = subirTipo("PAZ_Y_SALVO");
        servicio.aprobar(admin, aprobado.id());
        Documento rechazado = subirTipo("CARTA_RENUNCIA");
        servicio.rechazar(admin, rechazado.id(), "Borroso");

        assertThatThrownBy(() -> servicio.aprobar(admin, aprobado.id())).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.aprobar(admin, rechazado.id())).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.rechazar(admin, rechazado.id(), "Otro")).isInstanceOf(ConflictoException.class);
        assertThat(documentosDao.buscarPorId(rechazado.id()).orElseThrow().comentario()).isEqualTo("Borroso");
    }

    @Test
    void rechazarUnPendienteHaceQueElEstadoVuelvaADependerDeEseTipo() {
        for (String tipo : OBLIGATORIOS.subList(0, 3)) {
            servicio.aprobar(admin, subirTipo(tipo).id());
        }
        Documento cuarto = subirTipo("AUTORIZACION_HABEAS_DATA");
        assertThat(servicio.listar(dueno, deportista).estadoInscripcion()).as("con el cuarto pendiente").isEqualTo("PENDIENTE");

        var rechazo = servicio.rechazar(admin, cuarto.id(), "Falta la firma.");
        assertThat(rechazo.estadoInscripcion()).isEqualTo("PENDIENTE");
        assertThat(servicio.listar(dueno, deportista).documentosFaltantes()).containsExactly("AUTORIZACION_HABEAS_DATA");

        // Volver a subir tras el rechazo es posible; al aprobarlo, la inscripción se completa
        Documento nuevo = subirTipo("AUTORIZACION_HABEAS_DATA");
        assertThat(nuevo.id()).isNotEqualTo(cuarto.id());
        assertThat(nuevo.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(servicio.aprobar(admin, nuevo.id()).estadoInscripcion()).isEqualTo("COMPLETA");
        assertThat(servicio.listar(dueno, deportista).documentos()).hasSize(5);
    }

    @Test
    void siSeRechazaUnObligatorioYaAprobadoNoSePuedeAsiQueLaInscripcionNoRetrocede() {
        for (String tipo : OBLIGATORIOS) {
            servicio.aprobar(admin, subirTipo(tipo).id());
        }
        Documento uno = documentosDao.listarPorDeportista(deportista).get(0);

        assertThatThrownBy(() -> servicio.rechazar(admin, uno.id(), "Tarde")).isInstanceOf(ConflictoException.class);
        assertThat(servicio.listar(dueno, deportista).estadoInscripcion()).isEqualTo("COMPLETA");
    }

    @Test
    void unDocumentoInexistenteEs404() {
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.aprobar(admin, inexistente)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.rechazar(admin, inexistente, "x")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void soloElAdministradorRevisaYUnAcudienteDuenoRecibe403SinCambiarNada() {
        Documento d = subirTipo("PAZ_Y_SALVO");

        assertThatThrownBy(() -> servicio.aprobar(dueno, d.id()))
                .isInstanceOf(co.edu.futdata.services.facade.SinPermisoException.class);
        assertThatThrownBy(() -> servicio.rechazar(dueno, d.id(), "Borroso"))
                .isInstanceOf(co.edu.futdata.services.facade.SinPermisoException.class);

        assertThat(documentosDao.buscarPorId(d.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.PENDIENTE);
    }

    @Test
    void dosRevisionesSimultaneasDelMismoDocumentoGanaUnaSolaYLaOtraRecibe409() throws Exception {
        Documento d = subirTipo("CARTA_RENUNCIA");
        Solicitante otroAdmin = new Solicitante(cuenta("admin2", "ADMINISTRADOR"), Solicitante.ADMINISTRADOR);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Future<String> aprobar = hilos.submit(() -> {
                salida.await();
                try {
                    servicio.aprobar(admin, d.id());
                    return "gana";
                } catch (ConflictoException e) {
                    return "409";
                }
            });
            Future<String> rechazar = hilos.submit(() -> {
                salida.await();
                try {
                    servicio.rechazar(otroAdmin, d.id(), "Borroso");
                    return "gana";
                } catch (ConflictoException e) {
                    return "409";
                }
            });
            salida.countDown();
            String resultadoAprobar = aprobar.get(30, TimeUnit.SECONDS);
            String resultadoRechazar = rechazar.get(30, TimeUnit.SECONDS);

            assertThat(List.of(resultadoAprobar, resultadoRechazar)).containsExactlyInAnyOrder("gana", "409");
            Documento leido = documentosDao.buscarPorId(d.id()).orElseThrow();
            // El estado final y el revisor son los del que ganó, sin mezclas
            if (resultadoAprobar.equals("gana")) {
                assertThat(leido.estado()).isEqualTo(EstadoDocumento.APROBADO);
                assertThat(leido.comentario()).isNull();
                assertThat(leido.revisadoPor()).isEqualTo(CORREO_PG + "admin@pradofc.co");
            } else {
                assertThat(leido.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
                assertThat(leido.comentario()).isEqualTo("Borroso");
                assertThat(leido.revisadoPor()).isEqualTo(CORREO_PG + "admin2@pradofc.co");
            }
            assertThat(leido.revisadoEn()).isNotNull();
        } finally {
            hilos.shutdownNow();
        }
    }

    // --- Aviso DOCUMENTO_RECHAZADO --------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> avisosEnviados() {
        org.mockito.ArgumentCaptor<Map<String, Object>> datos = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(notificador, org.mockito.Mockito.atLeast(0)).notificar(eq(INotificador.DOCUMENTO_RECHAZADO), datos.capture());
        return datos.getAllValues();
    }

    @Test
    void rechazarConUnAcudienteAvisaUnaVezConSuCorreoYLosDatosDelDocumento() {
        Documento d = subirTipo("PAZ_Y_SALVO");

        var respuesta = servicio.rechazar(admin, d.id(), "  Falta la firma.  ");

        assertThat(respuesta.avisoEnviado()).isTrue();
        List<Map<String, Object>> avisos = avisosEnviados();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0)).containsEntry("correo", CORREO_PG + "dueno@pradofc.co")
                .containsEntry("nombreAcudiente", "Acu Diente").containsEntry("nombreDeportista", "Hijo Prueba")
                .containsEntry("documento", "Paz y salvo").containsEntry("comentario", "Falta la firma.")
                .containsEntry("enlaceDocumentos", "https://futdata.test/acudiente/documentos");
    }

    @Test
    void conDosAcudientesDistintosSonDosLlamadasCadaUnaConSuCorreo() {
        vincular(cuenta("madre", "ACUDIENTE"), deportista);
        Documento d = subirTipo("PAZ_Y_SALVO");

        var respuesta = servicio.rechazar(admin, d.id(), "Borroso");

        assertThat(respuesta.avisoEnviado()).isTrue();
        assertThat(avisosEnviados()).extracting(a -> a.get("correo"))
                .containsExactlyInAnyOrder(CORREO_PG + "dueno@pradofc.co", CORREO_PG + "madre@pradofc.co");
        verify(notificador, times(2)).notificar(eq(INotificador.DOCUMENTO_RECHAZADO), any());
    }

    @Test
    void unAcudienteSinCorreoNoCuentaYElOtroSiRecibeElAviso() {
        jdbc.update("""
                WITH nuevo AS (
                    INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo)
                    VALUES ('Sin', 'Correo', 'CC', :doc, DATE '1985-05-05', NULL) RETURNING id_acudiente)
                INSERT INTO acudiente_deportista (id_acudiente, id_deportista) SELECT id_acudiente, :d FROM nuevo""",
                new MapSqlParameterSource("d", deportista).addValue("doc", "8" + System.nanoTime() % 1000000));
        Documento d = subirTipo("PAZ_Y_SALVO");

        var respuesta = servicio.rechazar(admin, d.id(), "Borroso");

        assertThat(respuesta.avisoEnviado()).isTrue();
        assertThat(avisosEnviados()).extracting(a -> a.get("correo")).containsExactly(CORREO_PG + "dueno@pradofc.co");
    }

    @Test
    void unDeportistaSinAcudientesDaNullYNoSeLlamaANotificar() {
        UUID sinFamilia = deportista();
        Documento d = servicio.subir(admin, sinFamilia, "PAZ_Y_SALVO", "p.pdf", PDF, null);

        var respuesta = servicio.rechazar(admin, d.id(), "Borroso");

        assertThat(respuesta.avisoEnviado()).isNull();
        assertThat(respuesta.documento().estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        verifyNoInteractions(notificador);
    }

    @Test
    void siN8nFallaElRechazoQuedaHechoEnLaBaseYElAvisoEsFalse() {
        doThrow(new IllegalStateException("n8n caído")).when(notificador).notificar(any(), any());
        Documento d = subirTipo("PAZ_Y_SALVO");

        var respuesta = servicio.rechazar(admin, d.id(), "Borroso");

        assertThat(respuesta.avisoEnviado()).isFalse();
        Documento leido = documentosDao.buscarPorId(d.id()).orElseThrow();
        assertThat(leido.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(leido.comentario()).isEqualTo("Borroso");
        assertThat(leido.revisadoPor()).isEqualTo(CORREO_PG + "admin@pradofc.co");
    }

    @Test
    void aprobarYLosCaminosQueFallanNoAvisanNada() {
        Documento aprobado = subirTipo("PAZ_Y_SALVO");
        servicio.aprobar(admin, aprobado.id());
        Documento otro = subirTipo("CARTA_RENUNCIA");

        assertThatThrownBy(() -> servicio.rechazar(admin, aprobado.id(), "Tarde")).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.rechazar(admin, otro.id(), "  "))
                .isInstanceOf(co.edu.futdata.services.facade.DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.rechazar(dueno, otro.id(), "Borroso"))
                .isInstanceOf(co.edu.futdata.services.facade.SinPermisoException.class);
        assertThatThrownBy(() -> servicio.rechazar(admin, UUID.randomUUID(), "Borroso"))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(notificador, never()).notificar(any(), any());
    }

    // --- Datos ----------------------------------------------------------------------------------------------

    private UUID cuenta(String nombre, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", new MapSqlParameterSource("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, :n, 'Prueba', :c)",
                new MapSqlParameterSource("id", id).addValue("n", nombre).addValue("c", CORREO_PG + nombre + "@pradofc.co"));
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

    private void vincular(UUID usuario, UUID idDeportista) {
        UUID ficha = jdbc.queryForObject("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo, id_usuario)
                SELECT 'Acu', 'Diente', 'CC', :doc, DATE '1985-05-05', u.correo, u.id_usuario
                  FROM usuario u WHERE u.id_usuario = :u RETURNING id_acudiente""",
                new MapSqlParameterSource("doc", "9" + (++contador) + System.nanoTime() % 100000).addValue("u", usuario),
                UUID.class);
        jdbc.update("INSERT INTO acudiente_deportista (id_acudiente, id_deportista) VALUES (:a, :d)",
                new MapSqlParameterSource("a", ficha).addValue("d", idDeportista));
    }
}
