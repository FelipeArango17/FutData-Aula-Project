package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.futdata.api.dto.DescargaDocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentosDeportistaResponseDTO;
import co.edu.futdata.config.DocumentosProperties;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.AlmacenamientoNoDisponibleException;
import co.edu.futdata.datasource.external.ArchivoNoEncontradoException;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.persistence.dao.Destinatario;
import co.edu.futdata.persistence.dao.IDestinatariosDAO;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.EstadoDocumento;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.IDocumentoDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class DocumentoServiceImplTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final UUID DEPORTISTA = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DOCUMENTO = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final byte[] PDF = "%PDF-1.7\ncontenido".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final String RUTA_SECRETA = "ruta/secreta-del-cliente.pdf";

    private IDocumentoDAO documentos;
    private IDeportistaDAO deportistas;
    private IAlmacenamientoArchivos almacenamiento;
    private IDestinatariosDAO destinatarios;
    private INotificador notificador;
    private TransactionOperations transaccion;
    private AtomicBoolean dentroDeLaTransaccion;
    private DocumentoServiceImpl servicio;

    private final Solicitante admin = new Solicitante(ADMIN, Solicitante.ADMINISTRADOR);
    private final Solicitante acudiente = new Solicitante(ACUDIENTE, Solicitante.ACUDIENTE);

    @BeforeEach
    void preparar() {
        documentos = mock(IDocumentoDAO.class);
        deportistas = mock(IDeportistaDAO.class);
        almacenamiento = mock(IAlmacenamientoArchivos.class);
        destinatarios = mock(IDestinatariosDAO.class);
        notificador = mock(INotificador.class);
        transaccion = mock(TransactionOperations.class);
        dentroDeLaTransaccion = new AtomicBoolean(false);
        servicio = servicio(Duration.ofSeconds(300));

        Deportista d = new Deportista();
        d.setId(DEPORTISTA);
        d.setEstadoInscripcion("PENDIENTE");
        when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenReturn(Optional.of(d));
        when(documentos.esAcudienteDe(ACUDIENTE, DEPORTISTA)).thenReturn(true);
        when(documentos.bloquearDeportista(DEPORTISTA)).thenReturn(true);
        when(documentos.insertar(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(i -> documento());
        when(transaccion.execute(any())).thenAnswer(i -> {
            dentroDeLaTransaccion.set(true);
            try {
                return ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class));
            } finally {
                dentroDeLaTransaccion.set(false);
            }
        });
    }

    private DocumentoServiceImpl servicio(Duration vigencia) {
        return servicio(vigencia, new FrontendProperties("https://futdata.test"));
    }

    private DocumentoServiceImpl servicio(Duration vigencia, FrontendProperties frontend) {
        return new DocumentoServiceImpl(documentos, deportistas, almacenamiento,
                new DocumentosProperties("documentos", vigencia), RELOJ, transaccion, destinatarios, notificador, frontend);
    }

    private static Documento documento() {
        return new Documento(DOCUMENTO, DEPORTISTA, "PAZ_Y_SALVO", RUTA_SECRETA, "paz.pdf", "application/pdf", 10,
                EstadoDocumento.PENDIENTE, null, null, "ACUDIENTE", "familia@correo.co", Instant.parse("2026-11-05T15:00:00Z"),
                null, null);
    }

    private void subirComo(Solicitante s) {
        servicio.subir(s, DEPORTISTA, "PAZ_Y_SALVO", "paz.pdf", PDF, null);
    }

    // --- Autorización ---------------------------------------------------------------------------------------

    @Nested
    class Autorizacion {

        @Test
        void elAdministradorPuedeConCualquierDeportista() {
            subirComo(admin);
            servicio.listar(admin, DEPORTISTA);
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
            servicio.descargar(admin, DOCUMENTO);

            verify(documentos, never()).esAcudienteDe(any(), any());
        }

        @Test
        void elAcudienteDuenoPuede() {
            subirComo(acudiente);
            servicio.listar(acudiente, DEPORTISTA);
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
            when(documentos.esAcudienteDe(ACUDIENTE, DEPORTISTA)).thenReturn(true);
            servicio.descargar(acudiente, DOCUMENTO);
        }

        @Test
        void unAcudienteQueNoEsDuenoRecibeElMismoMensajeQueUnDeportistaInexistente() {
            when(documentos.esAcudienteDe(ACUDIENTE, DEPORTISTA)).thenReturn(false);
            UUID inexistente = UUID.randomUUID();
            when(deportistas.buscarPorId(eq(inexistente), anyInt())).thenReturn(Optional.empty());

            Throwable ajeno = org.assertj.core.api.Assertions.catchThrowable(
                    () -> servicio.listar(acudiente, DEPORTISTA));
            Throwable noExiste = org.assertj.core.api.Assertions.catchThrowable(
                    () -> servicio.listar(admin, inexistente));

            assertThat(ajeno).isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(noExiste).isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(ajeno).hasMessage(noExiste.getMessage()).hasMessage("No existe un deportista con ese id.");
            assertThatThrownBy(() -> subirComo(acudiente)).isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("No existe un deportista con ese id.");
        }

        @Test
        void unAcudienteQueNoEsDuenoDeUnDocumentoRecibeElMismoMensajeQueUnoInexistente() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
            when(documentos.esAcudienteDe(ACUDIENTE, DEPORTISTA)).thenReturn(false);
            UUID inexistente = UUID.randomUUID();
            when(documentos.buscarPorId(inexistente)).thenReturn(Optional.empty());

            Throwable ajeno = org.assertj.core.api.Assertions.catchThrowable(() -> servicio.descargar(acudiente, DOCUMENTO));
            Throwable noExiste = org.assertj.core.api.Assertions.catchThrowable(() -> servicio.descargar(acudiente, inexistente));

            assertThat(ajeno).isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe un documento con ese id.");
            assertThat(noExiste).isInstanceOf(RecursoNoEncontradoException.class).hasMessage(ajeno.getMessage());
            verifyNoInteractions(almacenamiento);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ENTRENADOR", "TESORERO", "", "ROL_RARO", "administrador"})
        void cualquierOtroRolEsUn403SinTocarNada(String rol) {
            Solicitante otro = new Solicitante(UUID.randomUUID(), rol);

            assertThatThrownBy(() -> subirComo(otro)).isInstanceOf(SinPermisoException.class);
            assertThatThrownBy(() -> servicio.listar(otro, DEPORTISTA)).isInstanceOf(SinPermisoException.class);
            assertThatThrownBy(() -> servicio.descargar(otro, DOCUMENTO)).isInstanceOf(SinPermisoException.class);

            sinEfectos();
        }

        @Test
        void rolNuloSubNuloOSolicitanteNuloSonUn403() {
            for (Solicitante s : new Solicitante[] {new Solicitante(UUID.randomUUID(), null),
                    new Solicitante(null, Solicitante.ADMINISTRADOR), new Solicitante(null, Solicitante.ACUDIENTE), null}) {
                assertThatThrownBy(() -> subirComo(s)).isInstanceOf(SinPermisoException.class);
                assertThatThrownBy(() -> servicio.listar(s, DEPORTISTA)).isInstanceOf(SinPermisoException.class);
                assertThatThrownBy(() -> servicio.descargar(s, DOCUMENTO)).isInstanceOf(SinPermisoException.class);
            }
            sinEfectos();
        }

        @Test
        void con404TampocoSeTocaStorageNiSeAbreUnaTransaccion() {
            when(documentos.esAcudienteDe(ACUDIENTE, DEPORTISTA)).thenReturn(false);

            assertThatThrownBy(() -> subirComo(acudiente)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(() -> servicio.listar(acudiente, DEPORTISTA)).isInstanceOf(RecursoNoEncontradoException.class);

            verifyNoInteractions(almacenamiento, transaccion);
            verify(documentos, never()).insertar(any(), any(), any(), any(), any(), anyInt(), any(), any());
        }

        @Test
        void laAutorizacionVaAntesDeValidarNada() {
            Solicitante entrenador = new Solicitante(UUID.randomUUID(), Solicitante.ENTRENADOR);

            // Con datos inválidos, el entrenador recibe 403 y no el 400 de validación
            assertThatThrownBy(() -> servicio.subir(entrenador, DEPORTISTA, "NO_ES_TIPO", "x", new byte[0], "ayer"))
                    .isInstanceOf(SinPermisoException.class);
            when(documentos.esAcudienteDe(ACUDIENTE, DEPORTISTA)).thenReturn(false);
            assertThatThrownBy(() -> servicio.subir(acudiente, DEPORTISTA, "NO_ES_TIPO", "x", new byte[0], "ayer"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        private void sinEfectos() {
            verifyNoInteractions(almacenamiento, transaccion, deportistas);
            verify(documentos, never()).insertar(any(), any(), any(), any(), any(), anyInt(), any(), any());
            verify(documentos, never()).buscarPorId(any());
            verify(documentos, never()).listarPorDeportista(any());
        }
    }

    // --- Subir ----------------------------------------------------------------------------------------------

    @Nested
    class Subir {

        @Test
        void unDeportistaInexistenteEsUn404() {
            when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subirComo(admin)).isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("No existe un deportista con ese id.");
            verifyNoInteractions(almacenamiento, transaccion);
        }

        @Test
        void losErroresDeValidacionVanJuntosYNoSeTocaStorage() {
            assertThatThrownBy(() -> servicio.subir(admin, DEPORTISTA, "FICHA_MEDICA", "x.pdf", new byte[0], "2020-01-01"))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e ->
                            assertThat(e.getCampos()).containsExactly("archivo", "fechaVencimiento", "tipo"));
            verifyNoInteractions(almacenamiento, transaccion);
            verify(documentos, never()).existeActivoDelTipo(any(), any());
        }

        @Test
        void siFallaSoloElArchivoEsElMismo400DeLaFotoConCamposArchivo() {
            assertThatThrownBy(() -> servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "x.pdf",
                    "no soy un pdf".getBytes(StandardCharsets.US_ASCII), null))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e ->
                            assertThat(e.getCampos()).containsExactly("archivo"));
            verifyNoInteractions(almacenamiento, transaccion);
        }

        @Test
        void unTipoNuloFaltaYUnaFechaMalFormadaSeReportan() {
            assertThatThrownBy(() -> servicio.subir(admin, DEPORTISTA, null, "x.pdf", PDF, "05/11/2026"))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e ->
                            assertThat(e.getCampos()).containsExactly("fechaVencimiento", "tipo"));
        }

        @Test
        void unArchivoDeMasDe5MbSeRechazaConUn400DeArchivo() {
            byte[] grande = new byte[5 * 1024 * 1024 + 1];
            System.arraycopy(PDF, 0, grande, 0, PDF.length);

            assertThatThrownBy(() -> servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "x.pdf", grande, null))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e ->
                            assertThat(e.getCampos()).containsExactly("archivo"));
            verifyNoInteractions(almacenamiento);
        }

        @Test
        void elConflictoRapidoNoSubeNada() {
            when(documentos.existeActivoDelTipo(DEPORTISTA, "PAZ_Y_SALVO")).thenReturn(true);

            assertThatThrownBy(() -> subirComo(admin)).isInstanceOfSatisfying(ConflictoException.class, e -> {
                assertThat(e.getCampos()).containsExactly("tipo");
                assertThat(e.getMessage()).contains("PENDIENTE o APROBADO").contains("rechazado");
            });
            verifyNoInteractions(almacenamiento, transaccion);
            verify(documentos, never()).insertar(any(), any(), any(), any(), any(), anyInt(), any(), any());
        }

        @Test
        void subeInsertaYDevuelveElDocumento() {
            Documento esperado = documento();
            Documento resultado = servicio.subir(acudiente, DEPORTISTA, "PAZ_Y_SALVO", "paz.pdf", PDF, "2027-01-31");

            assertThat(resultado).isEqualTo(esperado);
            ArgumentCaptor<String> ruta = ArgumentCaptor.forClass(String.class);
            verify(almacenamiento).subir(eq("documentos"), ruta.capture(), eq(PDF), eq("application/pdf"),
                    eq(List.of("application/pdf", "image/jpeg", "image/png")));
            verify(documentos).insertar(DEPORTISTA, "PAZ_Y_SALVO", ruta.getValue(), "paz.pdf", "application/pdf",
                    PDF.length, LocalDate.of(2027, 1, 31), ACUDIENTE);
        }

        @Test
        void elTipoDeContenidoSaleDeLaFirmaNoDelNombreNiDeLaExtension() {
            servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "documento.pdf", PNG, null);

            ArgumentCaptor<String> ruta = ArgumentCaptor.forClass(String.class);
            verify(almacenamiento).subir(eq("documentos"), ruta.capture(), eq(PNG), eq("image/png"), any());
            assertThat(ruta.getValue()).endsWith(".png");
            verify(documentos).insertar(any(), any(), eq(ruta.getValue()), any(), eq("image/png"), anyInt(), any(), any());
        }

        @Test
        void laRutaNuncaLlevaElNombreDelCliente() {
            servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "../../etc/passwd.pdf", PDF, null);

            ArgumentCaptor<String> ruta = ArgumentCaptor.forClass(String.class);
            verify(almacenamiento).subir(eq("documentos"), ruta.capture(), any(), any(), any());
            assertThat(ruta.getValue()).matches(DEPORTISTA + "/[0-9a-f-]{36}\\.pdf")
                    .doesNotContain("passwd").doesNotContain("..");
            // y el nombre se guarda limpio, solo como dato informativo
            verify(documentos).insertar(any(), any(), any(), eq("passwd.pdf"), any(), anyInt(), any(), any());
        }

        @Test
        void cadaSubidaUsaUnaRutaDistinta() {
            servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "a.pdf", PDF, null);
            servicio.subir(admin, DEPORTISTA, "CARTA_RENUNCIA", "a.pdf", PDF, null);

            ArgumentCaptor<String> rutas = ArgumentCaptor.forClass(String.class);
            verify(almacenamiento, org.mockito.Mockito.times(2)).subir(any(), rutas.capture(), any(), any(), any());
            assertThat(rutas.getAllValues()).doesNotHaveDuplicates();
        }

        @Test
        void storageSeLlamaFueraDeLaTransaccionYAntesDeAbrirla() {
            when(almacenamiento.subir(any(), any(), any(), any(), any())).thenAnswer(i -> {
                assertThat(dentroDeLaTransaccion).as("Storage no puede estar dentro de la transacción").isFalse();
                return i.getArgument(1);
            });

            subirComo(admin);

            InOrder orden = inOrder(documentos, almacenamiento, transaccion);
            orden.verify(documentos).existeActivoDelTipo(DEPORTISTA, "PAZ_Y_SALVO");     // revisión rápida
            orden.verify(almacenamiento).subir(any(), any(), any(), any(), any());
            orden.verify(transaccion).execute(any());
            orden.verify(documentos).bloquearDeportista(DEPORTISTA);
            orden.verify(documentos).existeActivoDelTipo(DEPORTISTA, "PAZ_Y_SALVO");     // otra vez, ya con bloqueo
            orden.verify(documentos).insertar(any(), any(), any(), any(), any(), anyInt(), any(), any());
        }

        @Test
        void elConflictoDentroDeLaTransaccionBorraElArchivoSubido() {
            when(documentos.existeActivoDelTipo(DEPORTISTA, "PAZ_Y_SALVO")).thenReturn(false, true);

            assertThatThrownBy(() -> subirComo(admin)).isInstanceOf(ConflictoException.class);

            ArgumentCaptor<String> subida = ArgumentCaptor.forClass(String.class);
            verify(almacenamiento).subir(eq("documentos"), subida.capture(), any(), any(), any());
            verify(almacenamiento).eliminar("documentos", subida.getValue());
            verify(documentos, never()).insertar(any(), any(), any(), any(), any(), anyInt(), any(), any());
        }

        @Test
        void unFalloDelInsertBorraElArchivoYRelanzaLaExcepcionOriginal() {
            RuntimeException original = new DataIntegrityViolationException("falla de la base");
            when(documentos.insertar(any(), any(), any(), any(), any(), anyInt(), any(), any())).thenThrow(original);

            assertThatThrownBy(() -> subirComo(admin)).isSameAs(original);

            ArgumentCaptor<String> subida = ArgumentCaptor.forClass(String.class);
            verify(almacenamiento).subir(eq("documentos"), subida.capture(), any(), any(), any());
            verify(almacenamiento).eliminar("documentos", subida.getValue());
        }

        @Test
        void siElDeportistaDesapareceEntreLaRevisionYElBloqueoEsUn404YSeBorraElArchivo() {
            when(documentos.bloquearDeportista(DEPORTISTA)).thenReturn(false);

            assertThatThrownBy(() -> subirComo(admin)).isInstanceOf(RecursoNoEncontradoException.class);

            verify(almacenamiento).eliminar(eq("documentos"), anyString());
            verify(documentos, never()).insertar(any(), any(), any(), any(), any(), anyInt(), any(), any());
        }

        @Test
        void siBorrarTambienFallaSeRelanzaLaOriginalYSoloSeRegistraElTipoDeExcepcion() {
            ListAppender<ILoggingEvent> registro = escucharRegistro();
            try {
                RuntimeException original = new DataIntegrityViolationException("falla de la base");
                when(documentos.insertar(any(), any(), any(), any(), any(), anyInt(), any(), any())).thenThrow(original);
                doThrow(new AlmacenamientoNoDisponibleException("No se pudo borrar " + RUTA_SECRETA, 503, null))
                        .when(almacenamiento).eliminar(any(), any());

                assertThatThrownBy(() -> servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "cedula-de-ana.pdf", PDF, null))
                        .isSameAs(original);

                assertThat(registro.list).hasSize(1);
                assertThat(registro.list.get(0).getLevel()).isEqualTo(Level.WARN);
                String mensaje = registro.list.get(0).getFormattedMessage();
                assertThat(mensaje).contains("AlmacenamientoNoDisponibleException")
                        .doesNotContain(RUTA_SECRETA).doesNotContain("cedula").doesNotContain(DEPORTISTA.toString())
                        .doesNotContain("secreta");
            } finally {
                dejarDeEscuchar(registro);
            }
        }

        @Test
        void siElArchivoYaNoEstabaAlBorrarNoSeRegistraNada() {
            ListAppender<ILoggingEvent> registro = escucharRegistro();
            try {
                RuntimeException original = new DataIntegrityViolationException("falla de la base");
                when(documentos.insertar(any(), any(), any(), any(), any(), anyInt(), any(), any())).thenThrow(original);
                doThrow(new ArchivoNoEncontradoException("no está", 404, null)).when(almacenamiento).eliminar(any(), any());

                assertThatThrownBy(() -> subirComo(admin)).isSameAs(original);
                assertThat(registro.list).isEmpty();
            } finally {
                dejarDeEscuchar(registro);
            }
        }

        @Test
        void siFallaStorageNoSeInsertaNadaNiSeAbreLaTransaccion() {
            AlmacenamientoNoDisponibleException falla = new AlmacenamientoNoDisponibleException("sin red", null, null);
            when(almacenamiento.subir(any(), any(), any(), any(), any())).thenThrow(falla);

            assertThatThrownBy(() -> subirComo(admin)).isSameAs(falla);

            verifyNoInteractions(transaccion);
            verify(documentos, never()).insertar(any(), any(), any(), any(), any(), anyInt(), any(), any());
            verify(almacenamiento, never()).eliminar(any(), any());
        }

        @Test
        void laFechaDeVencimientoDeHoySirveYUnaVaciaSeGuardaComoNula() {
            servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "a.pdf", PDF, "2026-11-05");   // hoy en Bogotá
            servicio.subir(admin, DEPORTISTA, "CARTA_RENUNCIA", "b.pdf", PDF, "  ");

            verify(documentos).insertar(any(), eq("PAZ_Y_SALVO"), any(), any(), any(), anyInt(), eq(LocalDate.of(2026, 11, 5)), any());
            verify(documentos).insertar(any(), eq("CARTA_RENUNCIA"), any(), any(), any(), anyInt(), eq((LocalDate) null), any());
        }

        @Test
        void ayerEnBogotaEsUnaFechaPasada() {
            assertThatThrownBy(() -> servicio.subir(admin, DEPORTISTA, "PAZ_Y_SALVO", "a.pdf", PDF, "2026-11-04"))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e ->
                            assertThat(e.getCampos()).containsExactly("fechaVencimiento"));
        }
    }

    // --- Listar ---------------------------------------------------------------------------------------------

    @Nested
    class Listar {

        @Test
        void devuelveLosDocumentosElEstadoYLosFaltantes() {
            Documento aprobado = new Documento(UUID.randomUUID(), DEPORTISTA, "DOCUMENTO_IDENTIDAD", RUTA_SECRETA, "ti.png",
                    "image/png", 5, EstadoDocumento.APROBADO, null, null, "ADMINISTRADOR", "a@x.co",
                    Instant.parse("2026-11-04T15:00:00Z"), "admin@x.co", Instant.parse("2026-11-05T15:00:00Z"));
            when(documentos.listarPorDeportista(DEPORTISTA)).thenReturn(List.of(documento(), aprobado));
            when(deportistas.documentosObligatoriosFaltantes(DEPORTISTA))
                    .thenReturn(List.of("CERTIFICADO_ESTUDIO", "FORMULARIO_INSCRIPCION", "AUTORIZACION_HABEAS_DATA"));

            DocumentosDeportistaResponseDTO respuesta = servicio.listar(acudiente, DEPORTISTA);

            assertThat(respuesta.documentos()).extracting(d -> d.id()).containsExactly(DOCUMENTO, aprobado.id());
            assertThat(respuesta.documentos().get(1).revisadoPor()).isEqualTo("admin@x.co");
            assertThat(respuesta.estadoInscripcion()).isEqualTo("PENDIENTE");
            assertThat(respuesta.documentosFaltantes())
                    .containsExactly("CERTIFICADO_ESTUDIO", "FORMULARIO_INSCRIPCION", "AUTORIZACION_HABEAS_DATA");
        }

        @Test
        void sinDocumentosLaListaVaVaciaPeroConElEstado() {
            when(documentos.listarPorDeportista(DEPORTISTA)).thenReturn(List.of());

            DocumentosDeportistaResponseDTO respuesta = servicio.listar(admin, DEPORTISTA);

            assertThat(respuesta.documentos()).isEmpty();
            assertThat(respuesta.estadoInscripcion()).isEqualTo("PENDIENTE");
        }

        @Test
        void unDeportistaInexistenteEsUn404() {
            when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.listar(admin, DEPORTISTA)).isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void laListaNuncaTraeLaRutaDelArchivo() {
            when(documentos.listarPorDeportista(DEPORTISTA)).thenReturn(List.of(documento()));

            assertThat(servicio.listar(admin, DEPORTISTA).toString()).doesNotContain(RUTA_SECRETA);
        }
    }

    // --- Descargar ------------------------------------------------------------------------------------------

    @Nested
    class Descargar {

        @Test
        void pideLaUrlFirmadaConLaVigenciaDeLaConfiguracion() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
            when(almacenamiento.urlFirmada("documentos", RUTA_SECRETA, Duration.ofSeconds(300)))
                    .thenReturn("https://firmada.example/x?token=t");

            DescargaDocumentoResponseDTO r = servicio.descargar(admin, DOCUMENTO);

            assertThat(r.url()).isEqualTo("https://firmada.example/x?token=t");
            assertThat(r.vigenciaSegundos()).isEqualTo(300);
        }

        @Test
        void laVigenciaSaleDeLaConfiguracionNoDeUnValorFijo() {
            DocumentoServiceImpl otro = servicio(Duration.ofSeconds(60));
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
            when(almacenamiento.urlFirmada(any(), any(), any())).thenReturn("https://firmada.example/y");

            assertThat(otro.descargar(admin, DOCUMENTO).vigenciaSegundos()).isEqualTo(60);
            verify(almacenamiento).urlFirmada("documentos", RUTA_SECRETA, Duration.ofSeconds(60));
        }

        @Test
        void elAcudienteDuenoDescarga() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
            when(almacenamiento.urlFirmada(any(), any(), any())).thenReturn("https://firmada.example/z");

            assertThat(servicio.descargar(acudiente, DOCUMENTO).url()).isEqualTo("https://firmada.example/z");
        }

        @Test
        void unDocumentoInexistenteEsUn404SinTocarStorage() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.descargar(admin, DOCUMENTO)).isInstanceOf(RecursoNoEncontradoException.class);
            verifyNoInteractions(almacenamiento);
        }

        @Test
        void siElArchivoYaNoEstaEnElBucketSePropagaLaExcepcionDeStorage() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
            ArchivoNoEncontradoException falta = new ArchivoNoEncontradoException("no está", 404, null);
            when(almacenamiento.urlFirmada(any(), any(), any())).thenThrow(falta);

            assertThatThrownBy(() -> servicio.descargar(admin, DOCUMENTO)).isSameAs(falta);
        }

        @Test
        void laUrlNoSeRegistra() {
            ListAppender<ILoggingEvent> registro = escucharRegistro();
            try {
                when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()));
                when(almacenamiento.urlFirmada(any(), any(), any())).thenReturn("https://firmada.example/secreto?token=abc");

                servicio.descargar(admin, DOCUMENTO);

                assertThat(registro.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                        .doesNotContain("secreto").doesNotContain("token").doesNotContain(RUTA_SECRETA));
            } finally {
                dejarDeEscuchar(registro);
            }
        }
    }

    // --- Aprobar y rechazar ---------------------------------------------------------------------------------

    @Nested
    class Revisar {

        private Documento pendiente;
        private Documento aprobado;
        private Documento rechazado;

        @BeforeEach
        void prepararRevision() {
            pendiente = documento();
            aprobado = revisado(EstadoDocumento.APROBADO, null);
            rechazado = revisado(EstadoDocumento.RECHAZADO, "Borroso");
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(pendiente));
            when(documentos.aprobar(DOCUMENTO, ADMIN)).thenReturn(true);
            when(documentos.rechazar(eq(DOCUMENTO), eq(ADMIN), anyString())).thenReturn(true);
        }

        private Documento revisado(EstadoDocumento estado, String comentario) {
            return new Documento(DOCUMENTO, DEPORTISTA, "PAZ_Y_SALVO", RUTA_SECRETA, "paz.pdf", "application/pdf", 10, estado,
                    comentario, null, "ACUDIENTE", "familia@correo.co", Instant.parse("2026-11-05T15:00:00Z"),
                    "admin@correo.co", Instant.parse("2026-11-05T16:00:00Z"));
        }

        private void inscripcion(String estado) {
            Deportista d = new Deportista();
            d.setId(DEPORTISTA);
            d.setEstadoInscripcion(estado);
            when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenReturn(Optional.of(d));
        }

        // --- Autorización: solo el administrador

        @Test
        void elAdministradorAprueba() {
            assertThat(servicio.aprobar(admin, DOCUMENTO).documento().estado()).isEqualTo(EstadoDocumento.PENDIENTE);
            verify(documentos).aprobar(DOCUMENTO, ADMIN);
        }

        @Test
        void elAdministradorRechaza() {
            servicio.rechazar(admin, DOCUMENTO, "Borroso");

            verify(documentos).rechazar(DOCUMENTO, ADMIN, "Borroso");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ACUDIENTE", "ENTRENADOR", "TESORERO", "", "ROL_RARO", "administrador"})
        void cualquierOtroRolEsUn403SinTocarElDao(String rol) {
            Solicitante otro = new Solicitante(UUID.randomUUID(), rol);

            assertThatThrownBy(() -> servicio.aprobar(otro, DOCUMENTO)).isInstanceOf(SinPermisoException.class);
            assertThatThrownBy(() -> servicio.rechazar(otro, DOCUMENTO, "Borroso")).isInstanceOf(SinPermisoException.class);

            sinEfectos();
        }

        @Test
        void unAcudienteDuenoDelDocumentoTampocoPuedeRevisar() {
            when(documentos.esAcudienteDe(ACUDIENTE, DEPORTISTA)).thenReturn(true);

            assertThatThrownBy(() -> servicio.aprobar(acudiente, DOCUMENTO)).isInstanceOf(SinPermisoException.class);
            assertThatThrownBy(() -> servicio.rechazar(acudiente, DOCUMENTO, "Borroso")).isInstanceOf(SinPermisoException.class);
            sinEfectos();
        }

        @Test
        void rolNuloSubNuloOSolicitanteNuloSonUn403() {
            for (Solicitante s : new Solicitante[] {new Solicitante(UUID.randomUUID(), null),
                    new Solicitante(null, Solicitante.ADMINISTRADOR), new Solicitante(null, null), null}) {
                assertThatThrownBy(() -> servicio.aprobar(s, DOCUMENTO)).isInstanceOf(SinPermisoException.class);
                assertThatThrownBy(() -> servicio.rechazar(s, DOCUMENTO, "Borroso")).isInstanceOf(SinPermisoException.class);
            }
            sinEfectos();
        }

        @Test
        void laAutorizacionVaAntesQueLaBusquedaYLaValidacion() {
            Solicitante tesorero = new Solicitante(UUID.randomUUID(), "TESORERO");
            UUID inexistente = UUID.randomUUID();

            // Con un documento inexistente y un comentario vacío, el tesorero recibe 403 y no 404 ni 400
            assertThatThrownBy(() -> servicio.rechazar(tesorero, inexistente, "  ")).isInstanceOf(SinPermisoException.class);
            verify(documentos, never()).buscarPorId(any());
        }

        private void sinEfectos() {
            verify(documentos, never()).buscarPorId(any());
            verify(documentos, never()).aprobar(any(), any());
            verify(documentos, never()).rechazar(any(), any(), any());
            verifyNoInteractions(deportistas, transaccion, almacenamiento);
        }

        // --- 404

        @Test
        void unDocumentoInexistenteEsUn404SinAbrirTransaccion() {
            UUID inexistente = UUID.randomUUID();
            when(documentos.buscarPorId(inexistente)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.aprobar(admin, inexistente)).isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("No existe un documento con ese id.");
            assertThatThrownBy(() -> servicio.rechazar(admin, inexistente, "Borroso"))
                    .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe un documento con ese id.");
            verifyNoInteractions(transaccion);
            verify(documentos, never()).aprobar(any(), any());
            verify(documentos, never()).rechazar(any(), any(), any());
        }

        @Test
        void laBusquedaDelDocumentoVaAntesQueLaValidacionDelComentario() {
            UUID inexistente = UUID.randomUUID();
            when(documentos.buscarPorId(inexistente)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.rechazar(admin, inexistente, "  "))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        // --- 409: ya no está PENDIENTE (el DAO devuelve false)

        @Test
        void aprobarOtraVezUnAprobadoEsUn409() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(aprobado));
            when(documentos.aprobar(DOCUMENTO, ADMIN)).thenReturn(false);

            assertThatThrownBy(() -> servicio.aprobar(admin, DOCUMENTO)).isInstanceOfSatisfying(ConflictoException.class, e -> {
                assertThat(e.getMessage()).contains("ya fue revisado");
                assertThat(e.getCampos()).containsExactly("estado");
            });
        }

        @Test
        void aprobarUnRechazadoEsUn409() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(rechazado));
            when(documentos.aprobar(DOCUMENTO, ADMIN)).thenReturn(false);

            assertThatThrownBy(() -> servicio.aprobar(admin, DOCUMENTO)).isInstanceOf(ConflictoException.class);
        }

        @Test
        void rechazarUnRechazadoEsUn409() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(rechazado));
            when(documentos.rechazar(eq(DOCUMENTO), eq(ADMIN), anyString())).thenReturn(false);

            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, "Otro motivo")).isInstanceOf(ConflictoException.class);
        }

        @Test
        void rechazarUnAprobadoEsUn409PorqueReemplazarUnAprobadoNoEntraEnEstaVersion() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(aprobado));
            when(documentos.rechazar(eq(DOCUMENTO), eq(ADMIN), anyString())).thenReturn(false);

            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, "Me equivoqué"))
                    .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getMessage()).contains("ya fue revisado"));
        }

        @Test
        void conUn409NoSeReleeNada() {
            when(documentos.aprobar(DOCUMENTO, ADMIN)).thenReturn(false);

            assertThatThrownBy(() -> servicio.aprobar(admin, DOCUMENTO)).isInstanceOf(ConflictoException.class);

            verify(documentos, org.mockito.Mockito.times(1)).buscarPorId(DOCUMENTO);   // solo la búsqueda previa
            verifyNoInteractions(deportistas);
        }

        // --- Comentario

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t\n "})
        void unComentarioVacioOSoloEspaciosEsUn400ConElCampoComentario(String comentario) {
            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, comentario))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e ->
                            assertThat(e.getCampos()).containsExactly("comentario"));
            comentarioRechazado();
        }

        @Test
        void unComentarioNuloEsUn400ConElCampoComentario() {
            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, null))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e ->
                            assertThat(e.getCampos()).containsExactly("comentario"));
            comentarioRechazado();
        }

        @Test
        void de301CaracteresEsUn400() {
            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, "x".repeat(301)))
                    .isInstanceOfSatisfying(DatosInvalidosException.class, e -> {
                        assertThat(e.getCampos()).containsExactly("comentario");
                        assertThat(e.getMessage()).contains("300");
                    });
            comentarioRechazado();
        }

        @Test
        void de300CaracteresPasa() {
            servicio.rechazar(admin, DOCUMENTO, "x".repeat(300));

            verify(documentos).rechazar(DOCUMENTO, ADMIN, "x".repeat(300));
        }

        @Test
        void losEspaciosDeLosExtremosNoCuentanYElComentarioSeGuardaRecortado() {
            servicio.rechazar(admin, DOCUMENTO, "  " + "x".repeat(300) + " \n");
            servicio.rechazar(admin, DOCUMENTO, "   La imagen está borrosa.  ");

            verify(documentos).rechazar(DOCUMENTO, ADMIN, "x".repeat(300));
            verify(documentos).rechazar(DOCUMENTO, ADMIN, "La imagen está borrosa.");
        }

        private void comentarioRechazado() {
            verify(documentos, never()).rechazar(any(), any(), any());
            verifyNoInteractions(transaccion);
        }

        // --- Revisor, relectura y respuesta

        @Test
        void elDaoRecibeElSubDelAdministradorComoRevisor() {
            UUID otroAdmin = UUID.randomUUID();
            Solicitante segundo = new Solicitante(otroAdmin, Solicitante.ADMINISTRADOR);
            when(documentos.aprobar(DOCUMENTO, otroAdmin)).thenReturn(true);
            when(documentos.rechazar(eq(DOCUMENTO), eq(otroAdmin), anyString())).thenReturn(true);

            servicio.aprobar(segundo, DOCUMENTO);
            servicio.rechazar(segundo, DOCUMENTO, "Borroso");

            verify(documentos).aprobar(DOCUMENTO, otroAdmin);
            verify(documentos).rechazar(DOCUMENTO, otroAdmin, "Borroso");
            verify(documentos, never()).aprobar(DOCUMENTO, ADMIN);
        }

        @Test
        void aprobarDevuelveElDocumentoReleidoYElEstadoDeLaInscripcionReleido() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(pendiente), Optional.of(aprobado));
            inscripcion("COMPLETA");

            var respuesta = servicio.aprobar(admin, DOCUMENTO);

            assertThat(respuesta.documento().estado()).as("sale de la relectura, no del documento previo")
                    .isEqualTo(EstadoDocumento.APROBADO);
            assertThat(respuesta.documento().revisadoPor()).isEqualTo("admin@correo.co");
            assertThat(respuesta.estadoInscripcion()).isEqualTo("COMPLETA");
        }

        @Test
        void rechazarDevuelveElDocumentoReleidoElEstadoYElAvisoNulo() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(pendiente), Optional.of(rechazado));
            inscripcion("PENDIENTE");

            var respuesta = servicio.rechazar(admin, DOCUMENTO, " Borroso ");

            assertThat(respuesta.documento().estado()).isEqualTo(EstadoDocumento.RECHAZADO);
            assertThat(respuesta.documento().comentario()).isEqualTo("Borroso");
            assertThat(respuesta.estadoInscripcion()).isEqualTo("PENDIENTE");
            assertThat(respuesta.avisoEnviado()).as("sin acudientes con correo no hay a quién avisar").isNull();
        }

        @Test
        void elUpdateYLaRelecturaVanDentroDeLaTransaccionYLaBusquedaPreviaFuera() {
            List<Boolean> lecturasDentro = new java.util.ArrayList<>();
            when(documentos.buscarPorId(DOCUMENTO)).thenAnswer(i -> {
                lecturasDentro.add(dentroDeLaTransaccion.get());
                return Optional.of(dentroDeLaTransaccion.get() ? aprobado : pendiente);
            });
            when(documentos.aprobar(DOCUMENTO, ADMIN)).thenAnswer(i -> {
                assertThat(dentroDeLaTransaccion).as("el update va dentro de la transacción").isTrue();
                return true;
            });
            when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenAnswer(i -> {
                assertThat(dentroDeLaTransaccion).as("la relectura va dentro de la transacción").isTrue();
                Deportista d = new Deportista();
                d.setEstadoInscripcion("PENDIENTE");
                return Optional.of(d);
            });

            servicio.aprobar(admin, DOCUMENTO);

            assertThat(lecturasDentro).as("la primera lectura va fuera de la transacción y la relectura dentro")
                    .containsExactly(false, true);
            InOrder orden = inOrder(documentos, transaccion, deportistas);
            orden.verify(documentos).buscarPorId(DOCUMENTO);
            orden.verify(transaccion).execute(any());
            orden.verify(documentos).aprobar(DOCUMENTO, ADMIN);
            orden.verify(documentos).buscarPorId(DOCUMENTO);
            orden.verify(deportistas).buscarPorId(eq(DEPORTISTA), anyInt());
        }

        @Test
        void siFallaLaTransaccionSePropagaLaExcepcionOriginal() {
            RuntimeException falla = new DataIntegrityViolationException("falla de la base");
            when(documentos.aprobar(DOCUMENTO, ADMIN)).thenThrow(falla);

            assertThatThrownBy(() -> servicio.aprobar(admin, DOCUMENTO)).isSameAs(falla);
        }

        @Test
        void nuncaRegistraElComentarioNiNadaDelDocumento() {
            ListAppender<ILoggingEvent> registro = escucharRegistro();
            try {
                inscripcion("PENDIENTE");
                servicio.aprobar(admin, DOCUMENTO);
                servicio.rechazar(admin, DOCUMENTO, "comentario-privado");
                when(documentos.aprobar(DOCUMENTO, ADMIN)).thenReturn(false);
                assertThatThrownBy(() -> servicio.aprobar(admin, DOCUMENTO)).isInstanceOf(ConflictoException.class);

                assertThat(registro.list).isEmpty();
            } finally {
                dejarDeEscuchar(registro);
            }
        }
    }

    // --- Aviso DOCUMENTO_RECHAZADO --------------------------------------------------------------------------

    @Nested
    class Aviso {

        private static final String ENLACE = "https://futdata.test/acudiente/documentos";
        private final Destinatario ana = new Destinatario("ana@correo.co", "Ana Mora");
        private final Destinatario pedro = new Destinatario("pedro@correo.co", "Pedro Alvarez");

        @BeforeEach
        void prepararAviso() {
            Documento rechazado = new Documento(DOCUMENTO, DEPORTISTA, "PAZ_Y_SALVO", RUTA_SECRETA, "paz.pdf",
                    "application/pdf", 10, EstadoDocumento.RECHAZADO, "Borroso", null, "ACUDIENTE", "familia@correo.co",
                    Instant.parse("2026-11-05T15:00:00Z"), "admin@correo.co", Instant.parse("2026-11-05T16:00:00Z"));
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()), Optional.of(rechazado));
            when(documentos.rechazar(eq(DOCUMENTO), eq(ADMIN), anyString())).thenReturn(true);
            conDeportista("Luis", "Gómez");
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of(ana, pedro));
        }

        private void conDeportista(String nombre, String apellido) {
            Deportista d = new Deportista();
            d.setId(DEPORTISTA);
            d.setNombre(nombre);
            d.setApellido(apellido);
            d.setEstadoInscripcion("PENDIENTE");
            when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenReturn(Optional.of(d));
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> avisosEnviados() {
            ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
            verify(notificador, org.mockito.Mockito.atLeast(0)).notificar(eq("DOCUMENTO_RECHAZADO"), datos.capture());
            return datos.getAllValues();
        }

        // --- Contenido

        @Test
        void sigueElContratoUnNotificarPorAcudienteConLasClavesYValoresExactos() {
            var respuesta = servicio.rechazar(admin, DOCUMENTO, "  Borroso  ");

            assertThat(respuesta.avisoEnviado()).isTrue();
            List<Map<String, Object>> avisos = avisosEnviados();
            assertThat(avisos).hasSize(2);
            assertThat(avisos.get(0)).containsOnlyKeys("correo", "nombreAcudiente", "nombreDeportista", "documento",
                    "comentario", "enlaceDocumentos");
            assertThat(avisos.get(0)).containsEntry("correo", "ana@correo.co").containsEntry("nombreAcudiente", "Ana Mora")
                    .containsEntry("nombreDeportista", "Luis Gómez").containsEntry("documento", "Paz y salvo")
                    .containsEntry("comentario", "Borroso").containsEntry("enlaceDocumentos", ENLACE);
            assertThat(avisos.get(1)).containsEntry("correo", "pedro@correo.co").containsEntry("nombreAcudiente", "Pedro Alvarez")
                    .containsEntry("nombreDeportista", "Luis Gómez").containsEntry("comentario", "Borroso");
            verify(notificador, org.mockito.Mockito.times(2)).notificar(eq(INotificador.DOCUMENTO_RECHAZADO), any());
        }

        @Test
        void elDocumentoVaConSuEtiquetaLegibleYElComentarioYaRecortado() {
            when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()), Optional.of(documento()));

            servicio.rechazar(admin, DOCUMENTO, "   motivo con espacios   ");

            assertThat(avisosEnviados()).allSatisfy(a -> assertThat(a).containsEntry("documento", "Paz y salvo")
                    .containsEntry("comentario", "motivo con espacios"));
        }

        @Test
        void losDatosNoTraenNiLaRutaNiElNombreOriginalNiElCorreoDeQuienLoSubio() {
            servicio.rechazar(admin, DOCUMENTO, "Borroso");

            assertThat(avisosEnviados().toString()).doesNotContain(RUTA_SECRETA).doesNotContain("paz.pdf")
                    .doesNotContain("familia@correo.co").doesNotContain("admin@correo.co").doesNotContain("secreta");
        }

        // --- Destinatarios

        @Test
        void filtraLosCorreosNulosVaciosMuyLargosOMalFormados() {
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of(
                    new Destinatario(null, "Sin correo"),
                    new Destinatario("", "Vacío"),
                    new Destinatario("   ", "Solo espacios"),
                    new Destinatario("sin-arroba", "Mal formado"),
                    new Destinatario("a@b", "Sin punto después de la arroba"),
                    new Destinatario("con espacio@correo.co", "Con espacio"),
                    new Destinatario("x".repeat(250) + "@a.co", "Muy largo"),
                    ana));

            var respuesta = servicio.rechazar(admin, DOCUMENTO, "Borroso");

            assertThat(respuesta.avisoEnviado()).isTrue();
            assertThat(avisosEnviados()).extracting(a -> a.get("correo")).containsExactly("ana@correo.co");
        }

        @Test
        void unCorreoDeExactamente254CaracteresSePermite() {
            String largo = "x".repeat(254 - "@a.co".length()) + "@a.co";
            assertThat(largo).hasSize(254);
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of(new Destinatario(largo, "Largo")));

            assertThat(servicio.rechazar(admin, DOCUMENTO, "Borroso").avisoEnviado()).isTrue();
            assertThat(avisosEnviados()).extracting(a -> a.get("correo")).containsExactly(largo);
        }

        @Test
        void sinDestinatariosValidosEsNullYNoSeLlamaANotificar() {
            for (List<Destinatario> lista : List.of(List.<Destinatario>of(),
                    List.of(new Destinatario(null, "Sin correo"), new Destinatario("mal", "Mal")))) {
                when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(lista);
                when(documentos.buscarPorId(DOCUMENTO)).thenReturn(Optional.of(documento()), Optional.of(documento()));

                assertThat(servicio.rechazar(admin, DOCUMENTO, "Borroso").avisoEnviado()).isNull();
            }
            verifyNoInteractions(notificador);
        }

        // --- Fallas: nunca deshacen el rechazo

        @Test
        void siNotificarLanzaUnaExcepcionElAvisoEsFalseYElRechazoSigueEnLaRespuesta() {
            doThrow(new IllegalStateException("n8n caído")).when(notificador).notificar(any(), any());

            var respuesta = servicio.rechazar(admin, DOCUMENTO, "Borroso");

            assertThat(respuesta.avisoEnviado()).isFalse();
            assertThat(respuesta.documento().estado()).isEqualTo(EstadoDocumento.RECHAZADO);
            assertThat(respuesta.documento().comentario()).isEqualTo("Borroso");
            verify(documentos).rechazar(DOCUMENTO, ADMIN, "Borroso");
        }

        @Test
        void siLaLecturaDeDestinatariosLanzaElAvisoEsFalse() {
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenThrow(new DataIntegrityViolationException("falla"));

            var respuesta = servicio.rechazar(admin, DOCUMENTO, "Borroso");

            assertThat(respuesta.avisoEnviado()).isFalse();
            assertThat(respuesta.documento().estado()).isEqualTo(EstadoDocumento.RECHAZADO);
            verifyNoInteractions(notificador);
        }

        @Test
        void siFrontendUrlNoEstaConfiguradaElAvisoEsFalseYNoSeLlamaANotificar() {
            DocumentoServiceImpl sinUrl = servicio(Duration.ofSeconds(300), new FrontendProperties(null));

            var respuesta = sinUrl.rechazar(admin, DOCUMENTO, "Borroso");

            assertThat(respuesta.avisoEnviado()).isFalse();
            assertThat(respuesta.documento().estado()).isEqualTo(EstadoDocumento.RECHAZADO);
            verifyNoInteractions(notificador);
        }

        @Test
        void sinAcudientesNoImportaQueFalteFrontendUrlPorqueNoHayNadaQueEnviar() {
            DocumentoServiceImpl sinUrl = servicio(Duration.ofSeconds(300), new FrontendProperties(null));
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of());

            assertThat(sinUrl.rechazar(admin, DOCUMENTO, "Borroso").avisoEnviado()).isNull();
        }

        @Test
        void unEnlaceDeMasDe2000CaracteresEsUnErrorDeConfiguracionNoSeCorta() {
            DocumentoServiceImpl enlaceLargo = servicio(Duration.ofSeconds(300),
                    new FrontendProperties("https://futdata.test/" + "a".repeat(2000)));

            assertThat(enlaceLargo.rechazar(admin, DOCUMENTO, "Borroso").avisoEnviado()).isFalse();
            verifyNoInteractions(notificador);
        }

        @Test
        void siFallaElPrimerPostNoSeIntentanLosDemas() {
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of(ana, pedro,
                    new Destinatario("tercero@correo.co", "Tercero Uno")));
            doThrow(new IllegalStateException("n8n caído")).when(notificador).notificar(any(), any());

            assertThat(servicio.rechazar(admin, DOCUMENTO, "Borroso").avisoEnviado()).isFalse();

            verify(notificador, org.mockito.Mockito.times(1)).notificar(any(), any());
        }

        @Test
        void siFallaElSegundoNoSigueConElTerceroYDevuelveFalse() {
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of(ana, pedro,
                    new Destinatario("tercero@correo.co", "Tercero Uno")));
            doThrow(new IllegalStateException("n8n caído")).when(notificador)
                    .notificar(eq(INotificador.DOCUMENTO_RECHAZADO), org.mockito.ArgumentMatchers.argThat(m -> "pedro@correo.co".equals(m.get("correo"))));

            assertThat(servicio.rechazar(admin, DOCUMENTO, "Borroso").avisoEnviado()).isFalse();

            verify(notificador, org.mockito.Mockito.times(2)).notificar(any(), any());
        }

        // --- Después del commit

        @Test
        void elAvisoSeEnviaDespuesDeLaTransaccionNuncaDentro() {
            doAnswer(i -> {
                assertThat(dentroDeLaTransaccion).as("notificar no puede ir dentro de la transacción").isFalse();
                return null;
            }).when(notificador).notificar(any(), any());
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenAnswer(i -> {
                assertThat(dentroDeLaTransaccion).as("la lectura de destinatarios va después del commit").isFalse();
                return List.of(ana, pedro);
            });

            assertThat(servicio.rechazar(admin, DOCUMENTO, "Borroso").avisoEnviado()).isTrue();

            InOrder orden = inOrder(documentos, transaccion, destinatarios, notificador);
            orden.verify(transaccion).execute(any());
            orden.verify(documentos).rechazar(DOCUMENTO, ADMIN, "Borroso");
            orden.verify(destinatarios).acudientesDeDeportista(DEPORTISTA);
            orden.verify(notificador, org.mockito.Mockito.times(2)).notificar(any(), any());
        }

        // --- Máximos y nombres

        @Test
        void losNombresLargosSeRecortanA120YElComentarioDe300PasaCompleto() {
            conDeportista("N".repeat(100), "A".repeat(100));
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of(new Destinatario("ana@correo.co", "M".repeat(300))));

            servicio.rechazar(admin, DOCUMENTO, "c".repeat(300));

            Map<String, Object> aviso = avisosEnviados().get(0);
            assertThat((String) aviso.get("nombreAcudiente")).hasSize(120);
            assertThat((String) aviso.get("nombreDeportista")).hasSize(120);
            assertThat((String) aviso.get("documento")).hasSizeLessThanOrEqualTo(120);
            assertThat((String) aviso.get("comentario")).hasSize(300);
            assertThat((String) aviso.get("enlaceDocumentos")).hasSizeLessThanOrEqualTo(2000);
            assertThat((String) aviso.get("correo")).hasSizeLessThanOrEqualTo(254);
        }

        @Test
        void losNombresSonDeUnaSolaLineaSinSaltosNiEspaciosRepetidos() {
            conDeportista("Luis\nMiguel", "  Gómez\t\tRojas ");
            when(destinatarios.acudientesDeDeportista(DEPORTISTA))
                    .thenReturn(List.of(new Destinatario("ana@correo.co", "Ana\r\n  Mora\u0000")));

            servicio.rechazar(admin, DOCUMENTO, "Borroso");

            Map<String, Object> aviso = avisosEnviados().get(0);
            assertThat(aviso).containsEntry("nombreAcudiente", "Ana Mora").containsEntry("nombreDeportista", "Luis Miguel Gómez Rojas");
        }

        @Test
        void unNombreVacioUsaUnTextoNeutroEnVezDeMandarUnCampoVacio() {
            conDeportista(null, "   ");
            when(destinatarios.acudientesDeDeportista(DEPORTISTA)).thenReturn(List.of(
                    new Destinatario("ana@correo.co", null), new Destinatario("pedro@correo.co", "  \n ")));

            servicio.rechazar(admin, DOCUMENTO, "Borroso");

            List<Map<String, Object>> avisos = avisosEnviados();
            assertThat(avisos).hasSize(2);
            assertThat(avisos).allSatisfy(a -> assertThat(a).containsEntry("nombreAcudiente", "Acudiente")
                    .containsEntry("nombreDeportista", "tu deportista"));
            assertThat(avisos).flatExtracting(Map::values).doesNotContain("");
        }

        // --- Cuándo NO se avisa

        @Test
        void aprobarNoEnviaNingunAviso() {
            when(documentos.aprobar(DOCUMENTO, ADMIN)).thenReturn(true);

            servicio.aprobar(admin, DOCUMENTO);

            verifyNoInteractions(notificador, destinatarios);
        }

        @Test
        void conUn400UnComentarioInvalidoNoSeAvisaNiSeLeenDestinatarios() {
            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, "  ")).isInstanceOf(DatosInvalidosException.class);
            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, "x".repeat(301))).isInstanceOf(DatosInvalidosException.class);

            verifyNoInteractions(notificador, destinatarios);
        }

        @Test
        void conUn403NoSeAvisa() {
            for (Solicitante s : new Solicitante[] {acudiente, new Solicitante(UUID.randomUUID(), "TESORERO"),
                    new Solicitante(UUID.randomUUID(), Solicitante.ENTRENADOR), new Solicitante(null, Solicitante.ADMINISTRADOR), null}) {
                assertThatThrownBy(() -> servicio.rechazar(s, DOCUMENTO, "Borroso")).isInstanceOf(SinPermisoException.class);
            }
            verifyNoInteractions(notificador, destinatarios);
        }

        @Test
        void conUn404NoSeAvisa() {
            UUID inexistente = UUID.randomUUID();
            when(documentos.buscarPorId(inexistente)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.rechazar(admin, inexistente, "Borroso")).isInstanceOf(RecursoNoEncontradoException.class);
            verifyNoInteractions(notificador, destinatarios);
        }

        @Test
        void conUn409NoSeAvisa() {
            when(documentos.rechazar(eq(DOCUMENTO), eq(ADMIN), anyString())).thenReturn(false);

            assertThatThrownBy(() -> servicio.rechazar(admin, DOCUMENTO, "Borroso")).isInstanceOf(ConflictoException.class);
            verifyNoInteractions(notificador, destinatarios);
        }

        // --- Registros

        @Test
        void losRegistrosNoTraenCorreosNombresComentarioNiEnlace() {
            ListAppender<ILoggingEvent> registro = escucharRegistro();
            try {
                doThrow(new IllegalStateException("falló para ana@correo.co, Ana Mora, Luis Gómez, comentario-privado, https://futdata.test"))
                        .when(notificador).notificar(any(), any());

                servicio.rechazar(admin, DOCUMENTO, "comentario-privado");

                assertThat(registro.list).hasSize(1);
                String mensaje = registro.list.get(0).getFormattedMessage();
                assertThat(mensaje).contains("DOCUMENTO_RECHAZADO").contains("IllegalStateException")
                        .doesNotContain("ana@correo.co").doesNotContain("Ana").doesNotContain("Mora").doesNotContain("Luis")
                        .doesNotContain("Gómez").doesNotContain("comentario-privado").doesNotContain("futdata.test")
                        .doesNotContain("Paz y salvo").doesNotContain(RUTA_SECRETA);
                assertThat(registro.list.get(0).getThrowableProxy()).as("sin traza, que traería el mensaje de la excepción").isNull();
            } finally {
                dejarDeEscuchar(registro);
            }
        }

        @Test
        void siTodoSaleBienNoSeRegistraNada() {
            ListAppender<ILoggingEvent> registro = escucharRegistro();
            try {
                servicio.rechazar(admin, DOCUMENTO, "comentario-privado");

                assertThat(registro.list).isEmpty();
            } finally {
                dejarDeEscuchar(registro);
            }
        }

        @Test
        void siFallaLaLecturaDeDestinatariosElRegistroNoTraeElMensajeDeLaExcepcion() {
            ListAppender<ILoggingEvent> registro = escucharRegistro();
            try {
                when(destinatarios.acudientesDeDeportista(DEPORTISTA))
                        .thenThrow(new DataIntegrityViolationException("duplicate key ana@correo.co"));

                servicio.rechazar(admin, DOCUMENTO, "Borroso");

                assertThat(registro.list).hasSize(1);
                assertThat(registro.list.get(0).getFormattedMessage()).contains("DataIntegrityViolationException")
                        .doesNotContain("ana@correo.co").doesNotContain("duplicate");
            } finally {
                dejarDeEscuchar(registro);
            }
        }
    }

    // --- Registro -------------------------------------------------------------------------------------------

    private static Logger registroDelServicio() {
        return (Logger) LoggerFactory.getLogger(DocumentoServiceImpl.class);
    }

    private static ListAppender<ILoggingEvent> escucharRegistro() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        registroDelServicio().addAppender(appender);
        return appender;
    }

    private static void dejarDeEscuchar(ListAppender<ILoggingEvent> appender) {
        registroDelServicio().detachAppender(appender);
        appender.stop();
    }
}
