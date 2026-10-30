package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeneradorCodigoTest {
    @Test
    void generaCodigoDeSeisDigitosYValidaSuHash() {
        GeneradorCodigo generador = new GeneradorCodigo();
        String codigo = generador.generar();
        String hash = generador.hash(codigo);

        assertThat(codigo).matches("\\d{6}");
        assertThat(hash).doesNotContain(codigo);
        assertThat(generador.coincide(codigo, hash)).isTrue();
        assertThat(generador.coincide("000000".equals(codigo) ? "000001" : "000000", hash)).isFalse();
    }
}
