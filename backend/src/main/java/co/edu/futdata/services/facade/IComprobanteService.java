package co.edu.futdata.services.facade;

import co.edu.futdata.util.reports.ComprobantePagoDTO;

/**
 * Comprobantes de pago en PDF guardados en Supabase Storage (HU-018, tarea 328).
 *
 * <p>Lo que se guarda en {@code Pago.recibo_url} es la <b>ruta</b> que devuelve {@link #generarYGuardar}
 * ({@code 2026/FD-2026-000123.pdf}), no una URL: las URL firmadas vencen a los pocos minutos, así que se pide una
 * nueva con {@link #enlaceDescarga} cada vez que alguien con permiso quiere descargar el comprobante.
 */
public interface IComprobanteService {

    /**
     * Genera el PDF y lo sube al bucket de comprobantes. No sobrescribe uno existente.
     *
     * @param datos datos del comprobante; {@code numeroComprobante} con el formato {@code FD-AAAA-NNNNNN} y el año
     *              de la fecha de pago
     * @return la ruta dentro del bucket, {@code AAAA/FD-AAAA-NNNNNN.pdf}: lo que se guarda en {@code Pago.recibo_url}
     * @throws co.edu.futdata.util.reports.ComprobanteInvalidoException                datos inválidos ({@code 400})
     * @throws co.edu.futdata.datasource.external.ArchivoYaExisteException            ya hay un comprobante con ese número
     * @throws co.edu.futdata.datasource.external.AlmacenamientoException             otras fallas de Storage
     */
    String generarYGuardar(ComprobantePagoDTO datos);

    /**
     * URL firmada para descargar el comprobante, con la vigencia de {@code futdata.supabase.vigencia-url-firmada}
     * (5 min). <b>No revisa permisos</b>: quien llama ya debe haber verificado el rol.
     *
     * @param ruta la de {@code Pago.recibo_url}, con el formato {@code AAAA/FD-AAAA-NNNNNN.pdf}
     * @throws co.edu.futdata.datasource.external.ArchivoInvalidoException     la ruta no tiene ese formato
     * @throws co.edu.futdata.datasource.external.ArchivoNoEncontradoException no hay un archivo en esa ruta
     * @throws co.edu.futdata.datasource.external.AlmacenamientoException      otras fallas de Storage
     */
    String enlaceDescarga(String ruta);
}
