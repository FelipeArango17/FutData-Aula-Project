package co.edu.futdata.api.controllers;

import co.edu.futdata.api.dto.AprobarPagoRequestDTO;
import co.edu.futdata.api.dto.CrearPagoRequestDTO;
import co.edu.futdata.api.dto.ErrorResponseDTO;
import co.edu.futdata.api.dto.GenerarMensualidadesRequestDTO;
import co.edu.futdata.api.dto.IniciarWompiResponseDTO;
import co.edu.futdata.api.dto.MiPagoResponseDTO;
import co.edu.futdata.api.dto.PaginaResponseDTO;
import co.edu.futdata.api.dto.PagoResponseDTO;
import co.edu.futdata.api.dto.RechazarPagoRequestDTO;
import co.edu.futdata.api.dto.ResultadoCiclosDTO;
import co.edu.futdata.api.dto.ResultadoGeneracionDTO;
import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.TipoPago;
import co.edu.futdata.domain.rules.ReglasSemana;
import co.edu.futdata.persistence.dao.FiltroPagos;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.ICobrosCicloService;
import co.edu.futdata.services.facade.IPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Cobros y pagos en línea. El administrador y el tesorero ven todo; el acudiente solo lo de sus deportistas vinculados
 * (de otro recibe 404). El webhook de Wompi está en {@link WompiWebhookController}.
 */
