package co.edu.futdata.api.controllers;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.services.facade.CrearContrasenaNoDisponibleException;
import co.edu.futdata.services.facade.DemasiadasSolicitudesException;
import co.edu.futdata.services.facade.EnlaceInvalidoException;
import co.edu.futdata.services.facade.IContrasenaService;
import co.edu.futdata.services.facade.PoliticaContrasenaException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /api/auth/crear-contrasena por HTTP, sin sesión, con la fachada simulada. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class ContrasenaControllerTest {

    private static final String RUTA = "/api/auth/crear-contrasena";
    private static final String CUERPO = "{\"token\": \"hash-inventado-7c1e\", \"contrasena\": \"Inventada2026\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IContrasenaService servicio;

    private ResultActions enviar() throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                .header("CF-Connecting-IP", "190.24.137.58")
                .header("X-Forwarded-For", "1.2.3.4"));
    }

    @Test
    void creaLaContrasenaSinTokensNiSesionEnLaRespuesta() throws Exception {
        enviar().andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Tu contraseña quedó creada. Ya puedes iniciar sesión."))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
        // La IP sale de Cloudflare, no de X-Forwarded-For
        verify(servicio).crearContrasena("hash-inventado-7c1e", "Inventada2026", "190.24.137.58");
    }

    @Test
    void laPoliticaEs400ConElMensajeTalCualYCampos() throws Exception {
        doThrow(new PoliticaContrasenaException()).when(servicio).crearContrasena(anyString(), anyString(), anyString());

        enviar().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número."))
                .andExpect(jsonPath("$.campos").value(contains("contrasena")));
    }

    @Test
    void elEnlaceInvalidoEs400GenericoSinCampos() throws Exception {
        doThrow(new EnlaceInvalidoException()).when(servicio).crearContrasena(any(), any(), any());

        enviar().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El enlace no es válido o venció; pide al administrador que te lo reenvíe"))
                .andExpect(jsonPath("$.campos").doesNotExist());
    }

    @Test
    void laFallaDeSupabaseEs503() throws Exception {
        doThrow(new CrearContrasenaNoDisponibleException()).when(servicio).crearContrasena(any(), any(), any());

        enviar().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Inténtalo de nuevo; si persiste, pide que te reenvíen el enlace."));
    }

    @Test
    void demasiadosIntentosEs429ConRetryAfter() throws Exception {
        doThrow(new DemasiadasSolicitudesException("Demasiados intentos.", Duration.ofSeconds(42)))
                .when(servicio).crearContrasena(any(), any(), any());

        enviar().andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"));
    }
}
