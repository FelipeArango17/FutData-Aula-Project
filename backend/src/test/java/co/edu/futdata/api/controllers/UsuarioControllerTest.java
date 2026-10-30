package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.IUsuarioService;
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
class UsuarioControllerTest {
    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IUsuarioService servicio;

    @Test
    void creaUsuarioYDevuelve201() throws Exception {
        when(servicio.crear(any())).thenReturn(
                new UsuarioResponseDTO(ID, "Ana", "Pérez", "ana@pradofc.co", "acudiente", true, null));

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Ana","apellido":"Pérez","correo":"ana@pradofc.co","rol":"acudiente"}
                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/usuarios/" + ID)))
                .andExpect(jsonPath("$.rol").value("acudiente"));
    }

    @Test
    void correoDuplicadoDevuelve409ConCampo() throws Exception {
        when(servicio.crear(any())).thenThrow(new ConflictoException(
                "correo: ya está registrado en otro usuario", List.of("correo")));

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Ana","apellido":"Pérez","correo":"ana@pradofc.co","rol":"acudiente"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos[0]").value("correo"));
    }
}
