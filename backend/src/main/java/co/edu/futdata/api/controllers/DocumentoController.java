package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.DescargaDocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentosDeportistaResponseDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.RechazarDocumentoRequestDTO;
import co.edu.futdata.api.dto.RechazoDocumentoResponseDTO;
import co.edu.futdata.api.dto.RevisionDocumentoResponseDTO;
import co.edu.futdata.services.facade.IDocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos de la inscripción de un deportista. Son documentos de identidad de menores: solo el ADMINISTRADOR y el
 * ACUDIENTE dueño del deportista los ven (el entrenador y el tesorero reciben {@code 403} por rol). La autorización
 * fina la decide el servicio; un deportista o documento de otro acudiente responde {@code 404}, igual que uno que no
 * existe. Aprobar y rechazar son solo del ADMINISTRADOR.
 */
@RestController
@Tag(name = "Documentos", description = "Documentos de la inscripción (administrador y acudiente dueño)")
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ADMINISTRADOR ni ACUDIENTE, o no completó el 2FA",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "404", description = "No existe, o no es de un jugador del acudiente (el mismo mensaje en los dos casos)",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class DocumentoController {

    private final IDocumentoService documentos;

    public DocumentoController(IDocumentoService documentos) {
        this.documentos = documentos;
    }

    @PostMapping(value = "/api/deportistas/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ACUDIENTE')")
    @Operation(summary = "Subir un documento",
            description = "Multipart con 'tipo' (uno de los 7 tipos), 'archivo' y 'fechaVencimiento' opcional (AAAA-MM-DD, no "
                    + "pasada). PDF, JPG o PNG de hasta 5 MB; se revisa el contenido, no la extensión ni el Content-Type. "
                    + "El documento queda PENDIENTE.")
    @ApiResponse(responseCode = "201", description = "Documento guardado")
    @ApiResponse(responseCode = "400", description = "Tipo, archivo o fecha inválidos (todos los errores juntos)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Ya hay un documento PENDIENTE o APROBADO de ese tipo",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Supabase Storage no está disponible",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<DocumentoResponseDTO> subir(
            @PathVariable UUID id,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "fechaVencimiento", required = false) String fechaVencimiento,
            @AuthenticationPrincipal Jwt jwt, Authentication autenticacion) throws IOException {
        var documento = documentos.subir(Solicitantes.de(jwt, autenticacion), id, tipo, archivo.getOriginalFilename(),
                archivo.getBytes(), fechaVencimiento);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentoResponseDTO.de(documento));
    }

    @GetMapping("/api/deportistas/{id}/documentos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ACUDIENTE')")
    @Operation(summary = "Documentos de un deportista",
            description = "Del más reciente al más antiguo, con el estado y el comentario de rechazo de cada uno, más "
                    + "estadoInscripcion (COMPLETA o PENDIENTE) y los documentos obligatorios que faltan por aprobar.")
    public DocumentosDeportistaResponseDTO listar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                                  Authentication autenticacion) {
        return documentos.listar(Solicitantes.de(jwt, autenticacion), id);
    }

    @GetMapping("/api/documentos/{id}/descarga")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ACUDIENTE')")
    @Operation(summary = "Descargar un documento",
            description = "Devuelve una URL firmada de vida corta (vigenciaSegundos). Se pide solo al descargar: no se "
                    + "guarda ni se comparte.")
    public ResponseEntity<DescargaDocumentoResponseDTO> descargar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                                                  Authentication autenticacion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(documentos.descargar(Solicitantes.de(jwt, autenticacion), id));
    }

    @PostMapping("/api/documentos/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Aprobar un documento",
            description = "Solo el administrador, sin comentario. Devuelve el documento y el estadoInscripcion del deportista "
                    + "después del cambio.")
    @ApiResponse(responseCode = "200", description = "Documento aprobado")
    @ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ADMINISTRADOR, o no completó el 2FA",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "El documento no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "El documento ya no está PENDIENTE (ya fue revisado)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public RevisionDocumentoResponseDTO aprobar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                                Authentication autenticacion) {
        return documentos.aprobar(Solicitantes.de(jwt, autenticacion), id);
    }

    @PostMapping("/api/documentos/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Rechazar un documento",
            description = "Solo el administrador. Body { comentario } obligatorio, de 1 a 300 caracteres (se guarda recortado). "
                    + "Devuelve el documento, el estadoInscripcion del deportista y avisoEnviado: después de confirmar el rechazo "
                    + "se avisa por correo a los acudientes (true = n8n aceptó el aviso; false = no se pudo avisar, el rechazo "
                    + "queda hecho igual; null = no hay acudientes con correo). Un documento ya aprobado no se puede rechazar (409).")
    @ApiResponse(responseCode = "200", description = "Documento rechazado; avisoEnviado dice si se avisó a los acudientes")
    @ApiResponse(responseCode = "400", description = "Comentario ausente, vacío o de más de 300 caracteres (campos: comentario)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol ADMINISTRADOR, o no completó el 2FA",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "El documento no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "El documento ya no está PENDIENTE (ya fue revisado, aprobado o rechazado)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public RechazoDocumentoResponseDTO rechazar(@PathVariable UUID id,
                                                @RequestBody(required = false) RechazarDocumentoRequestDTO datos,
                                                @AuthenticationPrincipal Jwt jwt, Authentication autenticacion) {
        // Sin cuerpo o sin comentario: el servicio responde 400 con el campo "comentario" (no un 500)
        return documentos.rechazar(Solicitantes.de(jwt, autenticacion), id, datos == null ? null : datos.comentario());
    }
}
