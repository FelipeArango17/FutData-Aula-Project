package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Política única de contraseña, igual a la de validaciones.js del frontend. */
class ReglasContrasenaTest {

    @Test
    void elMensajeEsElMismoDelFrontend() {
        assertThat(ReglasContrasena.MENSAJE)
                .isEqualTo("La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefg1", "1234567a", "Ñandú2026", "con espacio 1", "  ab12  "})
    void aceptaLasQueCumplen(String contrasena) {
        assertThat(ReglasContrasena.cumple(contrasena)).isTrue();
        assertThat(ReglasContrasena.validar(contrasena)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc1", "abcdef1", "abcdefgh", "12345678", "!!!!!!!!1", "        "})
    void rechazaLasCortasOSinLetraOSinNumero(String contrasena) {
        assertThat(ReglasContrasena.validar(contrasena))
                .containsExactly(Map.entry("contrasena", ReglasContrasena.MENSAJE));
    }

    @Test
    void nullNoCumple() {
        assertThat(ReglasContrasena.cumple(null)).isFalse();
        assertThat(ReglasContrasena.validar(null)).containsOnlyKeys("contrasena");
    }

    @Test
    void entre8Y72Incluidos() {
        assertThat(ReglasContrasena.cumple("a1" + "x".repeat(6))).as("8").isTrue();
        assertThat(ReglasContrasena.cumple("a1" + "x".repeat(70))).as("72").isTrue();
        assertThat(ReglasContrasena.cumple("a1" + "x".repeat(71))).as("73").isFalse();
    }

    @Test
    void comoMucho72BytesEnUtf8_36EnesCabenY37No() {
        // La ñ ocupa 2 bytes: 36 ñ son 72 bytes (el tope) y 37 son 74
        assertThat(ReglasContrasena.cabeEnBytes("ñ".repeat(36))).isTrue();
        assertThat(ReglasContrasena.cabeEnBytes("ñ".repeat(37))).isFalse();
    }

    @Test
    void conEnesLaPoliticaCompletaTambienCuentaLosBytes() {
        // Necesita un número: 35 ñ + "12" = 72 bytes pasa; 36 ñ + "1" = 73 bytes no (aunque son 37 caracteres)
        assertThat(ReglasContrasena.cumple("ñ".repeat(35) + "12")).isTrue();
        assertThat(ReglasContrasena.validar("ñ".repeat(36) + "1"))
                .containsExactly(Map.entry("contrasena", ReglasContrasena.MENSAJE));
        // 72 caracteres ASCII siguen pasando
        assertThat(ReglasContrasena.cumple("a1" + "x".repeat(70))).isTrue();
    }

    @Test
    void losDigitosNoAsciiNoCuentanComoNumeroComoEnJavaScript() {
        // "٣" es el 3 arábigo-índico: \d de JavaScript (sin la bandera u) no lo reconoce
        assertThat(ReglasContrasena.cumple("abcdefg٣")).isFalse();
    }

    @Test
    void elMensajeNoRepiteLaContrasena() {
        assertThat(ReglasContrasena.validar("secreto").values()).noneMatch(m -> m.contains("secreto"));
    }
}
