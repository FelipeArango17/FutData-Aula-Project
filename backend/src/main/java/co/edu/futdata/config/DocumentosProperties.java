package co.edu.futdata.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Documentos de la inscripción en Supabase Storage ({@code futdata.documentos.*}). Son documentos de identidad de
 * menores: la URL firmada es corta a propósito y solo se entrega bajo demanda.
 *
 * @param bucket             bucket privado de los documentos ({@code DOCUMENTOS_BUCKET}; el de la migración 007 es
 *                           {@code documentos})
 * @param vigenciaUrlFirmada cuánto dura la URL de descarga ({@code DOCUMENTOS_VIGENCIA_URL_FIRMADA}). 300 s por
 *                           defecto. De 10 s a 1 h, lo que acepta Storage; fuera de ese rango la aplicación no arranca
 */
@ConfigurationProperties(prefix = "futdata.documentos")
public record DocumentosProperties(
        @DefaultValue("documentos") String bucket,
        @DefaultValue("300s") Duration vigenciaUrlFirmada) {

    static final Duration VIGENCIA_MINIMA = Duration.ofSeconds(10);
    static final Duration VIGENCIA_MAXIMA = Duration.ofHours(1);

    public DocumentosProperties {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("futdata.documentos.bucket no puede estar vacío");
        }
        if (vigenciaUrlFirmada == null || vigenciaUrlFirmada.compareTo(VIGENCIA_MINIMA) < 0
                || vigenciaUrlFirmada.compareTo(VIGENCIA_MAXIMA) > 0) {
            throw new IllegalArgumentException("futdata.documentos.vigencia-url-firmada debe estar entre 10s y 60m");
        }
    }
}
