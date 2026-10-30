package co.edu.futdata.api.controllers;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.ComprobanteInvalidoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
                        "edad: debe ser mayor que o igual a 5; nombre: no debe estar vacío"))
                .andExpect(jsonPath("$.campos").doesNotExist());
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        formatoUniforme(mvc.perform(post("/api/pruebas-errores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json")), 400, "Bad Request", "/api/pruebas-errores")
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición falta o no es un JSON válido"));
    }

    @Test
    void valorConFormatoInvalidoResponde400NombrandoElCampoSinRepetirElValor() throws Exception {
        formatoUniforme(mvc.perform(post("/api/pruebas-errores/fecha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\": \"14/05/1990\"}")), 400, "Bad Request", "/api/pruebas-errores/fecha")
                .andExpect(jsonPath("$.mensaje").value("fecha: tiene un valor o un formato inválido"))
                .andExpect(jsonPath("$.campos[0]").value("fecha"))
                .andExpect(content().string(not(containsString("14/05/1990"))));
    }

    @Test
    void datosInvalidosDelServicioResponde400ConLosCamposOrdenados() throws Exception {
        formatoUniforme(mvc.perform(get("/api/pruebas-errores/datos")), 400, "Bad Request", "/api/pruebas-errores/datos")
                .andExpect(jsonPath("$.mensaje").value("correo: no es válido; documento: debe tener entre 6 y 12 dígitos"))
                .andExpect(jsonPath("$.campos").value(contains("correo", "documento")));
    }

    @Test
    void conflictoResponde409ConSuMensajeYLosCampos() throws Exception {
        formatoUniforme(mvc.perform(get("/api/pruebas-errores/conflicto")), 409, "Conflict", "/api/pruebas-errores/conflicto")
                .andExpect(jsonPath("$.mensaje").value("documento: ya está registrado en otro entrenador"))
                .andExpect(jsonPath("$.campos").value(contains("documento")));
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

    record FechaRequestDTO(LocalDate fecha) {
    }

    @RestController
    static class ControladorDePrueba {

        @PostMapping("/api/pruebas-errores")
        String crear(@Valid @RequestBody PruebaRequestDTO cuerpo) {
            return "ok";
        }

        @PostMapping("/api/pruebas-errores/fecha")
        String fecha(@RequestBody FechaRequestDTO cuerpo) {
            return "ok";
        }

        @GetMapping("/api/pruebas-errores/datos")
        String datos() {
            throw new DatosInvalidosException(Map.of("documento", "debe tener entre 6 y 12 dígitos", "correo", "no es válido"));
        }

        @GetMapping("/api/pruebas-errores/conflicto")
        String conflicto() {
            throw new ConflictoException("documento: ya está registrado en otro entrenador", List.of("documento"));
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
