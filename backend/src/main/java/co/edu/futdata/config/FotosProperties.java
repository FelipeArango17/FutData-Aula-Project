package co.edu.futdata.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Fotos de los deportistas en Supabase Storage ({@code futdata.fotos.*}). Separadas de {@link SupabaseProperties}
 * para no cambiar la vigencia de los comprobantes de pago (5 min), que es corta a propósito.
 *
 * @param bucket             bucket privado de las fotos ({@code FOTOS_BUCKET}; el de la migración 007 es {@code fotos})
 * @param vigenciaUrlFirmada cuánto dura la URL firmada que va en las respuestas ({@code FOTOS_VIGENCIA_URL_FIRMADA}).
 *                           60 min por defecto, para que una página abierta un rato siga mostrando las fotos. De
 *                           10 s a 1 h, lo que acepta Storage; fuera de ese rango la aplicación no arranca
 */
@ConfigurationProperties(prefix = "futdata.fotos")
public record FotosProperties(
        @DefaultValue("fotos") String bucket,
        @DefaultValue("60m") Duration vigenciaUrlFirmada) {

    static final Duration VIGENCIA_MINIMA = Duration.ofSeconds(10);
    static final Duration VIGENCIA_MAXIMA = Duration.ofHours(1);

    public FotosProperties {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("futdata.fotos.bucket no puede estar vacío");
        }
        if (vigenciaUrlFirmada == null || vigenciaUrlFirmada.compareTo(VIGENCIA_MINIMA) < 0
                || vigenciaUrlFirmada.compareTo(VIGENCIA_MAXIMA) > 0) {
            throw new IllegalArgumentException("futdata.fotos.vigencia-url-firmada debe estar entre 10s y 60m");
        }
    }
}
