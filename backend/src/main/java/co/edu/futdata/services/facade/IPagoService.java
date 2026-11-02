package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.AprobarPagoRequestDTO;
import co.edu.futdata.api.dto.CrearPagoRequestDTO;
import co.edu.futdata.api.dto.IniciarWompiResponseDTO;
import co.edu.futdata.api.dto.MiPagoResponseDTO;
import co.edu.futdata.api.dto.PaginaResponseDTO;
import co.edu.futdata.api.dto.PagoResponseDTO;
import co.edu.futdata.api.dto.RechazarPagoRequestDTO;
import co.edu.futdata.api.dto.ResultadoGeneracionDTO;
import co.edu.futdata.persistence.dao.FiltroPagos;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Cobros del club y pagos en línea con Wompi. Quien llama ya comprobó el rol ({@code @PreAuthorize}); aquí se comprueba
 * la <b>propiedad</b>: un acudiente solo ve e inicia pagos de sus deportistas vinculados, y de otro recibe
 * {@link RecursoNoEncontradoException} ({@code 404}, no 403). Los montos salen siempre de la base de datos.
 *
 * <p>Errores: {@link DatosInvalidosException} (400), {@link RecursoNoEncontradoException} (404), {@link ConflictoException}
 * (409) y {@link PagosNoConfiguradosException} (503).
 */
public interface IPagoService {

    /** Crea un cobro PENDIENTE (administrador). Una mensualidad repetida del mismo deportista y periodo es un conflicto. */
    PagoResponseDTO crear(UUID idUsuario, CrearPagoRequestDTO datos);

    /** Listado paginado para administrador y tesorero. {@code sort}: {@code campo,asc|desc} con campo vencimiento, monto, creadoEn o estado. */
    PaginaResponseDTO<PagoResponseDTO> listar(FiltroPagos filtro, String sort, int page, int size);

    /** Los cobros de los deportistas del acudiente. */
    List<MiPagoResponseDTO> misPagos(UUID idUsuario);

    /** Datos del checkout de Wompi para un cobro PENDIENTE del acudiente; reutiliza la referencia si ya la tiene. */
    IniciarWompiResponseDTO iniciarWompi(UUID idUsuario, UUID idPago);

    /** Sobre un cobro RECHAZADO, crea (o devuelve el ya existente) un cobro PENDIENTE equivalente. */
    MiPagoResponseDTO reintentar(UUID idUsuario, UUID idPago);

    /** Consulta a Wompi las transacciones de la referencia y aplica las mismas reglas del webhook. */
    PagoResponseDTO conciliar(UUID idUsuario, UUID idPago);

    /** Aprobación manual (EFECTIVO o TRANSFERENCIA). */
    PagoResponseDTO aprobar(UUID idUsuario, UUID idPago, AprobarPagoRequestDTO datos);

    PagoResponseDTO rechazar(UUID idUsuario, UUID idPago, RechazarPagoRequestDTO datos);

    /** El PDF del comprobante de un cobro APROBADO. {@code privilegiado}: administrador o tesorero (ven todos). */
    byte[] comprobante(UUID idUsuario, boolean privilegiado, UUID idPago);

    /** Crea la mensualidad PENDIENTE de {@code periodo} de cada deportista que no la tenga. Idempotente. */
    ResultadoGeneracionDTO generarMensualidades(UUID idUsuario, YearMonth periodo);
}
