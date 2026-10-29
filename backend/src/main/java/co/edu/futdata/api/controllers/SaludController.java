package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.SaludResponseDTO;
import co.edu.futdata.services.facade.ISaludService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado del backend y de su conexión a la base de datos. */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Salud", description = "Estado del backend")
public class SaludController {

    private final ISaludService saludService;

    public SaludController(ISaludService saludService) {
        this.saludService = saludService;
    }

    @GetMapping
    @Operation(summary = "Consultar el estado del backend y de la base de datos")
    @ApiResponse(responseCode = "200", description = "La aplicación y la base de datos responden")
    @ApiResponse(responseCode = "503", description = "La aplicación responde, pero la base de datos no")
    public ResponseEntity<SaludResponseDTO> consultar() {
        SaludResponseDTO salud = saludService.consultar();
        HttpStatus estado = salud.baseDeDatosDisponible() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(estado).body(salud);
    }
}
