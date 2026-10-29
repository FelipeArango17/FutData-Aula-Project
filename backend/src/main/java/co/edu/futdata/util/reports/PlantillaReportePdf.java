package co.edu.futdata.util.reports;

import co.edu.futdata.config.ClubProperties;
import java.awt.Color;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.draw.LineSeparator;

/**
 * Encabezado, pie, colores y fuentes comunes a los PDF de FutData (tamaño carta). La usa el comprobante de
 * pago y la podrá usar el estado de cuenta: cada reporte arma solo su cuerpo.
 *
 * <p>Colores de {@code docs/design-system/tokens.css} (tema normal). Para que se lea impreso en blanco y negro,
 * el texto va en tonos oscuros sobre fondo blanco o muy claro, los bordes son grises medios y ningún dato se
 * distingue solo por el color.
 *
 * <p>Fuente Helvetica estándar (codificación WinAnsi): tiene tildes y ñ, y no hay que incrustarla.
 */
public class PlantillaReportePdf {

    /** {@code --navy-900} ({@code --color-brand-primary}). */
    public static final Color AZUL_MARCA = new Color(0x0F2C4C);
    /** {@code --navy-50}: fondo de lo destacado. */
    public static final Color AZUL_FONDO = new Color(0xEAF0F7);
    /** {@code --green-700} ({@code --color-brand-accent-text}). */
    public static final Color VERDE_ACENTO = new Color(0x1F7A29);
    /** {@code --gray-900} ({@code --color-text-primary}). */
    public static final Color TEXTO = new Color(0x1A2332);
    /** {@code --gray-600} ({@code --color-text-secondary}). */
    public static final Color TEXTO_SECUNDARIO = new Color(0x4B5563);
    /** {@code --gray-50}: fondo de las etiquetas de las tablas. */
    public static final Color FONDO_ETIQUETA = new Color(0xF7F8FA);
    /** {@code --gray-400}: bordes. Más oscuro que {@code --gray-200} para que no desaparezca al imprimir. */
    public static final Color BORDE = new Color(0x9CA3AF);

