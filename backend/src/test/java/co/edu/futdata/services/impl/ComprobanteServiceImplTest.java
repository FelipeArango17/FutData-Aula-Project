package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.edu.futdata.config.SupabaseProperties;
import co.edu.futdata.datasource.external.AlmacenamientoNoDisponibleException;
import co.edu.futdata.datasource.external.ArchivoInvalidoException;
import co.edu.futdata.datasource.external.ArchivoNoEncontradoException;
import co.edu.futdata.datasource.external.ArchivoYaExisteException;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import co.edu.futdata.util.reports.ComprobanteInvalidoException;
import co.edu.futdata.util.reports.ComprobantePagoDTO;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import co.edu.futdata.util.reports.IGeneradorComprobante;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ComprobanteServiceImpl con dobles de prueba del generador y del almacenamiento: sin PDF real ni Storage. */
class ComprobanteServiceImplTest {

    private static final byte[] PDF = "%PDF-falso".getBytes(StandardCharsets.US_ASCII);
    private static final SupabaseProperties SUPABASE = new SupabaseProperties("https://x.supabase.co", "llave",
            "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(10));

    private final GeneradorFalso generador = new GeneradorFalso();
    private final AlmacenamientoFalso almacenamiento = new AlmacenamientoFalso();
    private final ComprobanteServiceImpl servicio = new ComprobanteServiceImpl(generador, almacenamiento, SUPABASE);

    @Test
    void generaElPdfYLoSubeConLaRutaDelAnioYElNumero() {
        String ruta = servicio.generarYGuardar(datos("FD-2026-000123", LocalDate.of(2026, 10, 27)));

        assertThat(ruta).isEqualTo("2026/FD-2026-000123.pdf");
        assertThat(generador.recibidos).hasSize(1);
        assertThat(almacenamiento.subidas).containsExactly(
                new Subida("comprobantes", "2026/FD-2026-000123.pdf", PDF, IAlmacenamientoArchivos.PDF));
    }

    @Test
    void laRutaNoLlevaDatosPersonales() {
        String ruta = servicio.generarYGuardar(datos("FD-2026-000124", LocalDate.of(2026, 1, 2)));

        assertThat(ruta).matches("\\d{4}/FD-\\d{4}-\\d{6}\\.pdf")
                .doesNotContain("Santiago", "Gómez", "Marta", "@", "Mensualidad");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "FD-26-000123", "FD-2026-123", "fd-2026-000123", "FD-2026-000123/../x", "Santiago-2026"})
    void unNumeroSinElFormatoNoSeSube(String numero) {
        ComprobanteInvalidoException e = catchThrowableOfType(ComprobanteInvalidoException.class,
                () -> servicio.generarYGuardar(datos(numero, LocalDate.of(2026, 10, 27))));

        assertThat(e.getErrores()).containsExactly("numeroComprobante: debe tener el formato FD-AAAA-NNNNNN");
        assertThat(almacenamiento.subidas).isEmpty();
    }

    /** Pago del 31 de diciembre registrado en enero: el número es del año nuevo y la carpeta sale del número. */
    @Test
    void laCarpetaEsElAnioDelNumeroAunqueElPagoSeaDelAnioAnterior() {
        String ruta = servicio.generarYGuardar(datos("FD-2026-000001", LocalDate.of(2025, 12, 31)));

        assertThat(ruta).isEqualTo("2026/FD-2026-000001.pdf");
        assertThat(almacenamiento.subidas).extracting(Subida::ruta).containsExactly("2026/FD-2026-000001.pdf");
        // Y el enlace se puede pedir con esa misma ruta
        assertThat(servicio.enlaceDescarga(ruta)).isEqualTo("https://firmada.example/2026/FD-2026-000001.pdf");
    }

    @Test
    void laCarpetaNoDependeDeLaFechaDePago() {
        assertThat(servicio.generarYGuardar(datos("FD-2025-000777", LocalDate.of(2026, 1, 3)))).isEqualTo("2025/FD-2025-000777.pdf");
        assertThat(servicio.generarYGuardar(datos("FD-2026-000778", LocalDate.of(2026, 10, 27)))).isEqualTo("2026/FD-2026-000778.pdf");
    }

    @Test
    void siElGeneradorRechazaLosDatosNoSeSubeNada() {
        generador.error = new ComprobanteInvalidoException(List.of("monto: debe ser mayor que 0"));

        assertThatThrownBy(() -> servicio.generarYGuardar(datos("FD-2026-000123", LocalDate.of(2026, 10, 27))))
                .isSameAs(generador.error);
        assertThat(almacenamiento.subidas).isEmpty();
    }

