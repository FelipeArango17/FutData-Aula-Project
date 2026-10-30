package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.IDeportistaService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class DeportistaControllerTest {

    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final String CUERPO = """
            {"nombre":"Ana","apellido":"Pérez","tipoDocumento":"TI","documento":"12345678",
             "fechaNacimiento":"2012-04-10","telefono":"3001234567","direccion":"Calle 10 # 20-30"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IDeportistaService servicio;

    @Test
    void registraYDevuelve201() throws Exception {
        when(servicio.crear(any())).thenReturn(new DeportistaResponseDTO(ID, "Ana", "Pérez", "TI",
                "12345678", LocalDate.of(2012, 4, 10), 14, "3001234567", "Calle 10 # 20-30", null));

        mvc.perform(post("/api/deportistas").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/deportistas/" + ID)))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.edad").value(14));
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
}
