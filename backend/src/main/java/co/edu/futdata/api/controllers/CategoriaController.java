package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.services.facade.IEntrenadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Categorías del club con su entrenador, para la pantalla de entrenadores (HU-025). */
// TODO HU-005: solo ADMINISTRADOR y ENTRENADOR
@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorías", description = "Categorías por edad y el entrenador que dirige cada una")
public class CategoriaController {

    private final IEntrenadorService entrenadores;

    public CategoriaController(IEntrenadorService entrenadores) {
        this.entrenadores = entrenadores;
    }

    @GetMapping
    @Operation(summary = "Listar las categorías", description = "En el orden oficial (Sub-8 a Sub-16), con su entrenador")
    @ApiResponse(responseCode = "200", description = "Lista; vacía mientras no se creen las categorías en la base de datos")
    public List<CategoriaResponseDTO> listar() {
        return entrenadores.listarCategorias();
    }
}
