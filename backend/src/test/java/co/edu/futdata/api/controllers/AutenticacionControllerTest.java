package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.services.facade.CodigoVerificacionException;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import java.util.List;
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
class AutenticacionControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ICodigoVerificacionService servicio;

    @Test
    void solicitarDevuelve202SinExponerElCodigo() throws Exception {
        when(servicio.solicitar(anyString())).thenReturn(new Codigo2FAResponseDTO("a***@pradofc.co", 600, 60_000));

        mvc.perform(post("/api/auth/2fa/codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"ana@pradofc.co\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correoEnmascarado").value("a***@pradofc.co"))
                .andExpect(jsonPath("$.vigenciaSegundos").value(600))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    void reenvioLimitadoDevuelve409ConCampoCodigo() throws Exception {
        when(servicio.reenviar(anyString())).thenThrow(new CodigoVerificacionException(
                "DEMASIADO_PRONTO", "Debes esperar antes de solicitar otro código.", 30_000L));

        mvc.perform(post("/api/auth/2fa/reenviar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"ana@pradofc.co\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos").isArray())
                .andExpect(jsonPath("$.campos[0]").value("codigo"));
    }
}