    @Test
    void siYaExisteElComprobanteSePropagaLaExcepcion() {
        almacenamiento.error = new ArchivoYaExisteException("Ya existe", 409, null);

        assertThatThrownBy(() -> servicio.generarYGuardar(datos("FD-2026-000123", LocalDate.of(2026, 10, 27))))
                .isSameAs(almacenamiento.error);
    }

    @Test
    void elEnlaceUsaElBucketYLaVigenciaConfigurados() {
        assertThat(servicio.enlaceDescarga("2026/FD-2026-000123.pdf")).isEqualTo("https://firmada.example/2026/FD-2026-000123.pdf");
        assertThat(almacenamiento.firmas).containsExactly(new Firma("comprobantes", "2026/FD-2026-000123.pdf", Duration.ofMinutes(5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2026/FD-2025-000123.pdf", "2026/FD-2026-000123", "../2026/FD-2026-000123.pdf",
            "/2026/FD-2026-000123.pdf", "2026/FD-2026-000123.pdf?x", "otros/archivo.pdf", "https://x.supabase.co/2026/FD-2026-000123.pdf"})
    void elEnlaceRechazaRutasQueNoSonDeComprobantes(String ruta) {
        assertThatThrownBy(() -> servicio.enlaceDescarga(ruta)).isInstanceOf(ArchivoInvalidoException.class)
                .hasMessage("La ruta del comprobante no tiene el formato AAAA/FD-AAAA-NNNNNN.pdf");
        assertThatThrownBy(() -> servicio.enlaceDescarga(null)).isInstanceOf(ArchivoInvalidoException.class);
        assertThat(almacenamiento.firmas).isEmpty();
    }

    @Test
    void lasFallasDeStorageAlFirmarSePropagan() {
        almacenamiento.error = new ArchivoNoEncontradoException("No existe", 404, null);
        assertThatThrownBy(() -> servicio.enlaceDescarga("2026/FD-2026-000123.pdf")).isSameAs(almacenamiento.error);

        almacenamiento.error = new AlmacenamientoNoDisponibleException("No disponible", 503, null);
        assertThatThrownBy(() -> servicio.enlaceDescarga("2026/FD-2026-000123.pdf")).isSameAs(almacenamiento.error);
    }

    // --- Dobles de prueba ---

    private static ComprobantePagoDTO datos(String numero, LocalDate fechaPago) {
        return new ComprobantePagoDTO(numero, Instant.parse("2026-10-28T21:35:00Z"), "Santiago Gómez Ríos", "Sub-12",
                "Marta Ríos", "Mensualidad octubre", 90_000, fechaPago, MetodoPago.EFECTIVO, null);
    }

    private static final class GeneradorFalso implements IGeneradorComprobante {
        final List<ComprobantePagoDTO> recibidos = new ArrayList<>();
        RuntimeException error;

        @Override
        public byte[] generar(ComprobantePagoDTO datos) {
            if (error != null) {
                throw error;
            }
            recibidos.add(datos);
            return PDF;
        }
    }

    private record Subida(String bucket, String ruta, byte[] contenido, String contentType) {
        @Override
        public boolean equals(Object otro) {
            return otro instanceof Subida s && bucket.equals(s.bucket) && ruta.equals(s.ruta)
                    && Arrays.equals(contenido, s.contenido) && contentType.equals(s.contentType);
        }

        @Override
        public int hashCode() {
            return ruta.hashCode();
        }
    }

    private record Firma(String bucket, String ruta, Duration vigencia) {
    }

    private static final class AlmacenamientoFalso implements IAlmacenamientoArchivos {
        final List<Subida> subidas = new ArrayList<>();
        final List<Firma> firmas = new ArrayList<>();
        RuntimeException error;

        @Override
        public String subir(String bucket, String ruta, byte[] contenido, String contentType) {
            if (error != null) {
                throw error;
            }
            subidas.add(new Subida(bucket, ruta, contenido, contentType));
            return ruta;
        }

        @Override
        public String urlFirmada(String bucket, String ruta, Duration vigencia) {
            if (error != null) {
                throw error;
            }
            firmas.add(new Firma(bucket, ruta, vigencia));
            return "https://firmada.example/" + ruta;
        }
    }
}
