package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que el frontend necesita para abrir el Web Checkout de Wompi ({@code https://checkout.wompi.co/p/}): los parámetros
 * {@code public-key}, {@code currency}, {@code amount-in-cents}, {@code reference}, {@code signature:integrity} y,
 * opcional, {@code redirect-url}. Nunca incluye la llave privada ni los secretos.
 */
public record IniciarWompiResponseDTO(
        String llavePublica,
        @Schema(example = "FD-7f0c2a52-6a5d-4f0e-9d5e-0b8d2d9c1a11") String referencia,
        @Schema(example = "9000000") long montoEnCentavos,
        @Schema(example = "COP") String moneda,
        String firmaIntegridad,
        String urlRedireccion) {
}
