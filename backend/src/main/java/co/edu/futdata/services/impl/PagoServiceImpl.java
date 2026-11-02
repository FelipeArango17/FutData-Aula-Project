package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.AprobarPagoRequestDTO;
import co.edu.futdata.api.dto.CrearPagoRequestDTO;
import co.edu.futdata.api.dto.IniciarWompiResponseDTO;
import co.edu.futdata.api.dto.MiPagoResponseDTO;
import co.edu.futdata.api.dto.PaginaResponseDTO;
import co.edu.futdata.api.dto.PagoMapper;
import co.edu.futdata.api.dto.PagoResponseDTO;
import co.edu.futdata.api.dto.RechazarPagoRequestDTO;
import co.edu.futdata.api.dto.ResultadoGeneracionDTO;
import co.edu.futdata.config.MensualidadProperties;
import co.edu.futdata.config.RelojConfig;
import co.edu.futdata.config.WompiProperties;
import co.edu.futdata.datasource.external.IWompiClient;
import co.edu.futdata.datasource.external.TransaccionWompi;
import co.edu.futdata.datasource.external.WompiFirmas;
import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.Pago;
import co.edu.futdata.domain.entities.TipoPago;
import co.edu.futdata.domain.rules.ReglasPago;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.FiltroPagos;
import co.edu.futdata.persistence.dao.IAuditoriaDAO;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.IPagoDAO;
import co.edu.futdata.persistence.dao.PaginaPagos;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IPagoService;
import co.edu.futdata.services.facade.PagosNoConfiguradosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.ComprobantePagoDTO;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import co.edu.futdata.util.reports.IGeneradorComprobante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cobros y pagos en línea. Cada operación que cambia un cobro lo toma con {@code FOR UPDATE} dentro de una transacción
 * ({@link TransactionTemplate}); las que hablan con Wompi (conciliar) consultan <b>antes</b> de abrirla, para no tener una
 * conexión y un bloqueo abiertos mientras se espera la red.
 */
@Service
public class PagoServiceImpl implements IPagoService {

    static final String NO_EXISTE = "No existe un pago con ese id.";
    static final int TAMANO_MAXIMO = 100;
    /** Campo de la API -> columna. Lista fija: el orden nunca se arma con texto de la petición. */
    private static final Map<String, String> ORDENES = Map.of(
            "vencimiento", "p.fecha_vencimiento", "monto", "p.monto", "creadoEn", "p.creado_en", "estado", "p.estado_pago");

    private static final Logger log = LoggerFactory.getLogger(PagoServiceImpl.class);

    private final IPagoDAO pagos;
    private final IDeportistaDAO deportistas;
    private final IAuditoriaDAO auditoria;
    private final IWompiClient clienteWompi;
    private final AplicadorTransaccionWompi aplicador;
    private final IGeneradorComprobante generador;
    private final WompiProperties wompi;
    private final MensualidadProperties mensualidad;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public PagoServiceImpl(IPagoDAO pagos, IDeportistaDAO deportistas, IAuditoriaDAO auditoria, IWompiClient clienteWompi,
                           AplicadorTransaccionWompi aplicador, IGeneradorComprobante generador, WompiProperties wompi,
                           MensualidadProperties mensualidad, Clock reloj, PlatformTransactionManager administrador) {
        this.pagos = pagos;
        this.deportistas = deportistas;
        this.auditoria = auditoria;
        this.clienteWompi = clienteWompi;
        this.aplicador = aplicador;
        this.generador = generador;
        this.wompi = wompi;
        this.mensualidad = mensualidad;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(administrador);
    }

    // --- Administrador / tesorero --------------------------------------------------------------------------

    @Override
    public PagoResponseDTO crear(UUID idUsuario, CrearPagoRequestDTO datos) {
        Pago nuevo = validarNuevo(datos, idUsuario);
        if (deportistas.buscarPorId(nuevo.idDeportista()).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe un deportista con ese id.");
        }
        UUID id = crearSinDuplicar(nuevo);
        return PagoMapper.toDTO(buscar(id));
    }

