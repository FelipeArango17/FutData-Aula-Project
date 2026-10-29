package co.edu.futdata.util.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.edu.futdata.config.ClubProperties;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** El comprobante generado, leído con PDFBox. Deja ejemplos en target/comprobantes-ejemplo/ para revisarlos a mano. */
class ComprobantePdfGeneratorTest {

    private static final ClubProperties CLUB = new ClubProperties("Asociación Deportiva Prado Fútbol Club",
            "811032309-1", "Calle 40 Sur 75b-81, San Antonio de Prado, Medellín, Antioquia", "3137317366",
            "prafofutbolclub@gmail.com");
    /** 4:35 p. m. del 28 de octubre de 2026 en Colombia. */
    private static final Instant AHORA = Instant.parse("2026-10-28T21:35:00Z");
    private static final Path EJEMPLOS = Path.of("target", "comprobantes-ejemplo");

    private final ComprobantePdfGenerator generador = generador(CLUB, AHORA);

    // --- Contenido ---

    @Test
    void pagoManualSinReferenciaWompi() throws IOException {
        byte[] pdf = generador.generar(pagoManual());
        guardarEjemplo("comprobante-pago-manual.pdf", pdf);

        String texto = texto(pdf);
        assertThat(texto).contains(
                "Comprobante de pago",
                "N.º FD-2026-000123",
                "Fecha de emisión: 28 de octubre de 2026",
                "Deportista", "Santiago Gómez Ríos",
                "Categoría", "Sub-12",
                "Acudiente", "Marta Ríos",
                "Concepto", "Mensualidad octubre",
                "Fecha de pago", "27 de octubre de 2026",
                "Método de pago", "Efectivo",
                "Valor pagado", "$ 90.000", "noventa mil pesos M/CTE",
                "Emitido el 28 de octubre de 2026, 4:35 p. m. (hora de Colombia)",
                "Documento generado por FutData",
                "No es una factura electrónica",
                "Página 1");
        assertThat(texto).doesNotContain("Referencia Wompi");
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            // Tamaño carta: 612 x 792 puntos
            assertThat(documento.getPage(0).getMediaBox().getWidth()).isEqualTo(612f);
            assertThat(documento.getPage(0).getMediaBox().getHeight()).isEqualTo(792f);
        }
    }

    @Test
    void pagoEnLineaConReferenciaWompi() throws IOException {
        ComprobantePagoDTO enLinea = con(pagoManual(), b -> b.metodo(MetodoPago.PSE).referenciaWompi("12345-1698765432-49201")
                .monto(1_250_500).concepto("Inscripción 2026"));
        byte[] pdf = generador.generar(enLinea);
        guardarEjemplo("comprobante-pago-en-linea.pdf", pdf);

        assertThat(texto(pdf)).contains("Método de pago", "PSE", "Referencia Wompi", "12345-1698765432-49201",
                "$ 1.250.500", "un millón doscientos cincuenta mil quinientos pesos M/CTE", "Inscripción 2026");
    }

    @Test
    void sinCategoriaNoImprimeEsaFila() throws IOException {
        assertThat(texto(generador.generar(con(pagoManual(), b -> b.categoria(" "))))).doesNotContain("Categoría");
    }

    @Test
    void imprimeLosDatosDelClubConFormato() throws IOException {
        assertThat(texto(generador.generar(pagoManual()))).contains(
                "Asociación Deportiva Prado Fútbol Club",
                "NIT 811.032.309-1",
                "Calle 40 Sur 75b-81, San Antonio de Prado, Medellín, Antioquia",
                "Tel. 313 731 7366",
                "prafofutbolclub@gmail.com");
    }

    /** Cada dato del club vacío deja de imprimirse, sin afectar a los demás. */
    @ParameterizedTest
    @ValueSource(strings = {"nombre", "nit", "direccion", "telefono", "correo"})
    void unDatoDelClubVacioNoSeImprime(String vacio) throws IOException {
        Map<String, String> valores = Map.of(
                "nombre", "Asociación Deportiva Prado Fútbol Club", "nit", "NIT 811.032.309-1",
                "direccion", "Calle 40 Sur 75b-81", "telefono", "Tel. 313 731 7366", "correo", "prafofutbolclub@gmail.com");
        ClubProperties club = new ClubProperties(
                vacio.equals("nombre") ? "" : CLUB.nombre(),
                vacio.equals("nit") ? " " : CLUB.nit(),
                vacio.equals("direccion") ? null : CLUB.direccion(),
                vacio.equals("telefono") ? "" : CLUB.telefono(),
                vacio.equals("correo") ? "" : CLUB.correo());

        String texto = texto(generador(club, AHORA).generar(pagoManual()));

        valores.forEach((campo, impreso) -> {
            if (campo.equals(vacio)) {
                assertThat(texto).doesNotContain(impreso);
            } else {
                assertThat(texto).contains(impreso);
            }
        });
    }

    @Test
    void todosLosDatosDelClubVaciosGeneraIgual() throws IOException {
        byte[] pdf = generador(new ClubProperties("", "", "", "", ""), AHORA).generar(pagoManual());
        String texto = texto(pdf);

        assertThat(texto).contains("Comprobante de pago", "$ 90.000").doesNotContain("NIT", "Tel.", "@");
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getDocumentInformation().getAuthor()).isEqualTo("FutData");
        }
    }

    @Test
    void conservaTildesYEnes() throws IOException {
        ComprobantePagoDTO conTildes = con(pagoManual(), b -> b.nombreDeportista("Sebastián Muñoz Ñañez")
                .nombreAcudiente("Inés Peña Gutiérrez").concepto("Mensualidad útiles y camiseta (año 2026)"));

        assertThat(texto(generador.generar(conTildes)))
                .contains("Sebastián Muñoz Ñañez", "Inés Peña Gutiérrez", "Mensualidad útiles y camiseta (año 2026)");
    }

    @Test
    void unConceptoLargoNoSeCorta() throws IOException {
        String largo = "Mensualidad de octubre, uniforme de presentación, sudadera de entrenamiento, inscripción al "
                + "torneo departamental y transporte a la cancha auxiliar de San Antonio de Prado, ida y vuelta, todo el mes";
        assertThat(largo.length()).isBetween(190, ComprobantePagoDTO.MAX_CONCEPTO);
        byte[] pdf = generador.generar(con(pagoManual(), b -> b.concepto(largo)));
        guardarEjemplo("comprobante-concepto-largo.pdf", pdf);

        // El texto se reparte en varias líneas: se comparan sin los saltos.
        assertThat(texto(pdf).replaceAll("\\s+", " ")).contains(largo.replaceAll("\\s+", " "));
    }

    // --- Datos que no deben salir ---

    /** El servicio sabe quién registró el pago y la nota interna, pero el DTO no tiene dónde ponerlos. */
    @Test
    void elDtoNoTieneCamposInternosNiSensibles() {
        List<String> campos = Arrays.stream(ComprobantePagoDTO.class.getRecordComponents()).map(RecordComponent::getName).toList();

        assertThat(campos).containsExactly("numeroComprobante", "fechaEmision", "nombreDeportista", "categoria",
                "nombreAcudiente", "concepto", "monto", "fechaPago", "metodo", "referenciaWompi");
        assertThat(campos).noneMatch(campo -> campo.toLowerCase().matches(
                ".*(nota|registr|referenciamanual|documento|identidad|tarjeta|cuenta|banco).*"));
    }

    @Test
    void niLaNotaInternaNiQuienRegistroAparecenEnElPdf() throws IOException {
        // Lo que el servicio de pagos tiene del pago manual (como en frontend/src/services/pagosService.js)
        Map<String, Object> pagoDelServicio = Map.of("concepto", "Mensualidad octubre", "monto", 90_000L,
                "fechaPago", LocalDate.of(2026, 10, 27), "metodo", "Transferencia", "referencia", "TRX-INTERNA-778",
                "nota", "Pagó con dos consignaciones", "registradoPor", "tesoreria@prado.example");

        ComprobantePagoDTO dto = new ComprobantePagoDTO("FD-2026-000124", AHORA, "Santiago Gómez Ríos", "Sub-12",
                "Marta Ríos", (String) pagoDelServicio.get("concepto"), (long) pagoDelServicio.get("monto"),
                (LocalDate) pagoDelServicio.get("fechaPago"), MetodoPago.desdeEtiqueta((String) pagoDelServicio.get("metodo")), null);
        String texto = texto(generador.generar(dto));

        assertThat(texto).contains("Transferencia")
                .doesNotContain("Pagó con dos consignaciones", "tesoreria@prado.example", "TRX-INTERNA-778", "Registrado");
    }

    @Test
    void losMetadatosNoTienenDatosPersonales() throws IOException {
        try (PDDocument documento = Loader.loadPDF(generador.generar(pagoManual()))) {
            PDDocumentInformation info = documento.getDocumentInformation();
            assertThat(info.getTitle()).isEqualTo("Comprobante de pago FD-2026-000123");
            assertThat(info.getAuthor()).isEqualTo("Asociación Deportiva Prado Fútbol Club");
            assertThat(info.getCreator()).isEqualTo("FutData");
            String metadatos = String.join("|", info.getMetadataKeys().stream().map(info::getCustomMetadataValue).toList());
            for (String personal : List.of("Santiago", "Marta", "Mensualidad", "90.000", "Sub-12")) {
                assertThat(metadatos).doesNotContain(personal);
            }
            assertThat(documento.getDocumentCatalog().getMetadata()).as("sin XMP").isNull();
        }
    }

    // --- Validación ---

    @ParameterizedTest
    @ValueSource(strings = {
            "numeroComprobante: es obligatorio",
            "fechaEmision: es obligatoria",
            "nombreDeportista: es obligatorio",
            "nombreAcudiente: es obligatorio",
            "concepto: es obligatorio",
            "concepto: debe tener máximo 200 caracteres",
            "monto: debe ser mayor que 0",
            "monto: negativo",
            "fechaPago: es obligatoria",
            "fechaPago: no puede ser posterior a hoy",
            "metodo: es obligatorio",
            "referenciaWompi: solo aplica a pagos en línea (PSE, Nequi o Tarjeta)"})
    void unDtoInvalidoLanzaUnaExcepcionClara(String caso) {
        ComprobantePagoDTO invalido = switch (caso) {
            case "numeroComprobante: es obligatorio" -> con(pagoManual(), b -> b.numeroComprobante(" "));
            case "fechaEmision: es obligatoria" -> con(pagoManual(), b -> b.fechaEmision(null));
            case "nombreDeportista: es obligatorio" -> con(pagoManual(), b -> b.nombreDeportista(""));
            case "nombreAcudiente: es obligatorio" -> con(pagoManual(), b -> b.nombreAcudiente(null));
            case "concepto: es obligatorio" -> con(pagoManual(), b -> b.concepto(""));
            case "concepto: debe tener máximo 200 caracteres" -> con(pagoManual(), b -> b.concepto("x".repeat(201)));
            case "monto: debe ser mayor que 0" -> con(pagoManual(), b -> b.monto(0));
            case "monto: negativo" -> con(pagoManual(), b -> b.monto(-90_000));
            case "fechaPago: es obligatoria" -> con(pagoManual(), b -> b.fechaPago(null));
            case "fechaPago: no puede ser posterior a hoy" -> con(pagoManual(), b -> b.fechaPago(LocalDate.of(2026, 10, 29)));
            case "metodo: es obligatorio" -> con(pagoManual(), b -> b.metodo(null));
            default -> con(pagoManual(), b -> b.referenciaWompi("12345-1698765432-49201"));
        };
        String esperado = caso.equals("monto: negativo") ? "monto: debe ser mayor que 0" : caso;

        ComprobanteInvalidoException e = catchThrowableOfType(ComprobanteInvalidoException.class, () -> generador.generar(invalido));

        assertThat(e.getErrores()).containsExactly(esperado);
        assertThat(e.getMessage()).isEqualTo("Los datos del comprobante no son válidos: " + esperado);
        // Nombra campos, nunca valores
        assertThat(e.getMessage()).doesNotContain("Santiago", "Marta", "90000");
    }

    @Test
    void juntaTodosLosErroresOrdenados() {
        ComprobanteInvalidoException e = catchThrowableOfType(ComprobanteInvalidoException.class, () -> generador.generar(
                con(pagoManual(), b -> b.monto(0).concepto(" ").nombreDeportista(null))));

        assertThat(e.getErrores()).containsExactly(
                "concepto: es obligatorio", "monto: debe ser mayor que 0", "nombreDeportista: es obligatorio");
    }

    /** "Hoy" es el día en Colombia: a las 10 p. m. del 28 en Bogotá ya es 29 en UTC. */
    @Test
    void laFechaDePagoSeComparaConHoyEnColombia() {
        ComprobantePdfGenerator nocheEnBogota = generador(CLUB, Instant.parse("2026-10-29T03:00:00Z"));

        assertThat(nocheEnBogota.generar(con(pagoManual(), b -> b.fechaPago(LocalDate.of(2026, 10, 28))))).isNotEmpty();
        ComprobanteInvalidoException e = catchThrowableOfType(ComprobanteInvalidoException.class,
                () -> nocheEnBogota.generar(con(pagoManual(), b -> b.fechaPago(LocalDate.of(2026, 10, 29)))));
        assertThat(e.getErrores()).containsExactly("fechaPago: no puede ser posterior a hoy");
    }

    @Test
    void datosNulosLanzanComprobanteInvalido() {
        assertThat(catchThrowableOfType(ComprobanteInvalidoException.class, () -> generador.generar(null)).getErrores())
                .containsExactly("datos: son obligatorios");
    }

    @Test
    void metodoDesdeLaEtiquetaDelFrontend() {
        assertThat(MetodoPago.desdeEtiqueta("Nequi")).isEqualTo(MetodoPago.NEQUI);
        assertThat(MetodoPago.desdeEtiqueta(" transferencia ")).isEqualTo(MetodoPago.TRANSFERENCIA);
        assertThat(MetodoPago.EFECTIVO.enLinea()).isFalse();
        assertThat(MetodoPago.TARJETA.enLinea()).isTrue();
        assertThat(catchThrowableOfType(ComprobanteInvalidoException.class, () -> MetodoPago.desdeEtiqueta("Bitcoin")).getErrores())
                .containsExactly("metodo: debe ser Efectivo, Transferencia, PSE, Nequi o Tarjeta");
    }

    // --- Apoyo ---

    private static ComprobantePdfGenerator generador(ClubProperties club, Instant ahora) {
        return new ComprobantePdfGenerator(club, Clock.fixed(ahora, FormatoColombiano.ZONA));
    }

    private static ComprobantePagoDTO pagoManual() {
        return new ComprobantePagoDTO("FD-2026-000123", AHORA, "Santiago Gómez Ríos", "Sub-12", "Marta Ríos",
                "Mensualidad octubre", 90_000, LocalDate.of(2026, 10, 27), MetodoPago.EFECTIVO, null);
    }

    private static String texto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private static void guardarEjemplo(String nombre, byte[] pdf) throws IOException {
        Files.createDirectories(EJEMPLOS);
        Files.write(EJEMPLOS.resolve(nombre), pdf);
    }

    /** Copia del DTO con algunos campos cambiados. */
    private static ComprobantePagoDTO con(ComprobantePagoDTO base, UnaryOperator<Copia> cambios) {
        return cambios.apply(new Copia(base)).construir();
    }

    private static final class Copia {
        private String numeroComprobante;
        private Instant fechaEmision;
        private String nombreDeportista;
        private String categoria;
        private String nombreAcudiente;
        private String concepto;
        private long monto;
        private LocalDate fechaPago;
        private MetodoPago metodo;
        private String referenciaWompi;

        Copia(ComprobantePagoDTO d) {
            numeroComprobante = d.numeroComprobante();
            fechaEmision = d.fechaEmision();
            nombreDeportista = d.nombreDeportista();
            categoria = d.categoria();
            nombreAcudiente = d.nombreAcudiente();
            concepto = d.concepto();
            monto = d.monto();
            fechaPago = d.fechaPago();
            metodo = d.metodo();
            referenciaWompi = d.referenciaWompi();
        }

        Copia numeroComprobante(String v) { numeroComprobante = v; return this; }
        Copia fechaEmision(Instant v) { fechaEmision = v; return this; }
        Copia nombreDeportista(String v) { nombreDeportista = v; return this; }
        Copia categoria(String v) { categoria = v; return this; }
        Copia nombreAcudiente(String v) { nombreAcudiente = v; return this; }
        Copia concepto(String v) { concepto = v; return this; }
        Copia monto(long v) { monto = v; return this; }
        Copia fechaPago(LocalDate v) { fechaPago = v; return this; }
        Copia metodo(MetodoPago v) { metodo = v; return this; }
        Copia referenciaWompi(String v) { referenciaWompi = v; return this; }

        ComprobantePagoDTO construir() {
            return new ComprobantePagoDTO(numeroComprobante, fechaEmision, nombreDeportista, categoria, nombreAcudiente,
                    concepto, monto, fechaPago, metodo, referenciaWompi);
        }
    }
}
