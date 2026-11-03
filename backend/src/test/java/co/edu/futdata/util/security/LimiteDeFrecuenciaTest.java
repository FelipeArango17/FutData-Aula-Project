package co.edu.futdata.util.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LimiteDeFrecuenciaTest {

    private final Reloj reloj = new Reloj();

    @Test
    void unoPorMinutoDevuelveCuantoFalta() {
        LimiteDeFrecuencia limite = new LimiteDeFrecuencia(1, Duration.ofMinutes(1), reloj);

        assertThat(limite.intentar("a")).isEmpty();
        reloj.avanzar(Duration.ofSeconds(15));
        assertThat(limite.intentar("a")).contains(Duration.ofSeconds(45));
        assertThat(limite.intentar("b")).as("otra clave").isEmpty();
        reloj.avanzar(Duration.ofSeconds(45));
        assertThat(limite.intentar("a")).as("justo al minuto").isEmpty();
    }

    @Test
    void variosPorVentanaDeslizante() {
        LimiteDeFrecuencia limite = new LimiteDeFrecuencia(3, Duration.ofMinutes(1), reloj);
        for (int i = 0; i < 3; i++) {
            assertThat(limite.intentar("ip")).isEmpty();
            reloj.avanzar(Duration.ofSeconds(10));
        }
        // El primero fue hace 30 s: falta otro tanto
        assertThat(limite.intentar("ip")).contains(Duration.ofSeconds(30));
    }

    @Test
    void laEsperaMinimaEsUnSegundo() {
        LimiteDeFrecuencia limite = new LimiteDeFrecuencia(1, Duration.ofMinutes(1), reloj);
        limite.intentar("a");
        reloj.avanzar(Duration.ofMillis(59_800));
        assertThat(limite.intentar("a")).contains(Duration.ofSeconds(1));
    }

    @Test
    void liberarDevuelveElCupo() {
        LimiteDeFrecuencia limite = new LimiteDeFrecuencia(1, Duration.ofMinutes(1), reloj);
        limite.intentar("a");
        limite.liberar("a");
        assertThat(limite.intentar("a")).isEmpty();
        limite.liberar("nunca-usada"); // no falla
    }

    @Test
    void borraLasClavesVencidasCuandoHayDemasiadas() {
        LimiteDeFrecuencia limite = new LimiteDeFrecuencia(1, Duration.ofMinutes(1), reloj, 2);
        limite.intentar("a");
        limite.intentar("b");
        limite.intentar("c");
        reloj.avanzar(Duration.ofMinutes(2));
        limite.intentar("d");
        assertThat(limite.claves()).isEqualTo(1);
    }

    @Test
    void rechazaParametrosInvalidos() {
        assertThatThrownBy(() -> new LimiteDeFrecuencia(0, Duration.ofMinutes(1), reloj)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiteDeFrecuencia(1, Duration.ZERO, reloj)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Reloj extends Clock {
        private Instant ahora = Instant.parse("2026-11-02T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
