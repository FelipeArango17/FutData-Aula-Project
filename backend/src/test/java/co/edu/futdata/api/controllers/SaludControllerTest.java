package co.edu.futdata.api.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.persistence.dao.ISaludDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/health de punta a punta (controlador, servicio) con el DAO simulado: sin red. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class SaludControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ISaludDAO saludDAO;

    @Test
    void responde200ConLaBaseDeDatosArriba() throws Exception {
        when(saludDAO.baseDeDatosDisponible()).thenReturn(true);

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value("UP"))
                .andExpect(jsonPath("$.baseDeDatos").value("UP"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void responde503ConLaBaseDeDatosAbajo() throws Exception {
        when(saludDAO.baseDeDatosDisponible()).thenReturn(false);

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value("UP"))
                .andExpect(jsonPath("$.baseDeDatos").value("DOWN"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }
}
