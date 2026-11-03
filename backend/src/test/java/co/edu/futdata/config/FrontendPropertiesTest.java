package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FrontendPropertiesTest {

    @Test
    void armaElEnlaceDeCrearContrasenaSinDobleBarra() {
        FrontendProperties p = new FrontendProperties(" http://localhost:5173/ ");
        assertThat(p.configurada()).isTrue();
        assertThat(p.enlaceCrearContrasena("abc123"))
                .isEqualTo("http://localhost:5173/crear-contrasena?token=abc123");
    }

    @Test
    void codificaElToken() {
        assertThat(new FrontendProperties("https://futdata.app").enlaceCrearContrasena("a b&c"))
                .isEqualTo("https://futdata.app/crear-contrasena?token=a+b%26c");
    }

    @Test
    void sinUrlNoEstaConfiguradaYNoArmaEnlace() {
        for (String vacia : new String[] {null, "", "   "}) {
            FrontendProperties p = new FrontendProperties(vacia);
            assertThat(p.configurada()).isFalse();
            assertThatThrownBy(() -> p.enlaceCrearContrasena("abc")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void sinTokenNoArmaEnlace() {
        assertThatThrownBy(() -> new FrontendProperties("http://localhost:5173").enlaceCrearContrasena(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
