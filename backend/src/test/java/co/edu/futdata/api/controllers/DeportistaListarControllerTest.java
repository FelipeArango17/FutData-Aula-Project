package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.PaginaDTO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import co.edu.futdata.util.security.JwtValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * GET /api/deportistas por HTTP con la cadena de seguridad completa (JWT con rol) y el servicio simulado: roles, los
 * parámetros (incluida la categoría repetida) y el {@code Solicitante} que recibe el servicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class DeportistaListarControllerTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ENTRENADOR = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID TESORERO = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final String URL_FOTO = "https://proyecto.supabase.co/storage/v1/object/sign/fotos/x.jpg?token=t";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private IUsuarioDAO usuarios;
    @MockitoBean
    private ISesionVerificadaDAO sesiones;
    @MockitoBean
    private IDeportistaService servicio;

    @BeforeEach
    void preparar() {
        for (var par : List.of(List.of(ADMIN, "ADMINISTRADOR"), List.of(ENTRENADOR, "ENTRENADOR"),
                List.of(TESORERO, "TESORERO"), List.of(ACUDIENTE, "ACUDIENTE"))) {
            UUID id = (UUID) par.get(0);
            when(decoder.decode("token-" + id)).thenReturn(Jwt.withTokenValue("token-" + id).header("alg", "ES256")
                    .subject(id.toString()).claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
            when(usuarios.rolesActivos(id)).thenReturn(List.of((String) par.get(1)));
        }
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
    }

    private ResultActions como(UUID usuario, MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer token-" + usuario));
    }

    /** Con teléfono = vista del administrador; sin teléfono = vista del entrenador (sin documento ni estado). */
    private static DeportistaResponseDTO ana(String telefono, String estadoInscripcion) {
        boolean admin = telefono != null;
        return new DeportistaResponseDTO(ID, "Ana", "Pérez", "TI", admin ? "12345678" : null, LocalDate.of(2012, 4, 10),
                14, telefono, admin ? "Calle 10 # 20-30" : null, "Arquera", URL_FOTO, "Sub-14",
                UUID.fromString("0a0a0a0a-0000-4000-8000-000000000014"), null, true, estadoInscripcion);
    }

    @Test
    void elAdministradorListaConSusParametrosYElServicioRecibeSuSolicitante() throws Exception {
        when(servicio.listar(any(), any(), any(), any(), any(), any()))
                .thenReturn(PaginaDTO.de(List.of(ana("3001234567", "COMPLETA")), 1, 20, 21));

        como(ADMIN, get("/api/deportistas").param("page", "1").param("size", "20").param("sort", "nombre,desc")
                .param("activo", "true").param("categoria", "Sub-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.content[0].estadoInscripcion").value("COMPLETA"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(servicio).listar(1, 20, "nombre,desc", true, List.of("Sub-14"),
                new Solicitante(ADMIN, Solicitante.ADMINISTRADOR));
    }

    @Test
    void categoriaRepetidaOSeparadaPorComaLlegaComoLista() throws Exception {
        when(servicio.listar(any(), any(), any(), any(), any(), any())).thenReturn(PaginaDTO.de(List.of(), 0, 10, 0));

        como(ENTRENADOR, get("/api/deportistas").param("categoria", "Sub-11").param("categoria", "Sub-13"))
                .andExpect(status().isOk());
        como(ENTRENADOR, get("/api/deportistas").param("categoria", "Sub-11,Sub-12")).andExpect(status().isOk());

        Solicitante entrenador = new Solicitante(ENTRENADOR, Solicitante.ENTRENADOR);
        verify(servicio).listar(null, null, null, null, List.of("Sub-11", "Sub-13"), entrenador);
        verify(servicio).listar(null, null, null, null, List.of("Sub-11", "Sub-12"), entrenador);
    }

    @Test
    void elEntrenadorRecibeLaListaSinDocumentoNiEstadoInscripcionYConSuSub() throws Exception {
        when(servicio.listar(any(), any(), any(), any(), any(), any()))
                .thenReturn(PaginaDTO.de(List.of(ana(null, null)), 0, 10, 1));

        como(ENTRENADOR, get("/api/deportistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoria").value("Sub-14"))
                .andExpect(jsonPath("$.content[0].foto").value(URL_FOTO))
                .andExpect(jsonPath("$.content[0].telefono").value(Matchers.nullValue()))
                .andExpect(content().string(Matchers.not(Matchers.containsString("estadoInscripcion"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("\"documento\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("12345678"))));

        verify(servicio).listar(isNull(), isNull(), isNull(), isNull(), isNull(),
                org.mockito.ArgumentMatchers.eq(new Solicitante(ENTRENADOR, Solicitante.ENTRENADOR)));
    }

    @Test
    void unParametroInvalidoDelServicioResponde400() throws Exception {
        when(servicio.listar(any(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new DatosInvalidosException(Map.of("size", "debe estar entre 1 y 100")));

        como(ENTRENADOR, get("/api/deportistas").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("size"))
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("debe estar entre 1 y 100")));
    }

    @Test
    void unParametroQueNoEsNumeroResponde400SinLlamarAlServicio() throws Exception {
        como(ADMIN, get("/api/deportistas").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El parámetro 'page' tiene un valor inválido"));
        verifyNoInteractions(servicio);
    }

    @Test
    void tesoreroYAcudienteRecibenElMismo403SinLlegarAlServicio() throws Exception {
        for (UUID otro : List.of(TESORERO, ACUDIENTE)) {
            como(otro, get("/api/deportistas")).andExpect(status().isForbidden());
            como(otro, get("/api/deportistas").param("categoria", "Sub-11")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void sinSesionResponde401() throws Exception {
        mvc.perform(get("/api/deportistas")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void siElServicioFallaCerradoLaRespuestaEs403() throws Exception {
        when(servicio.listar(any(), any(), any(), any(), any(), any())).thenThrow(new SinPermisoException());

        como(ENTRENADOR, get("/api/deportistas")).andExpect(status().isForbidden());
    }

    @Test
    void laFichaSigueSiendoSoloDelAdministrador() throws Exception {
        como(ENTRENADOR, get("/api/deportistas/" + ID)).andExpect(status().isForbidden());
        como(TESORERO, get("/api/deportistas/" + ID)).andExpect(status().isForbidden());
        como(ACUDIENTE, get("/api/deportistas/" + ID)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
}
