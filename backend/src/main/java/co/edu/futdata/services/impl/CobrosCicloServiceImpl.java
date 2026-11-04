package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.ResultadoCiclosDTO;
import co.edu.futdata.config.CobrosCicloProperties;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.config.MensualidadProperties;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.Pago;
import co.edu.futdata.domain.entities.TipoPago;
import co.edu.futdata.domain.rules.CiclosDeCobro;
import co.edu.futdata.domain.rules.CiclosDeCobro.Ciclo;
import co.edu.futdata.domain.rules.ReglasPago;
import co.edu.futdata.persistence.dao.AcudienteConCorreo;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.DeportistaDeCiclo;
import co.edu.futdata.persistence.dao.IAuditoriaDAO;
import co.edu.futdata.persistence.dao.IPagoDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.ICobrosCicloService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cobros por ciclo de ingreso. Cada deportista se procesa en <b>su propia transacción</b> (con su fila bloqueada), así que el
 * fallo de uno no aborta a los demás ni deja cobros a medias. Los avisos por correo salen <b>después</b> del commit de ese
 * deportista: nunca se espera a n8n con una transacción abierta, y si n8n falla el cobro ya existe.
 */
@Service
public class CobrosCicloServiceImpl implements ICobrosCicloService {

    private static final Logger log = LoggerFactory.getLogger(CobrosCicloServiceImpl.class);

    private final IPagoDAO pagos;
    private final IAuditoriaDAO auditoria;
    private final INotificador notificador;
    private final MensualidadProperties mensualidad;
    private final CobrosCicloProperties cobros;
    private final FrontendProperties frontend;
    private final TransactionTemplate transaccion;

    public CobrosCicloServiceImpl(IPagoDAO pagos, IAuditoriaDAO auditoria, INotificador notificador, MensualidadProperties mensualidad,
                                  CobrosCicloProperties cobros, FrontendProperties frontend, PlatformTransactionManager administrador) {
        this.pagos = pagos;
        this.auditoria = auditoria;
        this.notificador = notificador;
        this.mensualidad = mensualidad;
        this.cobros = cobros;
        this.frontend = frontend;
        this.transaccion = new TransactionTemplate(administrador);
    }

    /** Lo que pasó con un deportista; {@code aviso} es la mensualidad anticipada que hay que avisar (puede ser null). */
    private record Procesado(int creadas, int yaExistentes, Aviso aviso) {
        static final Procesado NADA = new Procesado(0, 0, null);
    }

    private record Aviso(String nombreDeportista, String concepto, long monto, LocalDate fechaVencimiento, int diasRestantes) {
    }

    @Override
    public ResultadoCiclosDTO generarCobrosDeCiclo(LocalDate hoy, UUID idUsuario) {
        if (mensualidad.monto() <= 0) {
            // PENDIENTE: el monto real lo define el club (FUTDATA_MENSUALIDAD_MONTO)
            throw new ConflictoException("La mensualidad no está configurada: el club debe definir el monto (FUTDATA_MENSUALIDAD_MONTO).", List.of());
        }
        int creadas = 0;
        int existentes = 0;
        int enviados = 0;
        int fallidos = 0;
        int conError = 0;
        for (DeportistaDeCiclo deportista : pagos.deportistasParaCiclo()) {
            Procesado resultado;
            try {
                resultado = transaccion.execute(estado -> procesar(deportista, hoy, idUsuario));
            } catch (DatoDuplicadoException e) {
                // Otra ejecución (u otro camino) creó la mensualidad entre la revisión y el insert: ya existe
                existentes++;
                continue;
            } catch (RuntimeException e) {
                conError++;
                log.error("No se generaron los cobros del deportista {}: {}", deportista.id(), e.getClass().getSimpleName());
                continue;
            }
            creadas += resultado.creadas();
            existentes += resultado.yaExistentes();
            if (resultado.aviso() != null) {
                int[] avisos = avisar(deportista, resultado.aviso(), hoy);
                enviados += avisos[0];
                fallidos += avisos[1];
            }
        }
        ResultadoCiclosDTO total = new ResultadoCiclosDTO(creadas, existentes, enviados, fallidos, conError);
        registrarAuditoria(idUsuario, hoy, total);
        log.info("Cobros por ciclo de {}: {} creadas, {} ya existían, {} avisos enviados, {} fallidos, {} deportistas con error",
                hoy, creadas, existentes, enviados, fallidos, conError);
        return total;
    }

