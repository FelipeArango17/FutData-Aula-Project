package co.edu.futdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cobros por ciclo de ingreso ({@code futdata.cobros.*}). El monto de la mensualidad es el de siempre
 * ({@link MensualidadProperties#monto()}, {@code FUTDATA_MENSUALIDAD_MONTO}).
 *
 * @param inscripcionMonto   pesos de la inscripción que se cobra al ingresar ({@code FUTDATA_COBROS_INSCRIPCION_MONTO}, 150000)
 * @param uniformeMonto      pesos del uniforme que se cobra al ingresar ({@code FUTDATA_COBROS_UNIFORME_MONTO}, 90000)
 * @param diasAnticipacion   con cuántos días de anticipación se crea el cobro del ciclo siguiente
 *                           ({@code FUTDATA_COBROS_DIAS_ANTICIPACION}, 5)
 * @param ventanaIngresoDias hasta cuántos días después del registro se considera un ingreso nuevo y se cobran inscripción y
 *                           uniforme ({@code FUTDATA_COBROS_VENTANA_INGRESO_DIAS}, 7)
 */
@ConfigurationProperties(prefix = "futdata.cobros")
public record CobrosCicloProperties(
        @DefaultValue("150000") long inscripcionMonto,
        @DefaultValue("90000") long uniformeMonto,
        @DefaultValue("5") int diasAnticipacion,
        @DefaultValue("7") int ventanaIngresoDias) {

    public CobrosCicloProperties {
        if (inscripcionMonto < 0 || uniformeMonto < 0) {
            throw new IllegalArgumentException("Los montos de inscripción y uniforme no pueden ser negativos");
        }
        if (diasAnticipacion < 0 || ventanaIngresoDias < 0) {
            throw new IllegalArgumentException("Los días de anticipación y de ventana de ingreso no pueden ser negativos");
        }
    }
}
