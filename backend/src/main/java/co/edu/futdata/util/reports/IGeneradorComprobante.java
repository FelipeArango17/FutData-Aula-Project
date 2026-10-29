package co.edu.futdata.util.reports;

/**
 * Genera el comprobante de un pago en PDF, en memoria. No lo guarda ni lo envía: eso lo hace el servicio de
 * pagos (tarea 328: Supabase Storage y enlace de descarga).
 */
public interface IGeneradorComprobante {

    /**
     * @param datos datos del pago que ve el acudiente
     * @return el PDF (tamaño carta)
     * @throws ComprobanteInvalidoException si los datos no son válidos ({@link ComprobantePagoDTO#validar})
     */
    byte[] generar(ComprobantePagoDTO datos);
}
