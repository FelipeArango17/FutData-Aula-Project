package co.edu.futdata.util.reports;

import co.edu.futdata.config.ClubProperties;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link IGeneradorComprobante} con OpenPDF: comprobante de pago en tamaño carta, en memoria.
 *
 * <p>Contenido: encabezado con los datos del club y el número; tabla con deportista, categoría, acudiente,
 * concepto, fecha y método de pago (y la referencia Wompi si hay); el monto en número y en letras; y el pie
 * con la emisión y la aclaración de que no es factura electrónica.
 */
@Component
public class ComprobantePdfGenerator implements IGeneradorComprobante {

    static final String TITULO = "Comprobante de pago";
    static final String NOTA_PIE = "Este comprobante es un soporte del pago recibido por el club. No es una factura electrónica.";

    private static final Font MONTO = new Font(Font.HELVETICA, 22, Font.BOLD, PlantillaReportePdf.AZUL_MARCA);
    private static final Font MONTO_LETRAS = new Font(Font.HELVETICA, 10, Font.ITALIC, PlantillaReportePdf.TEXTO);

    private final PlantillaReportePdf plantilla;
    private final Clock reloj;

    /** Con el reloj del sistema en hora de Colombia (para usarlo fuera de Spring, por ejemplo en una prueba). */
    public ComprobantePdfGenerator(ClubProperties club) {
        this(club, Clock.system(FormatoColombiano.ZONA));
    }

    /** @param reloj hora de Colombia ({@code config.RelojConfig}); en las pruebas, uno fijo para validar la fecha de pago */
    @Autowired
    public ComprobantePdfGenerator(ClubProperties club, Clock reloj) {
        this.plantilla = new PlantillaReportePdf(club);
        this.reloj = reloj;
    }

    @Override
    public byte[] generar(ComprobantePagoDTO datos) {
        if (datos == null) {
            throw new ComprobanteInvalidoException(List.of("datos: son obligatorios"));
        }
        datos.validar(LocalDate.now(reloj.withZone(FormatoColombiano.ZONA)));

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = plantilla.nuevoDocumento();
        try {
            String numero = datos.numeroComprobante().strip();
            plantilla.abrir(documento, salida, TITULO + " " + numero, datos.fechaEmision(), NOTA_PIE);
            plantilla.agregarEncabezado(documento, TITULO, List.of("N.º " + numero,
                    "Fecha de emisión: " + FormatoColombiano.fecha(datos.fechaEmision().atZone(FormatoColombiano.ZONA).toLocalDate())));
            documento.add(tablaDeDatos(datos));
            documento.add(new Paragraph(" ", PlantillaReportePdf.PEQUENA));
            documento.add(cuadroDelMonto(datos.monto()));
            documento.close();
        } catch (DocumentException e) {
            // Sin los datos en el mensaje: pueden tener nombres de menores de edad.
            throw new IllegalStateException("No se pudo generar el comprobante en PDF", e);
        }
        return salida.toByteArray();
    }

    private static PdfPTable tablaDeDatos(ComprobantePagoDTO datos) {
        PdfPTable tabla = new PdfPTable(new float[] {32, 68});
        tabla.setWidthPercentage(100);
        PlantillaReportePdf.agregarFila(tabla, "Deportista", datos.nombreDeportista().strip());
        if (ComprobantePagoDTO.tieneTexto(datos.categoria())) {
            PlantillaReportePdf.agregarFila(tabla, "Categoría", datos.categoria().strip());
        }
        PlantillaReportePdf.agregarFila(tabla, "Acudiente", datos.nombreAcudiente().strip());
        PlantillaReportePdf.agregarFila(tabla, "Concepto", datos.concepto().strip());
        PlantillaReportePdf.agregarFila(tabla, "Fecha de pago", FormatoColombiano.fecha(datos.fechaPago()));
        PlantillaReportePdf.agregarFila(tabla, "Método de pago", datos.metodo().etiqueta());
        if (ComprobantePagoDTO.tieneTexto(datos.referenciaWompi())) {
            PlantillaReportePdf.agregarFila(tabla, "Referencia Wompi", datos.referenciaWompi().strip());
        }
        return tabla;
    }

    /** Recuadro con fondo claro y borde oscuro: se distingue también impreso en blanco y negro. */
    private static PdfPTable cuadroDelMonto(long monto) {
        PdfPCell celda = new PdfPCell();
        celda.setBackgroundColor(PlantillaReportePdf.AZUL_FONDO);
        celda.setBorderColor(PlantillaReportePdf.AZUL_MARCA);
        celda.setBorderWidth(1.5f);
        celda.setPadding(12);
        celda.setPaddingTop(8);
        for (Paragraph parrafo : List.of(
                new Paragraph("Valor pagado", PlantillaReportePdf.ETIQUETA),
                new Paragraph(FormatoColombiano.pesos(monto), MONTO),
                new Paragraph(NumeroEnLetras.enPesos(monto), MONTO_LETRAS))) {
            parrafo.setAlignment(Element.ALIGN_CENTER);
            celda.addElement(parrafo);
        }
        PdfPTable cuadro = new PdfPTable(1);
        cuadro.setWidthPercentage(100);
        cuadro.addCell(celda);
        return cuadro;
    }
}
