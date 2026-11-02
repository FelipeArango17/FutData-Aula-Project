package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Pago;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la tabla {@code pago} (y a la vista {@code pago_con_estado}). Las escrituras que cambian el estado deben
 * llamarse dentro de una transacción que haya tomado el cobro con {@link #buscarPorIdParaActualizar}.
 */
public interface IPagoDAO {

    /**
     * Crea un cobro PENDIENTE.
     *
     * @throws DatoDuplicadoException ({@code mensualidad}) ya hay una mensualidad no rechazada de ese deportista y periodo
     */
    UUID crear(Pago pago);

    Optional<Pago> buscarPorId(UUID id);

    /** Igual, con {@code SELECT ... FOR UPDATE}: serializa los eventos de Wompi y las acciones del tesorero sobre el cobro. */
    Optional<Pago> buscarPorIdParaActualizar(UUID id);

    Optional<Pago> buscarPorReferenciaParaActualizar(String referenciaWompi);

    /** Orden por {@code columnaOrden} (ya validada contra una lista fija) y página; {@code page} desde 0. */
    PaginaPagos listar(FiltroPagos filtro, String columnaOrden, boolean ascendente, int page, int size);

    /** Los cobros de los deportistas vinculados al usuario acudiente, más recientes primero. */
    List<Pago> listarDeAcudiente(UUID idUsuario);

    boolean esAcudienteDe(UUID idUsuario, UUID idDeportista);

    /** Los acudientes (id_acudiente) del deportista, para los avisos. */
    List<UUID> acudientesDe(UUID idDeportista);

    /** Todos los deportistas (no hay columna de estado en el modelo). */
    List<UUID> idsDeportistas();

    boolean existeMensualidadNoRechazada(UUID idDeportista, LocalDate periodo);

    /** Cobros PENDIENTES del mismo deportista y tipo, con el mismo periodo (mensualidad) o concepto, distintos de {@code pago}. */
    List<Pago> hermanosPendientes(Pago pago);

    boolean hayHermanoAprobado(Pago pago);

    /** Un cobro PENDIENTE equivalente (mismos datos) distinto de {@code pago}, si existe. */
    Optional<Pago> pendienteEquivalente(Pago pago);

    /** {@code true} si esa transacción de Wompi ya aprobó algún cobro. */
    boolean transaccionYaUsada(String idTransaccionWompi);

    Optional<String> categoriaDeDeportista(UUID idDeportista);

    /** Nombre completo de un acudiente del deportista; prefiere al de {@code idUsuarioPreferido} (puede ser {@code null}). */
    Optional<String> nombreAcudiente(UUID idDeportista, UUID idUsuarioPreferido);

    void asignarReferenciaWompi(UUID id, String referencia);

    /** Siguiente {@code FD-AAAA-NNNNNN} de la secuencia de la migración 004. */
    String siguienteNumeroComprobante();

    /**
     * Pasa a APROBADO.
     *
     * @throws DatoDuplicadoException {@code mensualidad} (hay otra no rechazada) o {@code transaccion} (esa transacción ya aprobó otro cobro)
     */
    void aprobar(UUID id, LocalDate fechaPago, MetodoPago metodo, String idTransaccionWompi, String numeroComprobante,
                 UUID registradoPor, String notaInterna);

    void rechazar(UUID id, String notaInterna, UUID registradoPor);
}
