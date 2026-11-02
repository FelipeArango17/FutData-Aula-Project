package co.edu.futdata.datasource.external;

/**
 * Una transacción de Wompi tal como llega en el webhook ({@code data.transaction}) o en la consulta por referencia.
 * Cada intento de pago es una transacción distinta (otro {@code id}, a veces otro medio) con la misma
 * {@code referencia}: es normal ver un DECLINED y luego un APPROVED.
 *
 * @param id               ID de la transacción en Wompi
 * @param estado           {@code APPROVED}, {@code DECLINED}, {@code VOIDED}, {@code ERROR} o {@code PENDING}
 * @param montoEnCentavos  {@code amount_in_cents}
 * @param referencia       la referencia del cobro ({@code FD-<id_pago>})
 * @param tipoMetodoPago   {@code payment_method_type}: {@code CARD}, {@code NEQUI}, {@code PSE}, ...
 */
public record TransaccionWompi(String id, String estado, long montoEnCentavos, String referencia, String tipoMetodoPago) {
}