    public static final Font NOMBRE_CLUB = new Font(Font.HELVETICA, 13, Font.BOLD, AZUL_MARCA);
    public static final Font TITULO = new Font(Font.HELVETICA, 16, Font.BOLD, AZUL_MARCA);
    public static final Font SUBTITULO = new Font(Font.HELVETICA, 10, Font.BOLD, TEXTO);
    public static final Font NORMAL = new Font(Font.HELVETICA, 10, Font.NORMAL, TEXTO);
    public static final Font ETIQUETA = new Font(Font.HELVETICA, 9, Font.BOLD, TEXTO_SECUNDARIO);
    public static final Font PEQUENA = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXTO_SECUNDARIO);
    private static final Font PIE = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXTO_SECUNDARIO);

    private static final float MARGEN = 50;
    private static final float MARGEN_INFERIOR = 80;

    private final ClubProperties club;

    public PlantillaReportePdf(ClubProperties club) {
        this.club = club;
    }

    /** Documento tamaño carta con espacio abajo para el pie. */
    public Document nuevoDocumento() {
        return new Document(PageSize.LETTER, MARGEN, MARGEN, MARGEN, MARGEN_INFERIOR);
    }

    /**
     * Abre el documento sobre {@code salida} con los metadatos y el pie en cada página.
     *
     * @param titulo  título de los metadatos; <b>sin datos personales</b> (por ejemplo, "Comprobante de pago FD-2026-000123")
     * @param emision momento de emisión, que se imprime en el pie
     * @param notaPie segunda línea del pie, por ejemplo la aclaración de que no es factura electrónica; puede ser {@code null}
     */
    public PdfWriter abrir(Document documento, OutputStream salida, String titulo, Instant emision, String notaPie) {
        PdfWriter writer = PdfWriter.getInstance(documento, salida);
        writer.setPageEvent(new Pie(emision, notaPie));
        documento.addTitle(titulo);
        documento.addAuthor(ComprobantePagoDTO.tieneTexto(club.nombre()) ? club.nombre().strip() : "FutData");
        documento.addCreator("FutData");
        documento.addCreationDate();
        documento.open();
        return writer;
    }

    /**
     * Encabezado: a la izquierda el club y sus datos; a la derecha el título del reporte y sus detalles
     * (número, fecha...). Debajo, una línea del color de acento.
     */
    public void agregarEncabezado(Document documento, String titulo, List<String> detalles) {
        PdfPTable tabla = new PdfPTable(new float[] {58, 42});
        tabla.setWidthPercentage(100);

        PdfPCell izquierda = sinBorde();
        if (ComprobantePagoDTO.tieneTexto(club.nombre())) {
            izquierda.addElement(new Paragraph(club.nombre().strip(), NOMBRE_CLUB));
        }
        for (String linea : lineasDelClub()) {
            izquierda.addElement(new Paragraph(linea, PEQUENA));
        }
        tabla.addCell(izquierda);

        PdfPCell derecha = sinBorde();
        Paragraph tituloParrafo = new Paragraph(titulo, TITULO);
        tituloParrafo.setAlignment(Element.ALIGN_RIGHT);
        derecha.addElement(tituloParrafo);
        for (String detalle : detalles) {
            Paragraph parrafo = new Paragraph(detalle, SUBTITULO);
            parrafo.setAlignment(Element.ALIGN_RIGHT);
            derecha.addElement(parrafo);
        }
        tabla.addCell(derecha);
        tabla.setSpacingAfter(8);

        documento.add(tabla);
        documento.add(new Chunk(new LineSeparator(2f, 100f, VERDE_ACENTO, Element.ALIGN_CENTER, -4)));
        documento.add(new Paragraph(" ", PEQUENA));
    }

    /** NIT, dirección, teléfono y correo, ya con formato. Los vacíos no aparecen. */
    List<String> lineasDelClub() {
        List<String> lineas = new ArrayList<>();
        if (ComprobantePagoDTO.tieneTexto(club.nit())) {
            lineas.add(FormatoColombiano.nit(club.nit()));
        }
        if (ComprobantePagoDTO.tieneTexto(club.direccion())) {
            lineas.add(club.direccion().strip());
        }
        if (ComprobantePagoDTO.tieneTexto(club.telefono())) {
            lineas.add("Tel. " + FormatoColombiano.telefono(club.telefono()));
        }
        if (ComprobantePagoDTO.tieneTexto(club.correo())) {
            lineas.add(club.correo().strip());
        }
        return lineas;
    }

    /** Fila etiqueta / valor de una tabla de datos. El valor se parte en varias líneas si es largo. */
    public static void agregarFila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, ETIQUETA));
        celdaEtiqueta.setBackgroundColor(FONDO_ETIQUETA);
        PdfPCell celdaValor = new PdfPCell(new Phrase(valor, NORMAL));
        for (PdfPCell celda : List.of(celdaEtiqueta, celdaValor)) {
            celda.setBorderColor(BORDE);
            celda.setBorderWidth(0.5f);
            celda.setPadding(6);
            celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            tabla.addCell(celda);
        }
    }

    private static PdfPCell sinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        return celda;
    }

    /** Pie de cada página: emisión, "Documento generado por FutData", la nota y el número de página. */
    private static final class Pie extends PdfPageEventHelper {

        private final String emision;
        private final String nota;

        Pie(Instant emision, String nota) {
            this.emision = "Emitido el " + FormatoColombiano.fechaHora(emision) + " (hora de Colombia)"
                    + " · Documento generado por FutData";
            this.nota = nota;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document documento) {
            PdfContentByte lienzo = writer.getDirectContent();
            float y = documento.bottom() - 20;
            lienzo.saveState();
            lienzo.setColorStroke(BORDE);
            lienzo.setLineWidth(0.5f);
            lienzo.moveTo(documento.left(), y);
            lienzo.lineTo(documento.right(), y);
            lienzo.stroke();
            lienzo.restoreState();

            ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT, new Phrase(emision, PIE), documento.left(), y - 12, 0);
            if (nota != null) {
                ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT, new Phrase(nota, PIE), documento.left(), y - 23, 0);
            }
            ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT, new Phrase("Página " + writer.getPageNumber(), PIE),
                    documento.right(), y - 12, 0);
        }
    }
}