    private Pago validarNuevo(CrearPagoRequestDTO d, UUID idUsuario) {
        if (d == null) {
            throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        }
        Map<String, String> errores = new LinkedHashMap<>();
        if (d.idDeportista() == null) {
            errores.put("idDeportista", "es obligatorio");
        }
        if (d.tipo() == null) {
            errores.put("tipo", "es obligatorio (MENSUALIDAD, INSCRIPCION, TORNEO o UNIFORME)");
        }
        if (d.monto() == null || d.monto() <= 0 || d.monto() > ReglasPago.MONTO_MAXIMO) {
            errores.put("monto", "debe ser un número entero de pesos mayor que 0");
        }
        if (d.fechaVencimiento() == null) {
            errores.put("fechaVencimiento", "es obligatoria");
        }
        LocalDate periodo = d.periodo() == null ? null : ReglasPago.primerDiaDelMes(d.periodo());
        if (d.tipo() == TipoPago.MENSUALIDAD && periodo == null) {
            errores.put("periodo", "es obligatorio en una mensualidad");
        }
        String concepto = d.concepto() == null ? "" : d.concepto().strip();
        if (concepto.isEmpty() && d.tipo() == TipoPago.MENSUALIDAD && periodo != null) {
            concepto = ReglasPago.conceptoMensualidad(periodo);
        }
        if (concepto.isEmpty()) {
            errores.put("concepto", "es obligatorio");
        } else if (concepto.length() > ReglasPago.MAX_CONCEPTO) {
            errores.put("concepto", "debe tener máximo " + ReglasPago.MAX_CONCEPTO + " caracteres");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return new Pago(null, d.idDeportista(), d.tipo(), concepto, d.monto(), periodo, d.fechaVencimiento(), EstadoPago.PENDIENTE,
                null, null, null, null, null, null, idUsuario, null, false, null);
    }

    private UUID crearSinDuplicar(Pago nuevo) {
        if (nuevo.tipo() == TipoPago.MENSUALIDAD && pagos.existeMensualidadNoRechazada(nuevo.idDeportista(), nuevo.periodo())) {
            throw mensualidadRepetida();
        }
        try {
            return pagos.crear(nuevo);
        } catch (DatoDuplicadoException e) {
            throw mensualidadRepetida();
        }
    }

    private static ConflictoException mensualidadRepetida() {
        return new ConflictoException("periodo: el deportista ya tiene una mensualidad de ese periodo", List.of("periodo"));
    }

    @Override
    public PaginaResponseDTO<PagoResponseDTO> listar(FiltroPagos filtro, String sort, int page, int size) {
        if (page < 0 || size < 1 || size > TAMANO_MAXIMO) {
            throw new DatosInvalidosException(Map.of("page", "debe ser 0 o más", "size", "debe estar entre 1 y " + TAMANO_MAXIMO));
        }
        String[] partes = (sort == null || sort.isBlank() ? "vencimiento,desc" : sort).split(",");
        String columna = ORDENES.get(partes[0].strip());
        boolean asc = partes.length > 1 && "asc".equalsIgnoreCase(partes[1].strip());
        if (columna == null || (partes.length > 1 && !asc && !"desc".equalsIgnoreCase(partes[1].strip())) || partes.length > 2) {
            throw DatosInvalidosException.de("sort", "debe ser campo,asc|desc con campo vencimiento, monto, creadoEn o estado");
        }
        PaginaPagos pagina = pagos.listar(filtro, columna, asc, page, size);
        return new PaginaResponseDTO<>(pagina.contenido().stream().map(PagoMapper::toDTO).toList(), pagina.total(), page, size);
    }

    @Override
    public PagoResponseDTO aprobar(UUID idUsuario, UUID idPago, AprobarPagoRequestDTO datos) {
        Map<String, String> errores = new LinkedHashMap<>();
        LocalDate hoy = LocalDate.now(reloj);
        if (datos == null || datos.fechaPago() == null) {
            errores.put("fechaPago", "es obligatoria");
        } else if (datos.fechaPago().isAfter(hoy)) {
            errores.put("fechaPago", "no puede ser posterior a hoy");
        }
        if (datos == null || datos.metodo() == null) {
            errores.put("metodo", "es obligatorio");
        } else if (datos.metodo().enLinea()) {
            errores.put("metodo", "solo EFECTIVO o TRANSFERENCIA; los pagos en línea los confirma Wompi");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return transaccion.execute(estado -> {
            Pago pago = bloquear(idPago);
            if (pago.estado() == EstadoPago.APROBADO) {
                throw new ConflictoException("estado: el pago ya está aprobado", List.of("estado"));
            }
            if (pago.referenciaWompi() != null) {
                // Con una referencia de Wompi el cobro puede recibir un pago en línea: aprobarlo a mano lo dejaría sin enlace
                throw new ConflictoException("estado: el cobro ya se inició en Wompi; concílialo o recházalo antes de registrarlo como pago manual", List.of("estado"));
            }
            if (pagos.hayHermanoAprobado(pago)) {
                throw new ConflictoException("estado: ya hay otro cobro equivalente aprobado", List.of("estado"));
            }
            pagos.aprobar(idPago, datos.fechaPago(), datos.metodo(), null, pagos.siguienteNumeroComprobante(), idUsuario, datos.nota());
            auditoria.registrar(idUsuario, "PAGO_APROBADO_MANUAL", Map.of("idPago", idPago.toString(), "metodo", datos.metodo().name(),
                    "estadoAnterior", pago.estado().name()));
            return PagoMapper.toDTO(buscar(idPago));
        });
    }

    @Override
    public PagoResponseDTO rechazar(UUID idUsuario, UUID idPago, RechazarPagoRequestDTO datos) {
        return transaccion.execute(estado -> {
            Pago pago = bloquear(idPago);
            if (pago.estado() != EstadoPago.PENDIENTE) {
                throw new ConflictoException("estado: solo se puede rechazar un cobro pendiente", List.of("estado"));
            }
            String nota = datos == null || datos.nota() == null || datos.nota().isBlank() ? "Rechazado manualmente" : datos.nota().strip();
            pagos.rechazar(idPago, nota, idUsuario);
            auditoria.registrar(idUsuario, "PAGO_RECHAZADO_MANUAL", Map.of("idPago", idPago.toString()));
            return PagoMapper.toDTO(buscar(idPago));
        });
    }

    @Override
    public PagoResponseDTO conciliar(UUID idUsuario, UUID idPago) {
        exigirWompi();
        Pago pago = buscar(idPago);
        if (pago.referenciaWompi() == null) {
            throw new ConflictoException("estado: el cobro todavía no se inició en Wompi", List.of("estado"));
        }
        List<TransaccionWompi> transacciones = clienteWompi.transaccionesDe(pago.referenciaWompi()); // WompiNoDisponibleException -> 503
        // Las reglas son las del webhook, sobre lo que Wompi dice hoy; nunca se confía en montos del cliente.
        return transaccion.execute(estado -> {
            for (TransaccionWompi t : transacciones) {
                if (!pago.referenciaWompi().equals(t.referencia())) {
                    continue;
                }
                Pago actual = bloquear(idPago);
                log.info("Conciliación del cobro {}: {} -> {}", idPago, t.estado(), aplicador.aplicar(actual, t));
            }
            auditoria.registrar(idUsuario, "PAGO_CONCILIADO", Map.of("idPago", idPago.toString(), "transacciones", transacciones.size()));
            return PagoMapper.toDTO(buscar(idPago));
        });
    }

    // --- Acudiente -----------------------------------------------------------------------------------------

    @Override
    public List<MiPagoResponseDTO> misPagos(UUID idUsuario) {
        return pagos.listarDeAcudiente(idUsuario).stream().map(PagoMapper::toMiDTO).toList();
    }

    @Override
    public IniciarWompiResponseDTO iniciarWompi(UUID idUsuario, UUID idPago) {
        exigirWompi();
        return transaccion.execute(estado -> {
            Pago pago = propio(idUsuario, idPago);
            if (pago.estado() != EstadoPago.PENDIENTE) {
                throw new ConflictoException("estado: solo se puede pagar un cobro pendiente", List.of("estado"));
            }
            // La referencia se reutiliza: el reintento del checkout de Wompi usa la misma
            String referencia = pago.referenciaWompi();
            if (referencia == null) {
                referencia = ReglasPago.referenciaDe(idPago);
                pagos.asignarReferenciaWompi(idPago, referencia);
            }
            long centavos = ReglasPago.aCentavos(pago.monto()); // siempre el monto de la base de datos
            String redireccion = wompi.redirectUrl() == null || wompi.redirectUrl().isBlank() ? null : wompi.redirectUrl().strip();
            return new IniciarWompiResponseDTO(wompi.publicKey(), referencia, centavos, ReglasPago.MONEDA,
                    WompiFirmas.firmaIntegridad(referencia, centavos, ReglasPago.MONEDA, wompi.integritySecret()), redireccion);
        });
    }

    @Override
    public MiPagoResponseDTO reintentar(UUID idUsuario, UUID idPago) {
        return transaccion.execute(estado -> {
            Pago rechazado = propio(idUsuario, idPago);
            if (rechazado.estado() != EstadoPago.RECHAZADO) {
                throw new ConflictoException("estado: solo se puede reintentar un cobro rechazado", List.of("estado"));
            }
            var existente = pagos.pendienteEquivalente(rechazado);
            if (existente.isPresent()) {
                return PagoMapper.toMiDTO(existente.get()); // idempotente
            }
            if (pagos.hayHermanoAprobado(rechazado)) {
                throw new ConflictoException("estado: ya hay otro cobro equivalente aprobado", List.of("estado"));
            }
            UUID nuevo = pagos.crear(new Pago(null, rechazado.idDeportista(), rechazado.tipo(), rechazado.concepto(), rechazado.monto(),
                    rechazado.periodo(), rechazado.fechaVencimiento(), EstadoPago.PENDIENTE, null, null, null, null, null, null,
                    idUsuario, null, false, null));
            return PagoMapper.toMiDTO(buscar(nuevo));
        });
    }

    @Override
    public byte[] comprobante(UUID idUsuario, boolean privilegiado, UUID idPago) {
        Pago pago = privilegiado ? buscar(idPago) : propio(idUsuario, idPago);
        if (pago.estado() != EstadoPago.APROBADO || !pago.tieneComprobante()) {
            throw new ConflictoException("estado: el comprobante solo existe para pagos aprobados", List.of("estado"));
        }
        // La fecha de emisión es el día del pago (no cambia entre descargas); el generador la imprime en hora de Colombia
        Instant emision = pago.fechaPago().atTime(12, 0).atZone(RelojConfig.ZONA_COLOMBIA).toInstant();
        return generador.generar(new ComprobantePagoDTO(pago.numeroComprobante(), emision, pago.nombreDeportista(),
                pagos.categoriaDeDeportista(pago.idDeportista()).orElse(null),
                pagos.nombreAcudiente(pago.idDeportista(), privilegiado ? null : idUsuario).orElse("Sin acudiente registrado"),
                pago.concepto(), pago.monto(), pago.fechaPago(), pago.metodo(), pago.metodo().enLinea() ? pago.referenciaWompi() : null));
    }

    // --- Mensualidades -------------------------------------------------------------------------------------

    @Override
    public ResultadoGeneracionDTO generarMensualidades(UUID idUsuario, YearMonth periodo) {
        if (periodo == null) {
            throw DatosInvalidosException.de("periodo", "es obligatorio (AAAA-MM)");
        }
        if (mensualidad.monto() <= 0) {
            // PENDIENTE: el monto real lo define el club (FUTDATA_MENSUALIDAD_MONTO)
            throw new ConflictoException("La mensualidad no está configurada: el club debe definir el monto (FUTDATA_MENSUALIDAD_MONTO).", List.of());
        }
        LocalDate primero = periodo.atDay(1);
        LocalDate vence = ReglasPago.vencimientoMensualidad(primero, mensualidad.diaVencimiento());
        String concepto = ReglasPago.conceptoMensualidad(primero);
        int creadas = 0;
        int existentes = 0;
        for (UUID idDeportista : pagos.idsDeportistas()) {
            if (pagos.existeMensualidadNoRechazada(idDeportista, primero)) {
                existentes++;
                continue;
            }
            try { // cada inserción es su propia transacción: un duplicado concurrente no aborta a las demás
                pagos.crear(new Pago(null, idDeportista, TipoPago.MENSUALIDAD, concepto, mensualidad.monto(), primero, vence,
                        EstadoPago.PENDIENTE, null, null, null, null, null, null, idUsuario, null, false, null));
                creadas++;
            } catch (DatoDuplicadoException e) {
                existentes++;
            }
        }
        auditoria.registrar(idUsuario, "MENSUALIDADES_GENERADAS", Map.of("periodo", periodo.toString(), "creadas", creadas, "yaExistentes", existentes));
        return new ResultadoGeneracionDTO(periodo.toString(), creadas, existentes);
    }

    // --- Ayudas --------------------------------------------------------------------------------------------

    private void exigirWompi() {
        if (!wompi.configurado()) {
            log.error("Los pagos con Wompi no están configurados: faltan llaves o no son del ambiente de WOMPI_BASE_URL");
            throw new PagosNoConfiguradosException();
        }
    }

    private Pago buscar(UUID id) {
        return pagos.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
    }

    private Pago bloquear(UUID id) {
        return pagos.buscarPorIdParaActualizar(id).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
    }

    /** Un cobro de un deportista del acudiente (tomado para actualizar); de otro, 404: no se revela que existe. */
    private Pago propio(UUID idUsuario, UUID idPago) {
        Pago pago = pagos.buscarPorIdParaActualizar(idPago).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
        if (!pagos.esAcudienteDe(idUsuario, pago.idDeportista())) {
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        return pago;
    }
}
