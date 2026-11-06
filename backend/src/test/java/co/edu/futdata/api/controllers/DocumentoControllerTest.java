package co.edu.futdata.api.controllers;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.DescargaDocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentosDeportistaResponseDTO;
import co.edu.futdata.api.dto.RechazoDocumentoResponseDTO;
import co.edu.futdata.api.dto.RevisionDocumentoResponseDTO;
import co.edu.futdata.datasource.external.AlmacenamientoNoDisponibleException;
import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.EstadoDocumento;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IDocumentoService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.Solicitante;
import co.edu.futdata.util.security.JwtValidator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Los documentos por HTTP con la cadena de seguridad completa (JWT, sesión 2FA y roles) y el servicio simulado: roles,
 * forma del JSON, errores y que quien pide salga siempre del token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class DocumentoControllerTest {

    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID DEPORTISTA = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DOCUMENTO = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final byte[] PDF = "%PDF-1.7\ncontenido".getBytes(StandardCharsets.US_ASCII);
    private static final String SUBIR = "/api/deportistas/" + DEPORTISTA + "/documentos";
    private static final String LISTAR = "/api/deportistas/" + DEPORTISTA + "/documentos";
    private static final String DESCARGA = "/api/documentos/" + DOCUMENTO + "/descarga";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;
    @MockitoBean
    private IDocumentoService servicio;

    @BeforeEach
    void preparar() {
        for (var par : List.of(List.of(ACUDIENTE, "ACUDIENTE"), List.of(ADMIN, "ADMINISTRADOR"),
                List.of(TESORERO, "TESORERO"), List.of(ENTRENADOR, "ENTRENADOR"))) {
            UUID id = (UUID) par.get(0);
            when(decoder.decode("token-" + id)).thenReturn(jwt("token-" + id, id, UUID.randomUUID().toString()));
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) par.get(1)));
        }
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);

        when(servicio.subir(any(), eq(DEPORTISTA), any(), any(), any(), any())).thenReturn(documento());
        when(servicio.listar(any(), eq(DEPORTISTA))).thenReturn(new DocumentosDeportistaResponseDTO(
                List.of(DocumentoResponseDTO.de(documento())), "PENDIENTE", List.of("CERTIFICADO_ESTUDIO")));
        when(servicio.descargar(any(), eq(DOCUMENTO)))
                .thenReturn(new DescargaDocumentoResponseDTO("https://firmada.test/doc?token=t", 300));
    }

    private static Jwt jwt(String valor, UUID sub, String sesion) {
        Jwt.Builder b = Jwt.withTokenValue(valor).header("alg", "ES256").subject(sub.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
        if (sesion != null) {
            b.claim(JwtValidator.CLAIM_SESION, sesion);
        }
        return b.build();
    }

    private ResultActions como(UUID usuario, MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    private static MockHttpServletRequestBuilder subida() {
        return subida("tipo", "PAZ_Y_SALVO");
    }

    private static MockHttpServletRequestBuilder subida(String campo, String valor) {
        return multipart(SUBIR).file(new MockMultipartFile("archivo", "paz y salvo.pdf", "application/pdf", PDF))
                .param(campo, valor);
    }

    private static Documento documento() {
        return new Documento(DOCUMENTO, DEPORTISTA, "PAZ_Y_SALVO", "ruta/que/no/debe/salir.pdf", "paz y salvo.pdf",
                "application/pdf", PDF.length, EstadoDocumento.PENDIENTE, null, LocalDate.of(2027, 1, 31), "ACUDIENTE",
                "familia@correo.co", Instant.parse("2026-11-05T15:00:00Z"), null, null);
    }

    // --- Seguridad ------------------------------------------------------------------------------------------

    @Test
    void sinTokenEs401EnLasTresRutas() throws Exception {
        mvc.perform(subida()).andExpect(status().isUnauthorized());
        mvc.perform(get(LISTAR)).andExpect(status().isUnauthorized());
        mvc.perform(get(DESCARGA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void unTokenSinSessionIdEs401() throws Exception {
        when(decoder.decode("token-sin-sesion")).thenReturn(jwt("token-sin-sesion", ACUDIENTE, null));

        mvc.perform(get(LISTAR).header(HttpHeaders.AUTHORIZATION, "Bearer token-sin-sesion"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(DESCARGA).header(HttpHeaders.AUTHORIZATION, "Bearer token-sin-sesion"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    /** Igual que en el 2C: la sesión sin 2FA completo no es 401 sino 403 con código 2FA_REQUERIDA (SesionVerificadaFilter). */
    @Test
    void conLaSesionSin2faVerificadoEs403Con2faRequerida() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ACUDIENTE), any())).thenReturn(false);

        como(ACUDIENTE, subida()).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        como(ACUDIENTE, get(LISTAR)).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        como(ACUDIENTE, get(DESCARGA)).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"entrenador", "tesorero"})
    void elEntrenadorYElTesoreroRecibenElError403PorRol(String rol) throws Exception {
        UUID usuario = rol.equals("entrenador") ? ENTRENADOR : TESORERO;

        como(usuario, subida()).andExpect(status().isForbidden());
        como(usuario, get(LISTAR)).andExpect(status().isForbidden());
        como(usuario, get(DESCARGA)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    // --- Subir ----------------------------------------------------------------------------------------------

    @Test
    void subirDevuelve201ConLaFormaDelDocumentoYSinLaRuta() throws Exception {
        como(ACUDIENTE, subida().param("fechaVencimiento", "2027-01-31"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DOCUMENTO.toString()))
                .andExpect(jsonPath("$.idDeportista").value(DEPORTISTA.toString()))
                .andExpect(jsonPath("$.tipo").value("PAZ_Y_SALVO"))
                .andExpect(jsonPath("$.nombreOriginal").value("paz y salvo.pdf"))
                .andExpect(jsonPath("$.tipoContenido").value("application/pdf"))
                .andExpect(jsonPath("$.tamanoBytes").value(PDF.length))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.comentario").doesNotExist())
                .andExpect(jsonPath("$.fechaVencimiento").value("2027-01-31"))
                .andExpect(jsonPath("$.cargadoPor").value("Acudiente"))
                .andExpect(jsonPath("$.cargadoPorCorreo").value("familia@correo.co"))
                .andExpect(jsonPath("$.cargadoEn").exists())
                .andExpect(jsonPath("$.revisadoPor").doesNotExist())
                .andExpect(jsonPath("$.revisadoEn").doesNotExist())
                .andExpect(jsonPath("$.ruta").doesNotExist());
    }

    @Test
    void elServicioRecibeAQuienPideSacadoDelTokenYLosDatosDelMultipart() throws Exception {
        como(ACUDIENTE, subida().param("fechaVencimiento", "2027-01-31")
                .param("idUsuario", ADMIN.toString()).param("rol", "ADMINISTRADOR")).andExpect(status().isCreated());

        verify(servicio).subir(eq(new Solicitante(ACUDIENTE, "ACUDIENTE")), eq(DEPORTISTA), eq("PAZ_Y_SALVO"),
                eq("paz y salvo.pdf"), aryEq(PDF), eq("2027-01-31"));
    }

    @Test
    void elAdministradorTambienSube() throws Exception {
        como(ADMIN, subida()).andExpect(status().isCreated());

        verify(servicio).subir(eq(new Solicitante(ADMIN, "ADMINISTRADOR")), eq(DEPORTISTA), eq("PAZ_Y_SALVO"), any(),
                aryEq(PDF), eq(null));
    }

    @Test
    void sinTipoElServicioDecideElMensajeYResponde400ConLosCampos() throws Exception {
        when(servicio.subir(any(), eq(DEPORTISTA), eq(null), any(), any(), any()))
                .thenThrow(new DatosInvalidosException(Map.of("tipo", "es obligatorio")));

        como(ADMIN, multipart(SUBIR).file(new MockMultipartFile("archivo", "a.pdf", "application/pdf", PDF)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("tipo"));
    }

    @Test
    void sinArchivoEs400() throws Exception {
        como(ADMIN, multipart(SUBIR).param("tipo", "PAZ_Y_SALVO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("archivo"));
        verify(servicio, never()).subir(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unArchivoOUnTipoInvalidoSon400ConTodosLosCampos() throws Exception {
        when(servicio.subir(any(), eq(DEPORTISTA), eq("OTRO"), any(), any(), any())).thenThrow(
                new DatosInvalidosException(Map.of("tipo", "debe ser uno de los 7", "archivo", "está vacío")));

        como(ADMIN, subida("tipo", "OTRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[0]").value("archivo"))
                .andExpect(jsonPath("$.campos[1]").value("tipo"));
    }

    @Test
    void unDeportistaAjenoOInexistenteEs404() throws Exception {
        when(servicio.subir(any(), eq(DEPORTISTA), any(), any(), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("No existe un deportista con ese id."));

        como(ACUDIENTE, subida()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un deportista con ese id."));
    }

    @Test
    void unTipoYaActivoEs409ConElCampo() throws Exception {
        when(servicio.subir(any(), eq(DEPORTISTA), any(), any(), any(), any())).thenThrow(
                new ConflictoException("tipo: ya hay un documento PENDIENTE o APROBADO de ese tipo", List.of("tipo")));

        como(ACUDIENTE, subida()).andExpect(status().isConflict()).andExpect(jsonPath("$.campos[0]").value("tipo"));
    }

    @Test
    void siStorageNoRespondeEs503() throws Exception {
        when(servicio.subir(any(), eq(DEPORTISTA), any(), any(), any(), any()))
                .thenThrow(new AlmacenamientoNoDisponibleException("sin red", null, null));

        como(ADMIN, subida()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void unIdQueNoEsUuidEs400() throws Exception {
        como(ADMIN, multipart("/api/deportistas/no-es-uuid/documentos")
                .file(new MockMultipartFile("archivo", "a.pdf", "application/pdf", PDF)).param("tipo", "PAZ_Y_SALVO"))
                .andExpect(status().isBadRequest());
        como(ADMIN, get("/api/deportistas/no-es-uuid/documentos")).andExpect(status().isBadRequest());
        como(ADMIN, get("/api/documentos/no-es-uuid/descarga")).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    // --- Listar ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"acudiente", "admin"})
    void listarDevuelveLosDocumentosElEstadoYLosFaltantes(String quien) throws Exception {
        como(quien.equals("admin") ? ADMIN : ACUDIENTE, get(LISTAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos.length()").value(1))
                .andExpect(jsonPath("$.documentos[0].id").value(DOCUMENTO.toString()))
                .andExpect(jsonPath("$.documentos[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.documentos[0].ruta").doesNotExist())
                .andExpect(jsonPath("$.estadoInscripcion").value("PENDIENTE"))
                .andExpect(jsonPath("$.documentosFaltantes[0]").value("CERTIFICADO_ESTUDIO"));
    }

    @Test
    void listarEnUnDeportistaAjenoEs404() throws Exception {
        when(servicio.listar(any(), eq(DEPORTISTA)))
                .thenThrow(new RecursoNoEncontradoException("No existe un deportista con ese id."));

        como(ACUDIENTE, get(LISTAR)).andExpect(status().isNotFound());
    }

    // --- Descargar ------------------------------------------------------------------------------------------

    @Test
    void descargarDevuelveLaUrlYLaVigenciaSinGuardarlaEnCache() throws Exception {
        como(ACUDIENTE, get(DESCARGA))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.url").value("https://firmada.test/doc?token=t"))
                .andExpect(jsonPath("$.vigenciaSegundos").value(300));

        verify(servicio).descargar(new Solicitante(ACUDIENTE, "ACUDIENTE"), DOCUMENTO);
    }

    // --- Aprobar y rechazar ---------------------------------------------------------------------------------

    private static final String APROBAR = "/api/documentos/" + DOCUMENTO + "/aprobar";
    private static final String RECHAZAR = "/api/documentos/" + DOCUMENTO + "/rechazar";

    private static Documento revisado(EstadoDocumento estado, String comentario) {
        return new Documento(DOCUMENTO, DEPORTISTA, "PAZ_Y_SALVO", "ruta/que/no/debe/salir.pdf", "paz y salvo.pdf",
                "application/pdf", PDF.length, estado, comentario, null, "ACUDIENTE", "familia@correo.co",
                Instant.parse("2026-11-05T15:00:00Z"), "admin@correo.co", Instant.parse("2026-11-05T16:00:00Z"));
    }

    private void respuestasDeRevision() {
        when(servicio.aprobar(any(), eq(DOCUMENTO))).thenReturn(new RevisionDocumentoResponseDTO(
                DocumentoResponseDTO.de(revisado(EstadoDocumento.APROBADO, null)), "COMPLETA"));
        when(servicio.rechazar(any(), eq(DOCUMENTO), any())).thenReturn(new RechazoDocumentoResponseDTO(
                DocumentoResponseDTO.de(revisado(EstadoDocumento.RECHAZADO, "Está borroso.")), "PENDIENTE", null));
    }

    private static MockHttpServletRequestBuilder rechazo(String json) {
        return post(RECHAZAR).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void aprobarDevuelve200ConElDocumentoYElEstadoDeLaInscripcion() throws Exception {
        respuestasDeRevision();

        como(ADMIN, post(APROBAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.id").value(DOCUMENTO.toString()))
                .andExpect(jsonPath("$.documento.estado").value("APROBADO"))
                .andExpect(jsonPath("$.documento.revisadoPor").value("admin@correo.co"))
                .andExpect(jsonPath("$.documento.revisadoEn").exists())
                .andExpect(jsonPath("$.documento.ruta").doesNotExist())
                .andExpect(jsonPath("$.estadoInscripcion").value("COMPLETA"))
                .andExpect(jsonPath("$.avisoEnviado").doesNotExist());
        verify(servicio).aprobar(new Solicitante(ADMIN, "ADMINISTRADOR"), DOCUMENTO);
    }

    @Test
    void rechazarDevuelve200ConElDocumentoElEstadoYElAvisoNulo() throws Exception {
        respuestasDeRevision();

        como(ADMIN, rechazo("{\"comentario\":\"Está borroso.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.documento.comentario").value("Está borroso."))
                .andExpect(jsonPath("$.estadoInscripcion").value("PENDIENTE"))
                .andExpect(jsonPath("$.avisoEnviado").value(org.hamcrest.Matchers.nullValue()));
        verify(servicio).rechazar(new Solicitante(ADMIN, "ADMINISTRADOR"), DOCUMENTO, "Está borroso.");
    }

    /** La misma convención del resto de la API (ver HorarioSemanaControllerTest): la clave va siempre, con null si no aplica. */
    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "null"})
    void avisoEnviadoSerializaTrueFalseYNullComoLaClaveSiempreEnElJson(String valor) throws Exception {
        Boolean aviso = valor.equals("null") ? null : Boolean.valueOf(valor);
        when(servicio.rechazar(any(), eq(DOCUMENTO), any())).thenReturn(new RechazoDocumentoResponseDTO(
                DocumentoResponseDTO.de(revisado(EstadoDocumento.RECHAZADO, "Borroso")), "PENDIENTE", aviso));

        como(ADMIN, rechazo("{\"comentario\":\"Borroso\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisoEnviado").hasJsonPath())
                .andExpect(jsonPath("$.avisoEnviado").value(aviso == null ? org.hamcrest.Matchers.nullValue()
                        : org.hamcrest.Matchers.is(aviso)));
    }

    @Test
    void elRechazoConAvisoFallidoSigueSiendo200ConElDocumentoRechazado() throws Exception {
        when(servicio.rechazar(any(), eq(DOCUMENTO), any())).thenReturn(new RechazoDocumentoResponseDTO(
                DocumentoResponseDTO.de(revisado(EstadoDocumento.RECHAZADO, "Borroso")), "PENDIENTE", false));

        como(ADMIN, rechazo("{\"comentario\":\"Borroso\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.avisoEnviado").value(false));
    }

    @Test
    void elRevisorSaleDelTokenNoDelCuerpo() throws Exception {
        respuestasDeRevision();

        como(ADMIN, rechazo("{\"comentario\":\"Borroso\",\"idUsuario\":\"" + ACUDIENTE + "\",\"revisadoPor\":\"x@x.co\"}"))
                .andExpect(status().isOk());
        verify(servicio).rechazar(eq(new Solicitante(ADMIN, "ADMINISTRADOR")), eq(DOCUMENTO), eq("Borroso"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"comentario\":\"\"}", "{\"comentario\":\"   \"}", "{\"comentario\":null}"})
    void rechazarSinComentarioEs400ConElCampoComentario(String cuerpo) throws Exception {
        when(servicio.rechazar(any(), eq(DOCUMENTO), org.mockito.ArgumentMatchers.argThat(c -> c == null || c.isBlank())))
                .thenThrow(new DatosInvalidosException(Map.of("comentario", "es obligatorio al rechazar un documento")));

        como(ADMIN, rechazo(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("comentario"));
    }

    @Test
    void rechazarSinCuerpoEs400ConElCampoComentarioYNoUn500() throws Exception {
        when(servicio.rechazar(any(), eq(DOCUMENTO), eq(null)))
                .thenThrow(new DatosInvalidosException(Map.of("comentario", "es obligatorio al rechazar un documento")));

        como(ADMIN, post(RECHAZAR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("comentario"));
        verify(servicio).rechazar(new Solicitante(ADMIN, "ADMINISTRADOR"), DOCUMENTO, null);
    }

    @Test
    void rechazarConUnComentarioDeMasDe300CaracteresEs400() throws Exception {
        when(servicio.rechazar(any(), eq(DOCUMENTO), eq("x".repeat(301))))
                .thenThrow(new DatosInvalidosException(Map.of("comentario", "no puede pasar de 300 caracteres")));

        como(ADMIN, rechazo("{\"comentario\":\"" + "x".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("comentario"));
    }

    @Test
    void unJsonMalFormadoEs400SinLlegarAlServicio() throws Exception {
        como(ADMIN, rechazo("{\"comentario\": ")).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void unComentarioQueNoEsTextoEs400ConElCampoComentario() throws Exception {
        como(ADMIN, rechazo("{\"comentario\":{\"a\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("comentario"));
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"acudiente", "entrenador", "tesorero"})
    void aprobarYRechazarSonSolodelAdministradorElRestoRecibe403(String rol) throws Exception {
        UUID usuario = switch (rol) {
            case "acudiente" -> ACUDIENTE;
            case "entrenador" -> ENTRENADOR;
            default -> TESORERO;
        };

        como(usuario, post(APROBAR)).andExpect(status().isForbidden());
        como(usuario, rechazo("{\"comentario\":\"Borroso\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void aprobarYRechazarSinTokenSon401() throws Exception {
        mvc.perform(post(APROBAR)).andExpect(status().isUnauthorized());
        mvc.perform(rechazo("{\"comentario\":\"Borroso\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void aprobarYRechazarConUnTokenSinSessionIdSon401() throws Exception {
        when(decoder.decode("token-sin-sesion")).thenReturn(jwt("token-sin-sesion", ADMIN, null));

        mvc.perform(post(APROBAR).header(HttpHeaders.AUTHORIZATION, "Bearer token-sin-sesion"))
                .andExpect(status().isUnauthorized());
        mvc.perform(rechazo("{\"comentario\":\"Borroso\"}").header(HttpHeaders.AUTHORIZATION, "Bearer token-sin-sesion"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void aprobarYRechazarSinLaSesion2faVerificadaSon403Con2faRequerida() throws Exception {
        when(sesiones.estaVerificada(any(), eq(ADMIN), any())).thenReturn(false);

        como(ADMIN, post(APROBAR)).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        como(ADMIN, rechazo("{\"comentario\":\"Borroso\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("2FA_REQUERIDA"));
        verifyNoInteractions(servicio);
    }

    @Test
    void unDocumentoInexistenteEs404EnLasDos() throws Exception {
        RecursoNoEncontradoException noExiste = new RecursoNoEncontradoException("No existe un documento con ese id.");
        when(servicio.aprobar(any(), eq(DOCUMENTO))).thenThrow(noExiste);
        when(servicio.rechazar(any(), eq(DOCUMENTO), any())).thenThrow(noExiste);

        como(ADMIN, post(APROBAR)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un documento con ese id."));
        como(ADMIN, rechazo("{\"comentario\":\"Borroso\"}")).andExpect(status().isNotFound());
    }

    @Test
    void unDocumentoYaRevisadoEs409EnLasDos() throws Exception {
        ConflictoException yaRevisado = new ConflictoException(
                "estado: el documento ya fue revisado y no se puede revisar de nuevo", List.of("estado"));
        when(servicio.aprobar(any(), eq(DOCUMENTO))).thenThrow(yaRevisado);
        when(servicio.rechazar(any(), eq(DOCUMENTO), any())).thenThrow(yaRevisado);

        como(ADMIN, post(APROBAR)).andExpect(status().isConflict()).andExpect(jsonPath("$.campos[0]").value("estado"));
        como(ADMIN, rechazo("{\"comentario\":\"Borroso\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos[0]").value("estado"));
    }

    @Test
    void unIdQueNoEsUuidEsElError400EnLasDos() throws Exception {
        como(ADMIN, post("/api/documentos/no-es-uuid/aprobar")).andExpect(status().isBadRequest());
        como(ADMIN, post("/api/documentos/no-es-uuid/rechazar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"comentario\":\"Borroso\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void descargarUnDocumentoAjenoOInexistenteEs404() throws Exception {
        when(servicio.descargar(any(), eq(DOCUMENTO)))
                .thenThrow(new RecursoNoEncontradoException("No existe un documento con ese id."));

        como(ACUDIENTE, get(DESCARGA)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un documento con ese id."));
    }
}
