package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReglasFotoTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDB, 0};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    @Test
    void reconoceJpgYPngPorSusBytes() {
        assertThat(ReglasFoto.tipoPorContenido(JPG)).contains("image/jpeg");
        assertThat(ReglasFoto.tipoPorContenido(PNG)).contains("image/png");
        assertThat(ReglasFoto.extension("image/jpeg")).isEqualTo(".jpg");
        assertThat(ReglasFoto.extension("image/png")).isEqualTo(".png");
        assertThat(ReglasFoto.validar(JPG)).isEmpty();
        assertThat(ReglasFoto.validar(PNG)).isEmpty();
    }

    @Test
    void unPdfUnGifOUnaFirmaCortadaNoSonFotos() {
        for (byte[] b : new byte[][] {
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII),
                "GIF89a".getBytes(StandardCharsets.US_ASCII),
                {(byte) 0xFF, (byte) 0xD8},                     // JPG incompleto
                {(byte) 0x89, 0x50, 0x4E, 0x47}}) {             // PNG incompleto
            assertThat(ReglasFoto.tipoPorContenido(b)).isEmpty();
            assertThat(ReglasFoto.validar(b)).containsKey("archivo");
        }
    }

    @Test
    void elLimiteEsExactamente5Mb() {
        byte[] justo = new byte[5 * 1024 * 1024];
        System.arraycopy(JPG, 0, justo, 0, 4);
        byte[] unoDeMas = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPG, 0, unoDeMas, 0, 4);

        assertThat(ReglasFoto.validar(justo)).isEmpty();
        assertThat(ReglasFoto.validar(unoDeMas)).containsEntry("archivo",
                "pesa 5242881 bytes; el máximo es 5 MB (5242880 bytes)");
    }

    @Test
    void vacioONuloEsUnError() {
        assertThat(ReglasFoto.validar(new byte[0])).containsEntry("archivo", "está vacío");
        assertThat(ReglasFoto.validar(null)).containsEntry("archivo", "está vacío");
        assertThat(ReglasFoto.tipoPorContenido(null)).isEmpty();
    }
}
