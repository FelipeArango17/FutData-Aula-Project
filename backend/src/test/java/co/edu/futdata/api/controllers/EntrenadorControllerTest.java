package co.edu.futdata.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.api.dto.EntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IEntrenadorService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * /api/entrenadores y /api/categorias por HTTP (MockMvc) con la fachada simulada: códigos, JSON y errores. Como
 * administrador; los permisos por rol están en {@code SeguridadTest}.
 */
@SpringBootTest
@WithMockUser(roles = "ADMINISTRADOR")
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class EntrenadorControllerTest {

    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final String CUERPO = """
            {"nombre": "Luis", "apellido": "Pérez", "tipoDocumento": "CC", "documento": "1098765432",
             "fechaNacimiento": "1990-05-14", "telefono": "3001112233", "correo": "luis.perez@pradofc.co",
             "especialidad": "Porteros", "horarioTrabajo": "Lunes a viernes", "direccion": "Calle 40 Sur 75b-81"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IEntrenadorService servicio;

    private static EntrenadorResponseDTO luis(List<String> categorias) {
        return new EntrenadorResponseDTO(ID, "Luis", "Pérez", "CC", "1098765432", LocalDate.of(1990, 5, 14),
                "3001112233", "luis.perez@pradofc.co", "Porteros", categorias, "Lunes a viernes", null, true);
    }

    private static ResultActions error(ResultActions respuesta, int estado, String error, String ruta) throws Exception {
        return respuesta
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.ruta").value(ruta));
    }

    @Test
    void listaConLaFormaDeDatosDelFrontend() throws Exception {
        when(servicio.listar()).thenReturn(List.of(luis(List.of("Sub-8", "Sub-12"))));

        mvc.perform(get("/api/entrenadores"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Luis"))
                .andExpect(jsonPath("$[0].apellido").value("Pérez"))
                .andExpect(jsonPath("$[0].tipoDocumento").value("CC"))
                .andExpect(jsonPath("$[0].documento").value("1098765432"))
                .andExpect(jsonPath("$[0].fechaNacimiento").value("1990-05-14"))
                .andExpect(jsonPath("$[0].telefono").value("3001112233"))
                .andExpect(jsonPath("$[0].correo").value("luis.perez@pradofc.co"))
                .andExpect(jsonPath("$[0].especialidad").value("Porteros"))
                .andExpect(jsonPath("$[0].categorias").value(contains("Sub-8", "Sub-12")))
                .andExpect(jsonPath("$[0].horarioTrabajo").value("Lunes a viernes"))
                .andExpect(jsonPath("$[0].direccion").value(nullValue()))
                .andExpect(jsonPath("$[0].activo").value(true));
    }

    @Test
    void consultaUno() throws Exception {
        when(servicio.consultar(ID)).thenReturn(luis(List.of()));

        mvc.perform(get("/api/entrenadores/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.categorias").isEmpty());
    }

    @Test
    void unIdQueNoExisteEs404ConElFormatoDeErrores() throws Exception {
        when(servicio.consultar(ID)).thenThrow(new RecursoNoEncontradoException("No existe un entrenador con ese id."));

        error(mvc.perform(get("/api/entrenadores/" + ID)), 404, "Not Found", "/api/entrenadores/" + ID)
                .andExpect(jsonPath("$.mensaje").value("No existe un entrenador con ese id."));
    }

    @Test
    void unIdQueNoEsUuidEs400SinLlamarAlServicio() throws Exception {
        error(mvc.perform(get("/api/entrenadores/1")), 400, "Bad Request", "/api/entrenadores/1")
                .andExpect(jsonPath("$.mensaje").value("El parámetro 'id' tiene un valor inválido"));
        error(mvc.perform(put("/api/entrenadores/abc/categorias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categorias\": []}")), 400, "Bad Request", "/api/entrenadores/abc/categorias");
        verifyNoInteractions(servicio);
    }

    @Test
    void creaResponde201ConLocationYElEntrenador() throws Exception {
        when(servicio.crear(any())).thenReturn(luis(List.of()));

        mvc.perform(post("/api/entrenadores").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/entrenadores/" + ID)))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.nombre").value("Luis"));

        ArgumentCaptor<EntrenadorRequestDTO> recibido = ArgumentCaptor.forClass(EntrenadorRequestDTO.class);
        verify(servicio).crear(recibido.capture());
        assertThat(recibido.getValue()).isEqualTo(new EntrenadorRequestDTO("Luis", "Pérez", "CC", "1098765432",
                LocalDate.of(1990, 5, 14), "3001112233", "luis.perez@pradofc.co", "Porteros", "Lunes a viernes",
                "Calle 40 Sur 75b-81"));
    }

    @Test
    void alCrearIgnoraElIdYLasCategoriasDelCuerpo() throws Exception {
        when(servicio.crear(any())).thenReturn(luis(List.of()));
        String conExtras = CUERPO.replace("{\"nombre\"", "{\"id\": 7, \"categorias\": [\"Sub-8\"], \"nombre\"");

        mvc.perform(post("/api/entrenadores").contentType(MediaType.APPLICATION_JSON).content(conExtras))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categorias").isEmpty());
    }

    @Test
    void datosInvalidosEs400ConMensajeYCampos() throws Exception {
        when(servicio.crear(any())).thenThrow(new DatosInvalidosException(Map.of(
                "telefono", "debe tener exactamente 10 dígitos, sin espacios", "fechaNacimiento", "es obligatoria")));

        error(mvc.perform(post("/api/entrenadores").contentType(MediaType.APPLICATION_JSON).content(CUERPO)),
                400, "Bad Request", "/api/entrenadores")
                .andExpect(jsonPath("$.mensaje").value(
                        "fechaNacimiento: es obligatoria; telefono: debe tener exactamente 10 dígitos, sin espacios"))
                .andExpect(jsonPath("$.campos").value(contains("fechaNacimiento", "telefono")));
    }

    @Test
    void unaFechaConOtroFormatoEs400PorEseCampoSinLlamarAlServicio() throws Exception {
        String fechaColombiana = CUERPO.replace("\"1990-05-14\"", "\"14/05/1990\"");

        error(mvc.perform(post("/api/entrenadores").contentType(MediaType.APPLICATION_JSON).content(fechaColombiana)),
                400, "Bad Request", "/api/entrenadores")
                .andExpect(jsonPath("$.mensaje").value("fechaNacimiento: tiene un valor o un formato inválido"))
                .andExpect(jsonPath("$.campos").value(contains("fechaNacimiento")));
        verifyNoInteractions(servicio);
    }

    @Test
    void unCuerpoQueNoEsJsonEs400() throws Exception {
        error(mvc.perform(post("/api/entrenadores").contentType(MediaType.APPLICATION_JSON).content("{nombre")),
                400, "Bad Request", "/api/entrenadores")
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición falta o no es un JSON válido"));
    }

    @Test
    void unDuplicadoEs409IndicandoElCampo() throws Exception {
        when(servicio.crear(any())).thenThrow(
                new ConflictoException("correo: ya está registrado en otro entrenador", List.of("correo")));

        error(mvc.perform(post("/api/entrenadores").contentType(MediaType.APPLICATION_JSON).content(CUERPO)),
                409, "Conflict", "/api/entrenadores")
                .andExpect(jsonPath("$.mensaje").value("correo: ya está registrado en otro entrenador"))
                .andExpect(jsonPath("$.campos").value(contains("correo")));
    }

    @Test
    void editaYDevuelveElEntrenador() throws Exception {
        when(servicio.editar(eq(ID), any())).thenReturn(luis(List.of("Sub-12")));

        mvc.perform(put("/api/entrenadores/" + ID).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias").value(contains("Sub-12")));
    }

    @Test
    void editarUnoQueNoExisteEs404() throws Exception {
        when(servicio.editar(eq(ID), any())).thenThrow(new RecursoNoEncontradoException("No existe un entrenador con ese id."));

        error(mvc.perform(put("/api/entrenadores/" + ID).contentType(MediaType.APPLICATION_JSON).content(CUERPO)),
                404, "Not Found", "/api/entrenadores/" + ID);
    }

    @Test
    void asignaCategoriasConReasignar() throws Exception {
        when(servicio.asignarCategorias(eq(ID), any())).thenReturn(luis(List.of("Sub-8", "Sub-12")));

        mvc.perform(put("/api/entrenadores/" + ID + "/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\": [\"Sub-12\", \"Sub-8\"], \"reasignar\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias").value(contains("Sub-8", "Sub-12")));

        verify(servicio).asignarCategorias(ID, new AsignarCategoriasRequestDTO(List.of("Sub-12", "Sub-8"), true));
    }

    @Test
    void categoriasDeOtroSinReasignarEs409() throws Exception {
        String mensaje = "Sub-8 ya tiene otro entrenador. Confirma la reasignación para pasarla a este entrenador.";
        when(servicio.asignarCategorias(eq(ID), any())).thenThrow(new ConflictoException(mensaje, List.of("categorias")));

        error(mvc.perform(put("/api/entrenadores/" + ID + "/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorias\": [\"Sub-8\"]}")), 409, "Conflict", "/api/entrenadores/" + ID + "/categorias")
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.campos").value(contains("categorias")));
        verify(servicio).asignarCategorias(ID, new AsignarCategoriasRequestDTO(List.of("Sub-8"), null));
    }

    @Test
    void desactivaConPatchEstado() throws Exception {
        EntrenadorResponseDTO l = luis(List.of());
        EntrenadorResponseDTO inactivo = new EntrenadorResponseDTO(l.id(), l.nombre(), l.apellido(), l.tipoDocumento(),
                l.documento(), l.fechaNacimiento(), l.telefono(), l.correo(), l.especialidad(), l.categorias(),
                l.horarioTrabajo(), l.direccion(), false);
        when(servicio.cambiarEstado(eq(ID), any())).thenReturn(inactivo);

        mvc.perform(patch("/api/entrenadores/" + ID + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.categorias").isEmpty());

        verify(servicio).cambiarEstado(ID, new CambiarEstadoEntrenadorRequestDTO(false));
    }

    @Test
    void unaCategoriaSinEntrenadorLlegaConEntrenadorNull() throws Exception {
        when(servicio.listarCategorias()).thenReturn(List.of(new CategoriaResponseDTO(UUID.randomUUID(), "Sub-8", 0, 7, null)));

        mvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Sub-8"))
                .andExpect(jsonPath("$[0].entrenador").value(nullValue()));
    }

    @Test
    void listaLasCategoriasConSuEntrenador() throws Exception {
        UUID sub12 = UUID.randomUUID();
        when(servicio.listarCategorias()).thenReturn(List.of(new CategoriaResponseDTO(sub12, "Sub-12", 10, 11,
                new CategoriaResponseDTO.EntrenadorResumenDTO(ID, "Andrés", "Mora"))));

        mvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sub12.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Sub-12"))
                .andExpect(jsonPath("$[0].edadMinima").value(10))
                .andExpect(jsonPath("$[0].edadMaxima").value(11))
                .andExpect(jsonPath("$[0].entrenador.id").value(ID.toString()))
                .andExpect(jsonPath("$[0].entrenador.nombre").value("Andrés"))
                .andExpect(jsonPath("$[0].entrenador.apellido").value("Mora"))
                .andExpect(jsonPath("$[0].entrenador.correo").doesNotExist())
                .andExpect(jsonPath("$[0].entrenador.documento").doesNotExist());
    }
}
