package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReglasDocumentoTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDB, 0};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);

    private static byte[] conTamano(byte[] firma, int tamano) {
        byte[] b = new byte[tamano];
        System.arraycopy(firma, 0, b, 0, firma.length);
        return b;
    }

    @Nested
    class Tipos {

        @ParameterizedTest
        @ValueSource(strings = {"DOCUMENTO_IDENTIDAD", "CERTIFICADO_ESTUDIO", "FORMULARIO_INSCRIPCION",
                "AUTORIZACION_HABEAS_DATA", "PAZ_Y_SALVO", "CARTA_RENUNCIA", "TRASPASO_NACIONAL"})
        void losSieteTiposSonValidos(String tipo) {
            assertThat(ReglasDocumento.validarTipo(tipo)).isEmpty();
            assertThat(ReglasDocumento.etiqueta(tipo)).isNotBlank();
        }

        @Test
        void sonExactamenteLosSieteYLosCuatroObligatoriosVanPrimero() {
            assertThat(ReglasDocumento.TIPOS).hasSize(7).doesNotHaveDuplicates();
            assertThat(ReglasDocumento.TIPOS.subList(0, 4))
                    .containsExactlyElementsOf(ReglasDeportista.DOCUMENTOS_OBLIGATORIOS);
        }

        @Test
        void losDosListadosNoSeSeparan() {
            // ReglasDeportista.DOCUMENTOS_OBLIGATORIOS decide estadoInscripcion; ReglasDocumento.TIPOS decide qué se puede subir
            assertThat(ReglasDeportista.DOCUMENTOS_OBLIGATORIOS).hasSize(4).doesNotHaveDuplicates();
            assertThat(ReglasDocumento.TIPOS).containsAll(ReglasDeportista.DOCUMENTOS_OBLIGATORIOS);
            assertThat(ReglasDocumento.TIPOS).hasSize(7);
            // y todos los tipos tienen etiqueta y pasan la validación
            ReglasDocumento.TIPOS.forEach(t -> {
                assertThat(ReglasDocumento.validarTipo(t)).isEmpty();
                assertThat(ReglasDocumento.etiqueta(t)).isNotBlank();
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"FICHA_MEDICA", "PERMISO", "CONTRATO", "OTRO", "documento_identidad", "Paz_y_salvo", " PAZ_Y_SALVO"})
        void unTipoQueElClubYaNoPideOEscritoDistintoSeRechaza(String tipo) {
            assertThat(ReglasDocumento.validarTipo(tipo)).containsOnlyKeys("tipo");
        }

        @Test
        void nuloOEnBlancoEsObligatorio() {
            assertThat(ReglasDocumento.validarTipo(null)).containsEntry("tipo", "es obligatorio");
            assertThat(ReglasDocumento.validarTipo("  ")).containsEntry("tipo", "es obligatorio");
        }

        @Test
        void lasEtiquetasSonLasDelFrontend() {
            assertThat(ReglasDocumento.etiqueta("DOCUMENTO_IDENTIDAD")).isEqualTo("Documento de identidad");
            assertThat(ReglasDocumento.etiqueta("CERTIFICADO_ESTUDIO")).isEqualTo("Certificado de estudio");
            assertThat(ReglasDocumento.etiqueta("FORMULARIO_INSCRIPCION")).isEqualTo("Formulario de inscripción");
            assertThat(ReglasDocumento.etiqueta("AUTORIZACION_HABEAS_DATA")).isEqualTo("Habeas Data");
            assertThat(ReglasDocumento.etiqueta("PAZ_Y_SALVO")).isEqualTo("Paz y salvo");
            assertThat(ReglasDocumento.etiqueta("CARTA_RENUNCIA")).isEqualTo("Carta de renuncia");
            assertThat(ReglasDocumento.etiqueta("TRASPASO_NACIONAL")).isEqualTo("Traspaso nacional");
        }

        @Test
        void unTipoDesconocidoNoTieneEtiqueta() {
            assertThatThrownBy(() -> ReglasDocumento.etiqueta("OTRO")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ReglasDocumento.etiqueta(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Archivo {

        @Test
        void reconocePdfJpgYPngPorSusBytes() {
            assertThat(ReglasDocumento.tipoPorContenido(PDF)).contains("application/pdf");
            assertThat(ReglasDocumento.tipoPorContenido(JPG)).contains("image/jpeg");
            assertThat(ReglasDocumento.tipoPorContenido(PNG)).contains("image/png");
            assertThat(ReglasDocumento.validarArchivo(PDF)).isEmpty();
            assertThat(ReglasDocumento.validarArchivo(JPG)).isEmpty();
            assertThat(ReglasDocumento.validarArchivo(PNG)).isEmpty();
        }

        @Test
        void laExtensionSaleDeLaFirmaNoDelNombre() {
            assertThat(ReglasDocumento.extension("application/pdf")).isEqualTo(".pdf");
            assertThat(ReglasDocumento.extension("image/jpeg")).isEqualTo(".jpg");
            assertThat(ReglasDocumento.extension("image/png")).isEqualTo(".png");
            assertThatThrownBy(() -> ReglasDocumento.extension("image/gif")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ReglasDocumento.extension(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void extensionMentirosaUnPdfLlamadoJpgSeGuardaComoPdf() {
            // El nombre dice .jpg pero los bytes son de un PDF: manda la firma
            String tipo = ReglasDocumento.tipoPorContenido(PDF).orElseThrow();
            UUID deportista = UUID.randomUUID();
            UUID archivo = UUID.randomUUID();
            assertThat(ReglasDocumento.ruta(deportista, archivo, tipo)).isEqualTo(deportista + "/" + archivo + ".pdf");
            assertThat(ReglasDocumento.limpiarNombre("foto.jpg")).isEqualTo("foto.jpg"); // el nombre es solo informativo
        }

        @Test
        void extensionMentirosaUnTextoLlamadoPdfSeRechaza() {
            byte[] texto = "MZ esto es un ejecutable".getBytes(StandardCharsets.US_ASCII);
            assertThat(ReglasDocumento.tipoPorContenido(texto)).isEmpty();
            assertThat(ReglasDocumento.validarArchivo(texto)).containsOnlyKeys("archivo");
        }

        @Test
        void unaFirmaCortadaOUnGifNoSirven() {
            for (byte[] b : new byte[][] {
                    "%PDF".getBytes(StandardCharsets.US_ASCII),            // PDF incompleto (falta el guion)
                    "GIF89a".getBytes(StandardCharsets.US_ASCII),
                    {(byte) 0xFF, (byte) 0xD8},                              // JPG incompleto
                    {(byte) 0x89, 0x50, 0x4E, 0x47}}) {                      // PNG incompleto
                assertThat(ReglasDocumento.tipoPorContenido(b)).isEmpty();
                assertThat(ReglasDocumento.validarArchivo(b)).containsKey("archivo");
            }
        }

        @Test
        void unPdfNoEmpiezaAunqueTengaLaFirmaMasAdelante() {
            byte[] desplazado = "  %PDF-1.4".getBytes(StandardCharsets.US_ASCII);
            assertThat(ReglasDocumento.tipoPorContenido(desplazado)).isEmpty();
        }

        @Test
        void archivoDeCeroBytesOnuloEsUnError() {
            assertThat(ReglasDocumento.validarArchivo(new byte[0])).containsEntry("archivo", "está vacío");
            assertThat(ReglasDocumento.validarArchivo(null)).containsEntry("archivo", "está vacío");
        }

        @Test
        void elLimiteEsExactamente5MbParaLosTresTipos() {
            assertThat(ReglasDocumento.TAMANO_MAXIMO).isEqualTo(5_242_880);
            for (byte[] firma : new byte[][] {PDF, JPG, PNG}) {
                assertThat(ReglasDocumento.validarArchivo(conTamano(firma, 5 * 1024 * 1024))).isEmpty();
                assertThat(ReglasDocumento.validarArchivo(conTamano(firma, 5 * 1024 * 1024 + 1)))
                        .containsEntry("archivo", "pesa 5242881 bytes; el máximo es 5 MB (5242880 bytes)");
            }
        }

        @Test
        void unArchivoDeUnByteConFirmaIncompletaSeRechazaPorFirmaNoPorTamano() {
            assertThat(ReglasDocumento.validarArchivo(new byte[] {0x25})).containsKey("archivo");
        }

        @Test
        void elContentTypeDelClienteSeIgnoraDecideSoloLaFirma() {
            // Un PNG que el cliente declaró como PDF (decisión 15): se acepta y se detecta como PNG. El método ya no
            // recibe el tipo declarado, así que no hay forma de que influya.
            assertThat(ReglasDocumento.validarArchivo(PNG)).isEmpty();
            String detectado = ReglasDocumento.tipoPorContenido(PNG).orElseThrow();
            assertThat(detectado).isEqualTo("image/png");
            assertThat(ReglasDocumento.extension(detectado)).isEqualTo(".png");
            assertThat(Arrays.stream(ReglasDocumento.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals("validarArchivo")))
                    .allSatisfy(m -> assertThat(m.getParameterTypes()).containsExactly(byte[].class));
        }

        @Test
        void unaFirmaIncorrectaSeRechazaSinImportarComoSeLlameElArchivo() {
            assertThat(ReglasDocumento.validarArchivo("hola".getBytes(StandardCharsets.US_ASCII)))
                    .containsEntry("archivo", "debe ser un PDF, JPG o PNG (se revisa el contenido del archivo, no su extensión)");
        }

        @Test
        void laRutaEsIdDeportistaBarraUuidYExtension() {
            UUID deportista = UUID.fromString("11111111-1111-4111-8111-111111111111");
            UUID archivo = UUID.fromString("22222222-2222-4222-8222-222222222222");
            assertThat(ReglasDocumento.ruta(deportista, archivo, "image/png"))
                    .isEqualTo("11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222.png");
            assertThat(ReglasDocumento.ruta(deportista, archivo, "image/jpeg")).endsWith(".jpg");
        }

        @Test
        void laRutaNuncaLlevaElNombreDelCliente() {
            String ruta = ReglasDocumento.ruta(UUID.randomUUID(), UUID.randomUUID(), "application/pdf");
            // Dos segmentos: deportista y archivo; nada de ".." ni de barras extra
            assertThat(ruta.split("/")).hasSize(2);
            assertThat(ruta).doesNotContain("..").matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.pdf");
        }
    }

    @Nested
    class NombreOriginal {

        @Test
        void quitaCarpetasDeWindowsYUnix() {
            assertThat(ReglasDocumento.limpiarNombre("C:\\Users\\ana\\cedula.pdf")).isEqualTo("cedula.pdf");
            assertThat(ReglasDocumento.limpiarNombre("../../etc/passwd")).isEqualTo("passwd");
            assertThat(ReglasDocumento.limpiarNombre("/tmp/scan 1.png")).isEqualTo("scan 1.png");
        }

        @Test
        void quitaCaracteresDeControlYEspacios() {
            assertThat(ReglasDocumento.limpiarNombre("  ced\u0000ula\n.pdf \t")).isEqualTo("cedula.pdf");
        }

        @Test
        void vacioONuloQuedaComoDocumento() {
            assertThat(ReglasDocumento.limpiarNombre(null)).isEqualTo("documento");
            assertThat(ReglasDocumento.limpiarNombre("   ")).isEqualTo("documento");
            assertThat(ReglasDocumento.limpiarNombre("carpeta/")).isEqualTo("documento");
        }

        @Test
        void seRecortaA255Caracteres() {
            char[] largo = new char[400];
            Arrays.fill(largo, 'a');
            assertThat(ReglasDocumento.limpiarNombre(new String(largo))).hasSize(255);
        }

        @Test
        void conservaTildesYEspaciosInternos() {
            assertThat(ReglasDocumento.limpiarNombre("Certificado de estudio año 2026.pdf"))
                    .isEqualTo("Certificado de estudio año 2026.pdf");
        }
    }

    @Nested
    class FechaDeVencimiento {

        @Test
        void esOpcional() {
            assertThat(ReglasDocumento.validarFechaVencimiento(null, HOY)).isEmpty();
            assertThat(ReglasDocumento.validarFechaVencimiento("", HOY)).isEmpty();
            assertThat(ReglasDocumento.validarFechaVencimiento("   ", HOY)).isEmpty();
            assertThat(ReglasDocumento.parsearFecha(null)).isEmpty();
        }

        @Test
        void hoyYElFuturoSonValidos() {
            assertThat(ReglasDocumento.validarFechaVencimiento("2026-11-05", HOY)).isEmpty();
            assertThat(ReglasDocumento.validarFechaVencimiento("2026-11-06", HOY)).isEmpty();
            assertThat(ReglasDocumento.validarFechaVencimiento("2030-02-28", HOY)).isEmpty();
            assertThat(ReglasDocumento.parsearFecha("2026-12-01")).contains(LocalDate.of(2026, 12, 1));
        }

        @Test
        void unaFechaPasadaSeRechaza() {
            assertThat(ReglasDocumento.validarFechaVencimiento("2026-11-04", HOY))
                    .containsEntry("fechaVencimiento", "no puede ser una fecha pasada");
            assertThat(ReglasDocumento.validarFechaVencimiento("2020-01-01", HOY)).containsOnlyKeys("fechaVencimiento");
        }

        @Test
        void conElRelojDeBogotaHoyEsLaFechaQueSePasa() {
            // El caller pasa LocalDate.now(reloj): ayer en Bogotá es pasado aunque en UTC fuera "hoy"
            assertThat(ReglasDocumento.validarFechaVencimiento("2026-11-05", LocalDate.of(2026, 11, 6)))
                    .containsOnlyKeys("fechaVencimiento");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-13-01", "2026-02-30", "2027-02-29", "05/11/2026", "2026-1-5", "2026-11-05T10:00",
                "hoy", "20261105", "2026-00-10"})
        void unaFechaQueNoEsRealOSinElFormatoSeRechaza(String texto) {
            assertThat(ReglasDocumento.validarFechaVencimiento(texto, HOY))
                    .containsEntry("fechaVencimiento", "debe ser una fecha válida con el formato AAAA-MM-DD");
            assertThat(ReglasDocumento.parsearFecha(texto)).isEmpty();
        }

        @Test
        void el29DeFebreroDeUnBisiestoEsReal() {
            assertThat(ReglasDocumento.validarFechaVencimiento("2028-02-29", HOY)).isEmpty();
        }
    }

    @Nested
    class ComentarioDeRechazo {

        @Test
        void unComentarioNormalSirveYSeRecorta() {
            assertThat(ReglasDocumento.validarComentario("La imagen está borrosa.")).isEmpty();
            assertThat(ReglasDocumento.limpiarComentario("  La imagen está borrosa.\n")).isEqualTo("La imagen está borrosa.");
        }

        @Test
        void vacioNuloOSoloEspaciosEsObligatorio() {
            for (String c : new String[] {null, "", "   ", "\t\n  "}) {
                assertThat(ReglasDocumento.validarComentario(c))
                        .containsEntry("comentario", "es obligatorio al rechazar un documento");
            }
            assertThat(ReglasDocumento.limpiarComentario(null)).isEmpty();
        }

        @Test
        void de1a300CaracteresEsValido() {
            assertThat(ReglasDocumento.validarComentario("x")).isEmpty();
            assertThat(ReglasDocumento.validarComentario("x".repeat(300))).isEmpty();
        }

        @Test
        void de301CaracteresSeRechaza() {
            assertThat(ReglasDocumento.validarComentario("x".repeat(301)))
                    .containsEntry("comentario", "no puede pasar de 300 caracteres");
        }

        @Test
        void losEspaciosDeLosExtremosNoCuentanPeroLosInternosSi() {
            assertThat(ReglasDocumento.validarComentario("  " + "x".repeat(300) + "  ")).isEmpty();
            assertThat(ReglasDocumento.validarComentario("x".repeat(150) + "  " + "y".repeat(150))).containsKey("comentario");
        }
    }

    @Nested
    class QuienLoSubio {

        @Test
        void administradorYAcudienteTienenSuEtiqueta() {
            assertThat(ReglasDocumento.cargadoPor("ADMINISTRADOR")).isEqualTo("Administrador");
            assertThat(ReglasDocumento.cargadoPor("ACUDIENTE")).isEqualTo("Acudiente");
        }

        @Test
        void cualquierOtroRolNoTieneEtiqueta() {
            for (String rol : new String[] {"ENTRENADOR", "TESORERO", "", "administrador", null}) {
                assertThat(ReglasDocumento.cargadoPor(rol)).isNull();
            }
        }
    }
}
