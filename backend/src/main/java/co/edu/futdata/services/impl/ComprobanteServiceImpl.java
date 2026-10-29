package co.edu.futdata.services.impl;

import co.edu.futdata.config.SupabaseProperties;
import co.edu.futdata.datasource.external.ArchivoInvalidoException;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import co.edu.futdata.services.facade.IComprobanteService;
import co.edu.futdata.util.reports.ComprobanteInvalidoException;
import co.edu.futdata.util.reports.ComprobantePagoDTO;
import co.edu.futdata.util.reports.IGeneradorComprobante;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Genera el comprobante con {@link IGeneradorComprobante} y lo guarda con {@link IAlmacenamientoArchivos}.
 *
 * <p>Ruta: {@code AAAA/FD-AAAA-NNNNNN.pdf}: el año y el número del comprobante. No lleva
 * nombres, documentos ni correos: solo el año y el número.
 */
@Service
public class ComprobanteServiceImpl implements IComprobanteService {

    /** {@code FD-2026-000123}: año y consecutivo de 6 dígitos (lo generará una secuencia de la tabla Pago). */
    static final Pattern NUMERO = Pattern.compile("FD-(\\d{4})-\\d{6}");
    /** {@code 2026/FD-2026-000123.pdf}: la carpeta es el mismo año del número. */
    static final Pattern RUTA = Pattern.compile("(\\d{4})/FD-\\1-\\d{6}\\.pdf");

    private final IGeneradorComprobante generador;
    private final IAlmacenamientoArchivos almacenamiento;
    private final SupabaseProperties supabase;

    public ComprobanteServiceImpl(IGeneradorComprobante generador, IAlmacenamientoArchivos almacenamiento,
            SupabaseProperties supabase) {
        this.generador = generador;
        this.almacenamiento = almacenamiento;
        this.supabase = supabase;
    }

    // TODO tabla Pago: PagoServiceImpl la llamará al registrar o aprobar un pago y guardará la ruta en Pago.recibo_url.
    @Override
    public String generarYGuardar(ComprobantePagoDTO datos) {
        byte[] pdf = generador.generar(datos);
        String ruta = rutaDe(datos);
        return almacenamiento.subir(supabase.bucketComprobantes(), ruta, pdf, IAlmacenamientoArchivos.PDF);
    }

    // TODO HU-001, HU-005 y tabla Pago: PagoController expondrá GET /api/pagos/{id}/comprobante. Antes de llamar
    // aquí, IPagoService busca el pago (404 si no existe), verifica el rol con el JWT (ADMINISTRADOR o TESORERO, o el
    // ACUDIENTE de ese deportista; si no, 403) y lee Pago.recibo_url. Responde la URL firmada o un 302 hacia ella.
    @Override
    public String enlaceDescarga(String ruta) {
        if (ruta == null || !RUTA.matcher(ruta).matches()) {
            throw new ArchivoInvalidoException("La ruta del comprobante no tiene el formato AAAA/FD-AAAA-NNNNNN.pdf", null, null);
        }
        return almacenamiento.urlFirmada(supabase.bucketComprobantes(), ruta, supabase.vigenciaUrlFirmada());
    }

    /**
     * {@code 2026/FD-2026-000123.pdf}. La carpeta es el año del número, que puede no ser el de la fecha de pago
     * (un pago del 31 de diciembre registrado en enero recibe un número del año nuevo).
     */
    static String rutaDe(ComprobantePagoDTO datos) {
        String numero = datos.numeroComprobante().strip();
        Matcher formato = NUMERO.matcher(numero);
        if (!formato.matches()) {
            throw new ComprobanteInvalidoException(List.of("numeroComprobante: debe tener el formato FD-AAAA-NNNNNN"));
        }
        return formato.group(1) + "/" + numero + ".pdf";
    }
}
