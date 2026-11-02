package co.edu.futdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cobro mensual automático ({@code futdata.mensualidad.*}).
 *
 * @param monto                pesos enteros de la mensualidad ({@code FUTDATA_MENSUALIDAD_MONTO}). PENDIENTE: lo define el
 *                             club. {@code 0} = sin configurar: no se generan mensualidades
 * @param diaVencimiento       día del mes en que vence ({@code FUTDATA_MENSUALIDAD_DIA_VENCIMIENTO}, por defecto 5); si el mes
 *                             es más corto, el último día
 * @param generacionAutomatica activa el proceso diario que crea las mensualidades del mes en curso
 *                             ({@code FUTDATA_MENSUALIDAD_GENERACION_AUTOMATICA}, por defecto apagado)
 */
@ConfigurationProperties(prefix = "futdata.mensualidad")
public record MensualidadProperties(
        @DefaultValue("0") long monto,
        @DefaultValue("5") int diaVencimiento,
        @DefaultValue("false") boolean generacionAutomatica) {
}
