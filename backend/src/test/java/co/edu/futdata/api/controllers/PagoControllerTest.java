package co.edu.futdata.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.AprobarPagoRequestDTO;
import co.edu.futdata.api.dto.CrearPagoRequestDTO;
import co.edu.futdata.api.dto.IniciarWompiResponseDTO;
import co.edu.futdata.api.dto.MiPagoResponseDTO;
import co.edu.futdata.api.dto.PaginaResponseDTO;
import co.edu.futdata.api.dto.PagoResponseDTO;
import co.edu.futdata.api.dto.ResultadoGeneracionDTO;
import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.TipoPago;
import co.edu.futdata.persistence.dao.FiltroPagos;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.IPagoService;
import co.edu.futdata.services.facade.IPagoWompiService;
import co.edu.futdata.services.facade.PagosNoConfiguradosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.security.JwtValidator;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** /api/pagos por HTTP con la cadena de seguridad completa y los servicios simulados: JSON, parámetros y errores. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class PagoControllerTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
    private static final UUID PAGO = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DEPORTISTA = UUID.fromString("00000000-0000-0000-0000-0000000000de");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;
    @MockitoBean
    private IPagoService pagos;
    @MockitoBean
    private IPagoWompiService webhook;

    @BeforeEach
    void preparar() {
        for (var par : List.of(List.of(ADMIN, "ADMINISTRADOR"), List.of(TESORERO, "TESORERO"), List.of(ACUDIENTE, "ACUDIENTE"))) {
            UUID id = (UUID) par.get(0);
            when(decoder.decode("token-" + id)).thenReturn(Jwt.withTokenValue("token-" + id).header("alg", "ES256").subject(id.toString())
                    .claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) par.get(1)));
        }
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
    }

    private ResultActions como(UUID usuario, MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    private static PagoResponseDTO pagoDeEjemplo() {
        return new PagoResponseDTO(PAGO, DEPORTISTA, "Pedro Gómez", TipoPago.MENSUALIDAD, "Mensualidad noviembre 2026", 90_000,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), EstadoPago.PENDIENTE, false, null, null, null, null, null, null);
    }

    @Test
    void crearDevuelve201ConLocationYPasaLosDatos() throws Exception {
        when(pagos.crear(eq(ADMIN), any())).thenReturn(pagoDeEjemplo());

        como(ADMIN, post("/api/pagos").contentType(MediaType.APPLICATION_JSON).content("""
                {"idDeportista":"%s","tipo":"MENSUALIDAD","monto":90000,"fechaVencimiento":"2026-11-05","periodo":"2026-11-01"}""".formatted(DEPORTISTA)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/pagos/" + PAGO)))
                .andExpect(jsonPath("$.id").value(PAGO.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.vencido").value(false));

        ArgumentCaptor<CrearPagoRequestDTO> dto = ArgumentCaptor.forClass(CrearPagoRequestDTO.class);
        verify(pagos).crear(eq(ADMIN), dto.capture());
        assertThat(dto.getValue()).isEqualTo(new CrearPagoRequestDTO(DEPORTISTA, TipoPago.MENSUALIDAD, null, 90_000L,
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void unTipoOUnaFechaInvalidosSon400SinRepetirElValor() throws Exception {
        como(ADMIN, post("/api/pagos").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"REGALO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("tipo"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("REGALO"))));
    }

    @Test
    void unaMensualidadRepetidaEs409ConSuCampo() throws Exception {
        when(pagos.crear(any(), any())).thenThrow(new ConflictoException("periodo: el deportista ya tiene una mensualidad de ese periodo", List.of("periodo")));
        como(ADMIN, post("/api/pagos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos[0]").value("periodo"));
    }

    @Test
    void listarLeeLosFiltrosLaPaginaYElOrden() throws Exception {
        when(pagos.listar(any(), any(), anyInt(), anyInt())).thenReturn(new PaginaResponseDTO<>(List.of(pagoDeEjemplo()), 41, 2, 10));

        como(TESORERO, get("/api/pagos").param("page", "2").param("size", "10").param("sort", "monto,asc").param("estado", "PENDIENTE")
                .param("tipo", "UNIFORME").param("idDeportista", DEPORTISTA.toString()).param("periodo", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(PAGO.toString()))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(10));

        verify(pagos).listar(new FiltroPagos(EstadoPago.PENDIENTE, TipoPago.UNIFORME, DEPORTISTA, LocalDate.of(2026, 11, 1)), "monto,asc", 2, 10);
    }

    @Test
    void listarConValoresPorDefectoYFiltrosInvalidos() throws Exception {
        when(pagos.listar(any(), any(), anyInt(), anyInt())).thenReturn(new PaginaResponseDTO<>(List.of(), 0, 0, 20));
        como(ADMIN, get("/api/pagos")).andExpect(status().isOk());
        verify(pagos).listar(new FiltroPagos(null, null, null, null), null, 0, 20);

        como(ADMIN, get("/api/pagos").param("periodo", "noviembre")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0]").value("periodo"));
        como(ADMIN, get("/api/pagos").param("estado", "VENCIDO")).andExpect(status().isBadRequest());
        como(ADMIN, get("/api/pagos").param("idDeportista", "no-es-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void misPagosUsaElUsuarioDelToken() throws Exception {
        when(pagos.misPagos(ACUDIENTE)).thenReturn(List.of(new MiPagoResponseDTO(PAGO, DEPORTISTA, "Pedro Gómez", TipoPago.MENSUALIDAD,
                "Mensualidad noviembre 2026", 90_000, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), EstadoPago.APROBADO, false,
                LocalDate.of(2026, 11, 3), "TARJETA", true)));
        como(ACUDIENTE, get("/api/pagos/mis-pagos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tieneComprobante").value(true))
                .andExpect(jsonPath("$[0].referenciaWompi").doesNotExist())
                .andExpect(jsonPath("$[0].notaInterna").doesNotExist());
    }

    @Test
    void iniciarWompiDevuelveLosParametrosDelCheckoutSinSecretos() throws Exception {
        when(pagos.iniciarWompi(ACUDIENTE, PAGO)).thenReturn(new IniciarWompiResponseDTO("pub_test_x", "FD-" + PAGO, 9_000_000, "COP", "firma", "https://club.test/ok"));
        como(ACUDIENTE, post("/api/pagos/" + PAGO + "/iniciar-wompi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llavePublica").value("pub_test_x"))
                .andExpect(jsonPath("$.referencia").value("FD-" + PAGO))
                .andExpect(jsonPath("$.montoEnCentavos").value(9_000_000))
                .andExpect(jsonPath("$.moneda").value("COP"))
                .andExpect(jsonPath("$.firmaIntegridad").value("firma"))
                .andExpect(jsonPath("$.urlRedireccion").value("https://club.test/ok"));
    }

    @Test
    void iniciarWompiDeUnCobroAjenoEs404() throws Exception {
        when(pagos.iniciarWompi(any(), any())).thenThrow(new RecursoNoEncontradoException("No existe un pago con ese id."));
        como(ACUDIENTE, post("/api/pagos/" + PAGO + "/iniciar-wompi")).andExpect(status().isNotFound());
    }

    @Test
    void sinLlavesDeWompiEs503ConElCodigoYSinNombrarVariables() throws Exception {
        when(pagos.iniciarWompi(any(), any())).thenThrow(new PagosNoConfiguradosException());
        como(ACUDIENTE, post("/api/pagos/" + PAGO + "/iniciar-wompi"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PAGOS_NO_CONFIGURADOS"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("WOMPI_"))));
    }

    @Test
    void aprobarLeeLaFechaYElMetodo() throws Exception {
        when(pagos.aprobar(eq(TESORERO), eq(PAGO), any())).thenReturn(pagoDeEjemplo());
        como(TESORERO, post("/api/pagos/" + PAGO + "/aprobar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fechaPago\":\"2026-11-08\",\"metodo\":\"EFECTIVO\",\"nota\":\"En sede\"}")).andExpect(status().isOk());
        verify(pagos).aprobar(TESORERO, PAGO, new AprobarPagoRequestDTO(LocalDate.of(2026, 11, 8), MetodoPago.EFECTIVO, "En sede"));
    }

    @Test
    void rechazarAceptaUnCuerpoVacio() throws Exception {
        when(pagos.rechazar(eq(ADMIN), eq(PAGO), isNull())).thenReturn(pagoDeEjemplo());
        como(ADMIN, post("/api/pagos/" + PAGO + "/rechazar")).andExpect(status().isOk());
    }

    @Test
    void elComprobanteEsUnPdfYElAcudienteNoEsPrivilegiado() throws Exception {
        when(pagos.comprobante(any(), any(Boolean.class), eq(PAGO))).thenReturn("%PDF-1.4 prueba".getBytes());

        como(ACUDIENTE, get("/api/pagos/" + PAGO + "/comprobante"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes("%PDF-1.4 prueba".getBytes()));
        verify(pagos).comprobante(ACUDIENTE, false, PAGO);

        como(TESORERO, get("/api/pagos/" + PAGO + "/comprobante")).andExpect(status().isOk());
        verify(pagos).comprobante(TESORERO, true, PAGO);
    }

    @Test
    void generarMensualidadesLeeElPeriodo() throws Exception {
        when(pagos.generarMensualidades(any(), any())).thenReturn(new ResultadoGeneracionDTO("2026-12", 12, 3));
        como(ADMIN, post("/api/pagos/generar-mensualidades").contentType(MediaType.APPLICATION_JSON).content("{\"periodo\":\"2026-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creadas").value(12))
                .andExpect(jsonPath("$.yaExistentes").value(3));
        verify(pagos).generarMensualidades(ADMIN, YearMonth.of(2026, 12));
    }

    @Test
    void elWebhookRecibeElCuerpoCrudoYElEncabezadoYResponde200Vacio() throws Exception {
        String cuerpo = "{\"event\":\"transaction.updated\",\"data\":{}}";
        mvc.perform(post("/api/pagos/webhook/wompi").contentType(MediaType.APPLICATION_JSON).content(cuerpo).header("X-Event-Checksum", "abc123"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        verify(webhook).procesarEvento(cuerpo, "abc123");
    }
}
