package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.services.facade.IPagoWompiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de eventos de Wompi. <b>Pública</b> (sin JWT; ver {@code RutasSin2FA}): la autenticidad la da la firma del
 * evento, que se verifica antes de usar nada del cuerpo. Con firma válida siempre responde 200: otro código hace que Wompi
 * reintente (a los 30 min, 3 h y 24 h).
 */
@RestController
@RequestMapping("/api/pagos/webhook/wompi")
@Tag(name = "Pagos", description = "Cobros y pagos en línea con Wompi")
public class WompiWebhookController {

    static final String CABECERA_CHECKSUM = "X-Event-Checksum";

    private final IPagoWompiService servicio;

    public WompiWebhookController(IPagoWompiService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @Operation(summary = "Evento de Wompi (transaction.updated)", description = "Público: se valida la firma del evento")
    @ApiResponse(responseCode = "200", description = "Evento recibido (también si se ignoró)")
    @ApiResponse(responseCode = "401", description = "Firma inválida o ausente",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "PAGOS_NO_CONFIGURADOS: falta el secreto de eventos",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<Void> recibir(@RequestBody(required = false) String cuerpo,
                                        @RequestHeader(name = CABECERA_CHECKSUM, required = false) String checksum) {
        servicio.procesarEvento(cuerpo, checksum);
        return ResponseEntity.ok().build();
    }
}
