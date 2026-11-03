package co.edu.futdata.services.facade;

import java.time.Duration;

/**
 * Se superó un límite de frecuencia ({@code util.security.LimiteDeFrecuencia}). {@code ManejadorGlobalErrores}
 * responde {@code 429} con la cabecera {@code Retry-After} (segundos, redondeados hacia arriba) y {@code msRestantes}.
 */
public class DemasiadasSolicitudesException extends RuntimeException {

    public static final String CODIGO = "DEMASIADAS_SOLICITUDES";

    private final Duration espera;

    public DemasiadasSolicitudesException(String mensaje, Duration espera) {
        super(mensaje);
        this.espera = espera == null || espera.isNegative() ? Duration.ZERO : espera;
    }

    public Duration getEspera() {
        return espera;
    }

    /** Segundos para {@code Retry-After}: hacia arriba y al menos 1. */
    public long segundosParaReintentar() {
        long segundos = espera.toSeconds() + (espera.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, segundos);
    }
}
