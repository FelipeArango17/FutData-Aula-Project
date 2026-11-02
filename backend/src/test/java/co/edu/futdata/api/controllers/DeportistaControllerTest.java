package co.edu.futdata.api.controllers;

import org.springframework.security.test.context.support.WithMockUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.PaginaDTO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IDeportistaService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@WithMockUser(roles = "ADMINISTRADOR")
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class DeportistaControllerTest {

    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID CATEGORIA = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000014");
    private static final String CUERPO = """
            {"nombre":"Ana","apellido":"Pérez","tipoDocumento":"TI","documento":"12345678",
             "fechaNacimiento":"2012-04-10","telefono":"3001234567","direccion":"Calle 10 # 20-30"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IDeportistaService servicio;

    private static final String URL_FOTO = "https://proyecto.supabase.co/storage/v1/object/sign/fotos/x.jpg?token=t";

    /** Con teléfono = vista del administrador; sin teléfono = vista del entrenador (sin documento ni estado). */
    private static DeportistaResponseDTO ana(String telefono, String estadoInscripcion) {
        boolean admin = telefono != null;
        return new DeportistaResponseDTO(ID, "Ana", "Pérez", "TI", admin ? "12345678" : null, LocalDate.of(2012, 4, 10),
                14, telefono, admin ? "Calle 10 # 20-30" : null, "Arquera", URL_FOTO, "Sub-14", CATEGORIA, null, true,
                estadoInscripcion);
    }

    // --- Foto -----------------------------------------------------------------------------------------------

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @Test
    void subeLaFotoPorMultipartYDevuelveLaUrlFirmada() throws Exception {
        when(servicio.subirFoto(eq(ID), any())).thenReturn(ana("3001234567", "PENDIENTE"));

        mvc.perform(multipart("/api/deportistas/" + ID + "/foto")
                        .file(new MockMultipartFile("archivo", "mi foto.jpg", "image/jpeg", JPG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").value(URL_FOTO));
        verify(servicio).subirFoto(ID, JPG);
    }

    @Test
    void sinLaParteArchivoResponde400() throws Exception {
        mvc.perform(multipart("/api/deportistas/" + ID + "/foto")
                        .file(new MockMultipartFile("otra", "x.jpg", "image/jpeg", JPG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("archivo"));
        verifyNoInteractions(servicio);
    }

    @Test
    void unaFotoInvalidaResponde400ConElMensajeDelServicio() throws Exception {
        when(servicio.subirFoto(eq(ID), any())).thenThrow(new DatosInvalidosException(Map.of("archivo",
                "debe ser una imagen JPG o PNG (se revisa el contenido del archivo, no su extensión)")));

        mvc.perform(multipart("/api/deportistas/" + ID + "/foto")
                        .file(new MockMultipartFile("archivo", "falso.jpg", "image/jpeg", "%PDF-1.4".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("archivo"))
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("JPG o PNG")));
    }

    @Test
    void fotoDeUnDeportistaQueNoExisteResponde404() throws Exception {
        when(servicio.subirFoto(eq(ID), any())).thenThrow(new RecursoNoEncontradoException("No existe un deportista con ese id."));
        when(servicio.eliminarFoto(ID)).thenThrow(new RecursoNoEncontradoException("No existe un deportista con ese id."));

        mvc.perform(multipart("/api/deportistas/" + ID + "/foto").file(new MockMultipartFile("archivo", "a.jpg", "image/jpeg", JPG)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/deportistas/" + ID + "/foto")).andExpect(status().isNotFound());
    }

    @Test
    void quitarLaFotoResponde200() throws Exception {
        when(servicio.eliminarFoto(ID)).thenReturn(ana("3001234567", "PENDIENTE"));

        mvc.perform(delete("/api/deportistas/" + ID + "/foto")).andExpect(status().isOk());
        verify(servicio).eliminarFoto(ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TESORERO", "ENTRENADOR"})
    void soloElAdministradorSubeOQuitaLaFoto(String rol) throws Exception {
        var otroRol = SecurityMockMvcRequestPostProcessors.user("alguien").roles(rol);

        mvc.perform(multipart("/api/deportistas/" + ID + "/foto").file(new MockMultipartFile("archivo", "a.jpg", "image/jpeg", JPG))
                        .with(otroRol))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/deportistas/" + ID + "/foto").with(otroRol)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    /** Los topes de Spring deben dejar pasar más de 5 MB: el límite exacto lo aplica el servicio con un 400 claro. */
    @Test
    void losTopesDeMultipartDejanQueElServicioApliqueLos5Mb() {
        assertThat(multipartProperties.getMaxFileSize().toBytes()).isGreaterThan(5L * 1024 * 1024 + 1);
        assertThat(multipartProperties.getMaxRequestSize().toBytes())
                .isGreaterThan(multipartProperties.getMaxFileSize().toBytes());
    }

    @Autowired
    private MultipartProperties multipartProperties;

    private static final String EDICION = """
            {"nombre":"Ana","apellido":"Pérez","tipoDocumento":"TI","documento":"12345678",
             "fechaNacimiento":"2012-04-10","telefono":"3001234567","direccion":"Calle 10 # 20-30",
             "posicion":"Arquera","grupo":null,"activo":false}""";

    // --- PUT /api/deportistas/{id} --------------------------------------------------------------------------

    @Test
    void editaYDevuelve200ConLosCamposDelCuerpo() throws Exception {
        when(servicio.actualizar(eq(ID), any())).thenReturn(ana("3001234567", "PENDIENTE"));

        mvc.perform(put("/api/deportistas/" + ID).contentType(MediaType.APPLICATION_JSON).content(EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posicion").value("Arquera"));
        verify(servicio).actualizar(ID, new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678",
                LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30", "Arquera", null, false));
    }

    @Test
    void editarUnIdQueNoExisteResponde404() throws Exception {
        when(servicio.actualizar(eq(ID), any())).thenThrow(new RecursoNoEncontradoException("No existe un deportista con ese id."));

        mvc.perform(put("/api/deportistas/" + ID).contentType(MediaType.APPLICATION_JSON).content(EDICION))
                .andExpect(status().isNotFound());
    }

    @Test
    void editarConDocumentoRepetidoResponde409SinDatosDeOtro() throws Exception {
        when(servicio.actualizar(eq(ID), any())).thenThrow(new ConflictoException(
                "documento: ya está registrado en otro deportista", List.of("documento")));

        mvc.perform(put("/api/deportistas/" + ID).contentType(MediaType.APPLICATION_JSON).content(EDICION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("documento: ya está registrado en otro deportista"))
                .andExpect(jsonPath("$.campos[0]").value("documento"));
    }

    @Test
    void editarConDatosInvalidosResponde400() throws Exception {
        when(servicio.actualizar(eq(ID), any())).thenThrow(new DatosInvalidosException(
                Map.of("grupo", "solo se asigna en Cachorros y Semillero; por año de nacimiento está en Sub-11")));

        mvc.perform(put("/api/deportistas/" + ID).contentType(MediaType.APPLICATION_JSON).content(EDICION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("grupo"));
    }

    @Test
    void unIdQueNoEsUuidResponde400() throws Exception {
        mvc.perform(put("/api/deportistas/abc").contentType(MediaType.APPLICATION_JSON).content(EDICION))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TESORERO", "ENTRENADOR", "ACUDIENTE"})
    void soloElAdministradorCreaYEdita(String rol) throws Exception {
        var otroRol = SecurityMockMvcRequestPostProcessors.user("alguien").roles(rol);

        mvc.perform(post("/api/deportistas").with(otroRol).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/deportistas/" + ID).with(otroRol).contentType(MediaType.APPLICATION_JSON).content(EDICION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void registraYDevuelve201() throws Exception {
        when(servicio.crear(any())).thenReturn(ana("3001234567", "PENDIENTE"));

        mvc.perform(post("/api/deportistas").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/deportistas/" + ID)))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.edad").value(14))
                .andExpect(jsonPath("$.categoria").value("Sub-14"))
                .andExpect(jsonPath("$.idCategoria").value(CATEGORIA.toString()))
                .andExpect(jsonPath("$.grupo").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.estadoInscripcion").value("PENDIENTE"));
    }

    @Test
    void duplicadoResponde409YCampos() throws Exception {
        when(servicio.crear(any())).thenThrow(new ConflictoException(
                "documento: ya está registrado en otro deportista", List.of("documento")));

        mvc.perform(post("/api/deportistas").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos[0]").value("documento"))
                .andExpect(jsonPath("$.estado").value(409));
    }

    // --- GET /api/deportistas -------------------------------------------------------------------------------

    @Test
    void listaPaginadaConSusParametros() throws Exception {
        when(servicio.listar(1, 20, "nombre,desc", true, "Sub-14", true))
                .thenReturn(PaginaDTO.de(List.of(ana("3001234567", "COMPLETA")), 1, 20, 21));

        mvc.perform(get("/api/deportistas").param("page", "1").param("size", "20").param("sort", "nombre,desc")
                        .param("activo", "true").param("categoria", "Sub-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.content[0].estadoInscripcion").value("COMPLETA"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser(roles = "ENTRENADOR")
    void elEntrenadorNoRecibeEstadoInscripcionNiLaClave() throws Exception {
        when(servicio.listar(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(PaginaDTO.de(List.of(ana(null, null)), 0, 10, 1));

        mvc.perform(get("/api/deportistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoria").value("Sub-14"))
                .andExpect(jsonPath("$.content[0].foto").value(URL_FOTO))
                .andExpect(jsonPath("$.content[0].telefono").value(Matchers.nullValue()))
                .andExpect(content().string(Matchers.not(Matchers.containsString("estadoInscripcion"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("\"documento\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("12345678"))));
        verify(servicio).listar(null, null, null, null, null, false);
    }

    @Test
    void unParametroInvalidoDelServicioResponde400() throws Exception {
        when(servicio.listar(any(), eq(101), any(), any(), any(), anyBoolean()))
                .thenThrow(new DatosInvalidosException(Map.of("size", "debe estar entre 1 y 100")));

        mvc.perform(get("/api/deportistas").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("size"))
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("debe estar entre 1 y 100")));
    }

    @Test
    void unParametroQueNoEsNumeroResponde400SinLlamarAlServicio() throws Exception {
        mvc.perform(get("/api/deportistas").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El parámetro 'page' tiene un valor inválido"));
        verifyNoInteractions(servicio);
    }

    @Test
    @WithMockUser(roles = "TESORERO")
    void otroRolRecibe403() throws Exception {
        mvc.perform(get("/api/deportistas")).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
}