    private Procesado procesar(DeportistaDeCiclo d, LocalDate hoy, UUID idUsuario) {
        pagos.bloquearDeportista(d.id());
        Optional<Ciclo> vigente = CiclosDeCobro.cicloVigente(d.fechaRegistro(), hoy, cobros.diasAnticipacion());
        if (vigente.isEmpty()) {
            return Procesado.NADA; // registro a futuro: todavía no le toca nada
        }
        Ciclo ciclo = vigente.get();
        int creadas = 0;
        int existentes = 0;
        Aviso aviso = null;

        if (pagos.existeMensualidad(d.id(), ciclo.periodo())) { // en cualquier estado: un RECHAZADO a propósito no se regenera
            existentes++;
        } else {
            String concepto = ReglasPago.conceptoMensualidad(ciclo.periodo());
            LocalDate vence = CiclosDeCobro.vencimiento(ciclo.fechaCobro(), hoy);
            pagos.crear(nuevo(d.id(), TipoPago.MENSUALIDAD, concepto, mensualidad.monto(), ciclo.periodo(), vence, idUsuario));
            creadas++;
            if (ciclo.fechaCobro().isAfter(hoy)) { // cobro anticipado: se avisa
                aviso = new Aviso(d.nombre(), concepto, mensualidad.monto(), vence, (int) ChronoUnit.DAYS.between(hoy, vence));
            }
        }

        if (CiclosDeCobro.esIngresoReciente(d.fechaRegistro(), hoy, cobros.ventanaIngresoDias())) {
            int[] ingreso = cobrarIngreso(d.id(), TipoPago.INSCRIPCION, "Inscripción " + hoy.getYear(), cobros.inscripcionMonto(), hoy, idUsuario);
            int[] uniforme = cobrarIngreso(d.id(), TipoPago.UNIFORME, "Uniforme", cobros.uniformeMonto(), hoy, idUsuario);
            creadas += ingreso[0] + uniforme[0];
            existentes += ingreso[1] + uniforme[1];
        }
        return new Procesado(creadas, existentes, aviso);
    }

    /** @return {creados, yaExistentes}; con monto 0 el club no cobra ese concepto y no se crea nada */
    private int[] cobrarIngreso(UUID idDeportista, TipoPago tipo, String concepto, long monto, LocalDate hoy, UUID idUsuario) {
        if (monto <= 0) {
            return new int[] {0, 0};
        }
        if (pagos.existeCobroDeTipo(idDeportista, tipo)) {
            return new int[] {0, 1};
        }
        pagos.crear(nuevo(idDeportista, tipo, concepto, monto, null, hoy, idUsuario));
        return new int[] {1, 0};
    }

    private static Pago nuevo(UUID idDeportista, TipoPago tipo, String concepto, long monto, LocalDate periodo, LocalDate vence, UUID idUsuario) {
        return new Pago(null, idDeportista, tipo, concepto, monto, periodo, vence, EstadoPago.PENDIENTE, null, null, null, null, null, null,
                idUsuario, null, false, null);
    }

    /** @return {enviados, fallidos}. Un fallo de n8n (o de la configuración) no revierte ni bloquea nada. */
    private int[] avisar(DeportistaDeCiclo deportista, Aviso aviso, LocalDate hoy) {
        List<AcudienteConCorreo> destinatarios;
        try {
            destinatarios = pagos.acudientesConCorreo(deportista.id());
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer los acudientes del deportista {} para el aviso: {}", deportista.id(), e.getClass().getSimpleName());
            return new int[] {0, 1};
        }
        int enviados = 0;
        int fallidos = 0;
        for (AcudienteConCorreo acudiente : destinatarios) {
            try {
                notificador.notificar(INotificador.RECORDATORIO_PAGO, Map.of(
                        "correo", acudiente.correo(),
                        "nombreAcudiente", acudiente.nombre(),
                        "nombreDeportista", aviso.nombreDeportista(),
                        "concepto", aviso.concepto(),
                        "monto", aviso.monto(),
                        "fechaVencimiento", aviso.fechaVencimiento().toString(),
                        "diasRestantes", aviso.diasRestantes(),
                        "enlacePagos", frontend.enlacePagos()));
                enviados++;
            } catch (RuntimeException e) {
                fallidos++;
                // Sin el correo completo ni el mensaje de la excepción
                log.warn("Recordatorio de pago no enviado a {} (deportista {}): {}", enmascarar(acudiente.correo()), deportista.id(),
                        e.getClass().getSimpleName());
            }
        }
        return new int[] {enviados, fallidos};
    }

    /** {@code maria@gmail.com} -> {@code m***@gmail.com} */
    static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        return arroba <= 0 ? "***" : correo.charAt(0) + "***" + correo.substring(arroba);
    }

    private void registrarAuditoria(UUID idUsuario, LocalDate hoy, ResultadoCiclosDTO r) {
        try {
            auditoria.registrar(idUsuario, "COBROS_CICLO_GENERADOS", Map.of("fecha", hoy.toString(), "creadas", r.creadas(),
                    "yaExistentes", r.yaExistentes(), "avisosEnviados", r.avisosEnviados(), "avisosFallidos", r.avisosFallidos(),
                    "deportistasConError", r.deportistasConError()));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la auditoría de los cobros por ciclo: {}", e.getClass().getSimpleName());
        }
    }
}
