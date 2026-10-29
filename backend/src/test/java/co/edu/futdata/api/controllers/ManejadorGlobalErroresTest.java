package co.edu.futdata.api.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.ComprobanteInvalidoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Formato uniforme de errores. Usa un controlador que solo existe en las pruebas. */
@SpringBootTest
@Import(ManejadorGlobalErroresTest.ControladorDePrueba.class)
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class ManejadorGlobalErroresTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void validacionResponde400ConLosCamposEnEspanol() throws Exception {
        formatoUniforme(mvc.perform(post("/api/pruebas-errores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"edad\": 3}")), 400, "Bad Request", "/api/pruebas-errores")
                .andExpect(jsonPath("$.mensaje").value(
                        "edad: debe ser mayor que o igual a 5; nombre: no debe estar vacío"));
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        formatoUniforme(mvc.perform(post("/api/pruebas-errores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json")), 400, "Bad Request", "/api/pruebas-errores")
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición falta o no es un JSON válido"));
    }

    @Test
    void comprobanteInvalidoResponde400ConLosErrores() throws Exception {
        formatoUniforme(mvc.perform(get("/api/pruebas-errores/comprobante")), 400, "Bad Request", "/api/pruebas-errores/comprobante")
                .andExpect(jsonPath("$.mensaje").value(
                        "Los datos del comprobante no son válidos: concepto: es obligatorio; monto: debe ser mayor que 0"));
    }

    @Test
    void rutaInexistenteResponde404() throws Exception {
        formatoUniforme(mvc.perform(get("/api/no-existe")), 404, "Not Found", "/api/no-existe")
                .andExpect(jsonPath("$.mensaje").value("No existe la ruta GET /api/no-existe"));
    }

    @Test
    void recursoNoEncontradoResponde404ConSuMensaje() throws Exception {
        formatoUniforme(mvc.perform(get("/api/pruebas-errores/42")), 404, "Not Found", "/api/pruebas-errores/42")
                .andExpect(jsonPath("$.mensaje").value("No existe el recurso 42"));
    }

    @Test
    void metodoNoPermitidoResponde405() throws Exception {
        formatoUniforme(mvc.perform(post("/api/health")), 405, "Method Not Allowed", "/api/health")
                .andExpect(jsonPath("$.mensaje").value("El método POST no está permitido en esta ruta"));
    }

    @Test
    void errorInternoResponde500SinExponerLaTraza() throws Exception {
        formatoUniforme(mvc.perform(get("/api/pruebas-errores/falla")), 500, "Internal Server Error", "/api/pruebas-errores/falla")
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error interno. Intenta de nuevo más tarde."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("detalle secreto"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private ResultActions formatoUniforme(ResultActions respuesta, int estado, String error, String ruta) throws Exception {
        return respuesta
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.ruta").value(ruta));
    }

    record PruebaRequestDTO(@NotBlank String nombre, @Min(5) int edad) {
    }

    @RestController
    static class ControladorDePrueba {

        @PostMapping("/api/pruebas-errores")
        String crear(@Valid @RequestBody PruebaRequestDTO cuerpo) {
            return "ok";
        }

        @GetMapping("/api/pruebas-errores/falla")
        String fallar() {
            throw new IllegalStateException("detalle secreto que no debe salir");
        }

        @GetMapping("/api/pruebas-errores/comprobante")
        String comprobante() {
            throw new ComprobanteInvalidoException(List.of("monto: debe ser mayor que 0", "concepto: es obligatorio"));
        }

        @GetMapping("/api/pruebas-errores/{id}")
        String consultar(@PathVariable int id) {
            throw new RecursoNoEncontradoException("No existe el recurso " + id);
        }
    }
}