@RestController
@RequestMapping("/api/pagos")
@Tag(name = "Pagos", description = "Cobros y pagos en línea con Wompi")
@ApiResponse(responseCode = "401", description = "Sin sesión o con un token inválido o vencido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "La cuenta no tiene el rol que pide la ruta",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public class PagoController {

    /** El mismo mensaje que /api/horario-semana. */
    private static final String FECHA_INVALIDA = "debe ser una fecha válida con el formato AAAA-MM-DD";

    private final IPagoService pagos;
    private final ICobrosCicloService ciclos;
    private final Clock reloj;

    public PagoController(IPagoService pagos, ICobrosCicloService ciclos, Clock reloj) {
        this.pagos = pagos;
        this.ciclos = ciclos;
        this.reloj = reloj;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear un cobro PENDIENTE")
    @ApiResponse(responseCode = "201", description = "Creado; la cabecera Location tiene su URL")
    @ApiResponse(responseCode = "409", description = "Ya existe esa mensualidad del deportista",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResponseEntity<PagoResponseDTO> crear(@AuthenticationPrincipal Jwt jwt, @RequestBody CrearPagoRequestDTO datos) {
        PagoResponseDTO creado = pagos.crear(usuario(jwt), datos);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'TESORERO')")
    @Operation(summary = "Listar los cobros (paginado)",
            description = "Filtros: estado, tipo, idDeportista, periodo (AAAA-MM), vencimientoDesde y vencimientoHasta "
                    + "(AAAA-MM-DD, ambos incluidos, sobre la fecha de vencimiento). Todos se combinan. Un formato "
                    + "inválido, o vencimientoDesde posterior a "
                    + "vencimientoHasta, responde 400. sort: campo,asc|desc con campo vencimiento, monto, creadoEn o estado")
    public PaginaResponseDTO<PagoResponseDTO> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) EstadoPago estado,
            @RequestParam(required = false) TipoPago tipo,
            @RequestParam(required = false) UUID idDeportista,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String vencimientoDesde,
            @RequestParam(required = false) String vencimientoHasta) {
        // Texto y no LocalDate: así el mensaje y los campos los controlamos nosotros, y todos los errores de formato
        // salen juntos en un solo 400
        Map<String, String> errores = new LinkedHashMap<>();
        LocalDate primerDia = null;
        if (periodo != null && !periodo.isBlank()) {
            try {
                primerDia = YearMonth.parse(periodo.strip()).atDay(1);
            } catch (DateTimeParseException e) {
                errores.put("periodo", "debe tener el formato AAAA-MM");
            }
        }
        LocalDate desde = fecha("vencimientoDesde", vencimientoDesde, errores);
        LocalDate hasta = fecha("vencimientoHasta", vencimientoHasta, errores);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            errores.put("vencimientoDesde", "no puede ser posterior a vencimientoHasta");
            errores.put("vencimientoHasta", "no puede ser anterior a vencimientoDesde");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return pagos.listar(new FiltroPagos(estado, tipo, idDeportista, primerDia, desde, hasta), sort, page, size);
    }

    /** {@code null} si el parámetro no vino o está en blanco; si no es una fecha real AAAA-MM-DD, anota el error. */
    private static LocalDate fecha(String campo, String texto, Map<String, String> errores) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return ReglasSemana.fecha(texto.strip()).orElseGet(() -> {
            errores.put(campo, FECHA_INVALIDA);
            return null;
        });
    }

    @GetMapping("/mis-pagos")
    @PreAuthorize("hasRole('ACUDIENTE')")
    @Operation(summary = "Los cobros de mis deportistas")
    public List<MiPagoResponseDTO> misPagos(@AuthenticationPrincipal Jwt jwt) {
        return pagos.misPagos(usuario(jwt));
    }

    @PostMapping("/{id}/iniciar-wompi")
    @PreAuthorize("hasRole('ACUDIENTE')")
    @Operation(summary = "Datos para abrir el checkout de Wompi",
            description = "Solo un cobro PENDIENTE de un deportista propio. El monto sale de la base de datos; reutiliza la referencia si ya la tiene")
    @ApiResponse(responseCode = "404", description = "No existe o no es de un deportista del acudiente",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "El cobro no está pendiente",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "PAGOS_NO_CONFIGURADOS",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public IniciarWompiResponseDTO iniciarWompi(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return pagos.iniciarWompi(usuario(jwt), id);
    }

    @PostMapping("/{id}/reintentar")
    @PreAuthorize("hasRole('ACUDIENTE')")
    @Operation(summary = "Reintentar un cobro rechazado",
            description = "Crea un cobro PENDIENTE nuevo con los mismos datos (o devuelve el que ya existe)")
    public MiPagoResponseDTO reintentar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return pagos.reintentar(usuario(jwt), id);
    }

    @PostMapping("/{id}/conciliar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'TESORERO')")
    @Operation(summary = "Conciliar con Wompi", description = "Consulta las transacciones de la referencia y aplica las reglas del webhook")
    public PagoResponseDTO conciliar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return pagos.conciliar(usuario(jwt), id);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'TESORERO')")
    @Operation(summary = "Aprobar un pago manual", description = "EFECTIVO o TRANSFERENCIA; exige fechaPago y metodo")
    public PagoResponseDTO aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody AprobarPagoRequestDTO datos) {
        return pagos.aprobar(usuario(jwt), id, datos);
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'TESORERO')")
    @Operation(summary = "Rechazar un cobro pendiente")
    public PagoResponseDTO rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @RequestBody(required = false) RechazarPagoRequestDTO datos) {
        return pagos.rechazar(usuario(jwt), id, datos);
    }

    @GetMapping("/{id}/comprobante")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'TESORERO', 'ACUDIENTE')")
    @Operation(summary = "Comprobante en PDF", description = "Solo de un cobro APROBADO; el acudiente, solo de sus deportistas")
    public ResponseEntity<byte[]> comprobante(@AuthenticationPrincipal Jwt jwt, Authentication autenticacion, @PathVariable UUID id) {
        boolean privilegiado = autenticacion.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR") || a.getAuthority().equals("ROLE_TESORERO"));
        byte[] pdf = pagos.comprobante(usuario(jwt), privilegiado, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"comprobante.pdf\"")
                .body(pdf);
    }

    /**
     * @deprecated Generación manual por calendario. La reemplaza {@link #generarCiclos}, que cobra por ciclo de ingreso de cada
     *             deportista. Se mantiene sin cambios mientras el frontend migra.
     */
    @Deprecated
    @PostMapping("/generar-mensualidades")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Generar las mensualidades de un periodo (obsoleto: usa generar-ciclos)", deprecated = true,
            description = "Crea la mensualidad PENDIENTE de cada deportista que no la tenga; idempotente. "
                    + "Obsoleto: los cobros se generan por ciclo de ingreso con POST /api/pagos/generar-ciclos")
    public ResultadoGeneracionDTO generarMensualidades(@AuthenticationPrincipal Jwt jwt, @RequestBody GenerarMensualidadesRequestDTO datos) {
        return pagos.generarMensualidades(usuario(jwt), datos == null ? null : datos.periodo());
    }

    @PostMapping("/generar-ciclos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CRON')")
    @Operation(summary = "Generar los cobros por ciclo de ingreso",
            description = "Crea la mensualidad del ciclo vigente de cada deportista activo (ancla: fecha de registro; se crea hasta "
                    + "5 días antes) y, a los ingresos recientes, la inscripción y el uniforme. Avisa por correo de los cobros "
                    + "anticipados. Idempotente. Lo llama el administrador (JWT) o la tarea diaria de n8n con la cabecera "
                    + "X-Cron-Token (solo si N8N_CRON_TOKEN está definida)")
    @ApiResponse(responseCode = "409", description = "La mensualidad no está configurada (FUTDATA_MENSUALIDAD_MONTO)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public ResultadoCiclosDTO generarCiclos(@AuthenticationPrincipal Jwt jwt) {
        // Con X-Cron-Token no hay JWT: quien actúa es el sistema (usuario null en la auditoría)
        return ciclos.generarCobrosDeCiclo(LocalDate.now(reloj), jwt == null ? null : usuario(jwt));
    }

    private static UUID usuario(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
