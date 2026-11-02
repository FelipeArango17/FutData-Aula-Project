package co.edu.futdata.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.FranjaHorarioRequestDTO;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IFranjaHorarioService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.security.RespuestasSeguridad;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * /api/horario-club por HTTP (MockMvc) con la fachada simulada: roles, códigos, JSON y errores. Los roles con
 * tokens reales (JWT, 2FA y {@code @PreAuthorize}) están también en {@code SeguridadTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class FranjaHorarioControllerTest {

    private static final UUID ID = UUID.fromString("0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b");
    private static final UUID CATEGORIA = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID ENTRENADOR = UUID.fromString("e0000000-0000-0000-0000-00000000000a");
    private static final String RUTA = "/api/horario-club";
    private static final String RUTA_ID = RUTA + "/" + ID;
    private static final String CUERPO = """
            {"idCategoria": "%s", "dia": 2, "horaInicio": "16:30", "horaFin": "18:00",
             "sede": "UVA El Paraíso", "idEntrenador": null, "grupo": "tarde"}""".formatted(CATEGORIA);
    private static final String CRUCE = "Ese entrenador ya tiene otra franja que se cruza con este horario";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private IFranjaHorarioService servicio;

    private static RequestPostProcessor como(String rol) {
        return user("usuario-" + rol).roles(rol);
    }

    private static final RequestPostProcessor ADMIN = como("ADMINISTRADOR");

    private static FranjaHorarioResponseDTO semilleroTarde() {
        return new FranjaHorarioResponseDTO(ID, CATEGORIA, "Semillero", 2, "16:30", "18:00", "UVA El Paraíso",
                null, null, null, "tarde");
    }

    private static FranjaHorarioResponseDTO sub12ConEntrenador() {
        return new FranjaHorarioResponseDTO(ID, CATEGORIA, "Sub-12", 1, "19:00", "20:00", "Horizontes",
                ENTRENADOR, "Luis Pérez", false, null);
    }

    private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static ResultActions error(ResultActions respuesta, int estado, String ruta) throws Exception {
        return respuesta
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.ruta").value(ruta));
    }

    // --- Roles ----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "TESORERO", "ENTRENADOR", "ACUDIENTE"})
    void losCuatroRolesLeenElHorario(String rol) throws Exception {
        when(servicio.listar(null, null, null)).thenReturn(List.of(semilleroTarde()));
        when(servicio.consultar(ID)).thenReturn(semilleroTarde());

        mvc.perform(get(RUTA).with(como(rol))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idFranja").value(ID.toString()));
        mvc.perform(get(RUTA_ID).with(como(rol))).andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("Semillero"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TESORERO", "ENTRENADOR", "ACUDIENTE"})
    void soloElAdministradorCreaReemplazaYElimina(String rol) throws Exception {
        error(mvc.perform(conCuerpo(post(RUTA), CUERPO).with(como(rol))), 403, RUTA)
                .andExpect(jsonPath("$.mensaje").value(RespuestasSeguridad.SIN_PERMISO));
        error(mvc.perform(conCuerpo(put(RUTA_ID), CUERPO).with(como(rol))), 403, RUTA_ID);
        error(mvc.perform(delete(RUTA_ID).with(como(rol))), 403, RUTA_ID);
        verifyNoInteractions(servicio);
    }

    @Test
    void sinSesionEs401() throws Exception {
        error(mvc.perform(get(RUTA)), 401, RUTA);
        verifyNoInteractions(servicio);
    }

    // --- Lectura --------------------------------------------------------------------------------------------

    @Test
    void laRespuestaTraeSoloLosCamposDeLaFranjaSinDatosPersonales() throws Exception {
        when(servicio.consultar(ID)).thenReturn(sub12ConEntrenador());

        String cuerpo = mvc.perform(get(RUTA_ID).with(como("ACUDIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value("19:00"))
                .andExpect(jsonPath("$.horaFin").value("20:00"))
                .andExpect(jsonPath("$.entrenador").value("Luis Pérez"))
                .andExpect(jsonPath("$.entrenadorActivo").value(false))
                .andReturn().getResponse().getContentAsString();

        List<String> campos = new ArrayList<>();
        JsonNode nodo = json.readTree(cuerpo);
        nodo.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactly("idFranja", "idCategoria", "categoria", "dia", "horaInicio", "horaFin",
                "sede", "idEntrenador", "entrenador", "entrenadorActivo", "grupo");
    }

    @Test
    void sinEntrenadorLosCamposDelEntrenadorVanEnNull() throws Exception {
        when(servicio.consultar(ID)).thenReturn(semilleroTarde());
        mvc.perform(get(RUTA_ID).with(ADMIN))
                .andExpect(jsonPath("$.idEntrenador").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.entrenador").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.entrenadorActivo").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.grupo").value("tarde"));
    }

    @Test
    void losFiltrosLleganAlServicio() throws Exception {
        when(servicio.listar("Semillero", ENTRENADOR, "UVA El Paraíso")).thenReturn(List.of(semilleroTarde()));
        mvc.perform(get(RUTA).with(ADMIN)
                        .param("categoria", "Semillero")
                        .param("entrenador", ENTRENADOR.toString())
                        .param("sede", "UVA El Paraíso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idFranja", contains(ID.toString())));
    }

    @Test
    void unFiltroEntrenadorQueNoEsUuidDa400SinLlamarAlServicio() throws Exception {
        error(mvc.perform(get(RUTA).with(ADMIN).param("entrenador", "Jhon")), 400, RUTA)
                .andExpect(jsonPath("$.mensaje").value("El parámetro 'entrenador' tiene un valor inválido"));
        verifyNoInteractions(servicio);
    }

    @Test
    void filtrosCategoriaYSedeInvalidosDan400ConLosDosCampos() throws Exception {
        when(servicio.listar("Sub-10", null, "Estadio")).thenThrow(new DatosInvalidosException(Map.of(
                "categoria", "debe ser una de Párvulos, Cachorros, Semillero, Sub-11, Sub-12, Sub-13, Sub-14, Sub-15, Sub-16",
                "sede", "debe ser Horizontes o UVA El Paraíso")));
        error(mvc.perform(get(RUTA).with(ADMIN).param("categoria", "Sub-10").param("sede", "Estadio")), 400, RUTA)
                .andExpect(jsonPath("$.campos", contains("categoria", "sede")));
    }

    @Test
    void consultarUnaFranjaQueNoExisteDa404() throws Exception {
        when(servicio.consultar(ID)).thenThrow(new RecursoNoEncontradoException("No existe una franja con ese id."));
        error(mvc.perform(get(RUTA_ID).with(como("TESORERO"))), 404, RUTA_ID)
                .andExpect(jsonPath("$.mensaje").value("No existe una franja con ese id."));
    }

    @Test
    void unIdQueNoEsUuidDa400() throws Exception {
        error(mvc.perform(get(RUTA + "/no-es-uuid").with(ADMIN)), 400, RUTA + "/no-es-uuid");
        verifyNoInteractions(servicio);
    }

    // --- Escritura ------------------------------------------------------------------------------------------

    @Test
    void elAdministradorCreaUnaFranjaYRecibe201ConLocation() throws Exception {
        when(servicio.crear(any())).thenReturn(semilleroTarde());

        mvc.perform(conCuerpo(post(RUTA), CUERPO).with(ADMIN))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(RUTA_ID)))
                .andExpect(jsonPath("$.idFranja").value(ID.toString()))
                .andExpect(jsonPath("$.horaInicio").value("16:30"));

        ArgumentCaptor<FranjaHorarioRequestDTO> recibido = ArgumentCaptor.forClass(FranjaHorarioRequestDTO.class);
        verify(servicio).crear(recibido.capture());
        assertThat(recibido.getValue()).isEqualTo(new FranjaHorarioRequestDTO(CATEGORIA, 2, "16:30", "18:00",
                "UVA El Paraíso", null, "tarde"));
    }

    @Test
    void lasValidacionesDelServicioDan400ConLosCampos() throws Exception {
        when(servicio.crear(any())).thenThrow(new DatosInvalidosException(Map.of(
                "grupo", "solo se asigna en Cachorros y Semillero; la franja es de Sub-12",
                "horaInicio", "debe tener el formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00")));
        error(mvc.perform(conCuerpo(post(RUTA), CUERPO).with(ADMIN)), 400, RUTA)
                .andExpect(jsonPath("$.campos", contains("grupo", "horaInicio")))
                .andExpect(jsonPath("$.mensaje").value("grupo: solo se asigna en Cachorros y Semillero; la franja es "
                        + "de Sub-12; horaInicio: debe tener el formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00"));
    }

    @Test
    void elCruceDa409ConElMensajeExacto() throws Exception {
        when(servicio.crear(any())).thenThrow(new ConflictoException(CRUCE, List.of("idEntrenador")));
        error(mvc.perform(conCuerpo(post(RUTA), CUERPO).with(ADMIN)), 409, RUTA)
                .andExpect(jsonPath("$.mensaje").value(CRUCE))
                .andExpect(jsonPath("$.campos", contains("idEntrenador")));
    }

    @Test
    void elPutReemplazaLaFranja() throws Exception {
        when(servicio.actualizar(eq(ID), any())).thenReturn(semilleroTarde());
        mvc.perform(conCuerpo(put(RUTA_ID), CUERPO).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idFranja").value(ID.toString()));
    }

    @Test
    void elPutConCruceDa409() throws Exception {
        when(servicio.actualizar(eq(ID), any())).thenThrow(new ConflictoException(CRUCE, List.of("idEntrenador")));
        error(mvc.perform(conCuerpo(put(RUTA_ID), CUERPO).with(ADMIN)), 409, RUTA_ID)
                .andExpect(jsonPath("$.mensaje").value(CRUCE));
    }

    @Test
    void elPutDeUnIdInexistenteConCuerpoInvalidoDa404PorqueElControladorNoValidaAntes() throws Exception {
        when(servicio.actualizar(eq(ID), any())).thenThrow(new RecursoNoEncontradoException("No existe una franja con ese id."));
        String invalido = "{\"dia\": 9, \"horaInicio\": \"7:30\", \"sede\": \"Estadio\", \"grupo\": \"noche\"}";
        error(mvc.perform(conCuerpo(put(RUTA_ID), invalido).with(ADMIN)), 404, RUTA_ID)
                .andExpect(jsonPath("$.mensaje").value("No existe una franja con ese id."));
    }

    @Test
    void elDeleteResponde204SinCuerpo() throws Exception {
        mvc.perform(delete(RUTA_ID).with(ADMIN))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(servicio).eliminar(ID);
    }

    @Test
    void elDeleteDeUnaFranjaQueNoExisteDa404() throws Exception {
        doThrow(new RecursoNoEncontradoException("No existe una franja con ese id.")).when(servicio).eliminar(ID);
        error(mvc.perform(delete(RUTA_ID).with(ADMIN)), 404, RUTA_ID);
    }

    // --- Cuerpos que no se pueden leer: 400 del manejador global, sin llegar al servicio -------------------------

    @Test
    void unDiaComoTextoDa400NombrandoElCampo() throws Exception {
        error(mvc.perform(conCuerpo(post(RUTA), "{\"dia\": \"lunes\"}").with(ADMIN)), 400, RUTA)
                .andExpect(jsonPath("$.mensaje").value("dia: tiene un valor o un formato inválido"))
                .andExpect(jsonPath("$.campos", contains("dia")));
        verifyNoInteractions(servicio);
    }

    @Test
    void unIdCategoriaQueNoEsUuidDa400NombrandoElCampo() throws Exception {
        error(mvc.perform(conCuerpo(post(RUTA), "{\"idCategoria\": \"abc\"}").with(ADMIN)), 400, RUTA)
                .andExpect(jsonPath("$.mensaje").value("idCategoria: tiene un valor o un formato inválido"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("abc"))));
        verifyNoInteractions(servicio);
    }

    @Test
    void unIdEntrenadorQueNoEsUuidDa400NombrandoElCampo() throws Exception {
        error(mvc.perform(conCuerpo(put(RUTA_ID), "{\"idEntrenador\": \"123-xyz\"}").with(ADMIN)), 400, RUTA_ID)
                .andExpect(jsonPath("$.mensaje").value("idEntrenador: tiene un valor o un formato inválido"))
                .andExpect(jsonPath("$.campos", contains("idEntrenador")));
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{no es json", "[1, 2"})
    void unCuerpoVacioOIlegibleDa400(String cuerpo) throws Exception {
        error(mvc.perform(conCuerpo(post(RUTA), cuerpo).with(ADMIN)), 400, RUTA)
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición falta o no es un JSON válido"));
        verifyNoInteractions(servicio);
    }
}
