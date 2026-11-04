package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.ResultadoCiclosDTO;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cobros por ciclo de ingreso, "como un gimnasio" (reemplaza a la generación manual por calendario,
 * {@link IPagoService#generarMensualidades}). Ver {@link co.edu.futdata.domain.rules.CiclosDeCobro} para la regla de fechas.
 */
public interface ICobrosCicloService {

    /**
     * Crea los cobros que tocan hoy: la mensualidad del ciclo vigente de cada deportista activo (si no tiene ninguna de ese
     * periodo, en cualquier estado) y, a los ingresos recientes, la inscripción y el uniforme. Avisa por correo de las
     * mensualidades creadas con anticipación. Idempotente.
     *
     * @param hoy       el día de referencia (en la app, la fecha de Colombia)
     * @param idUsuario quien lo pidió; {@code null} si lo pidió la tarea programada (n8n)
     * @throws ConflictoException si el monto de la mensualidad no está configurado
     */
    ResultadoCiclosDTO generarCobrosDeCiclo(LocalDate hoy, UUID idUsuario);
}
