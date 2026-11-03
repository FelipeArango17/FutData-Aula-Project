package co.edu.futdata.util.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos por clave en una ventana deslizante, <b>en memoria</b>: como mucho {@code maximo} intentos por
 * clave en cada {@code ventana}. Lo usan el reenvío del enlace (1 por minuto por entrenador) y, en el paso 3, el
 * endpoint público de crear la contraseña.
 *
 * <p><b>Limitaciones</b> (decisión sin migración): se pierde al reiniciar la aplicación y no se comparte entre
 * instancias. Con una sola instancia en Render alcanza; con varias, cada una llevaría su propia cuenta.
 *
 * <p>Para que la memoria no crezca sin límite, cuando hay más de {@code maximoClaves} claves se borran las que ya no
 * tienen intentos dentro de la ventana.
 */
public final class LimiteDeFrecuencia {

    static final int MAXIMO_CLAVES_POR_DEFECTO = 10_000;

    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;
    private final int maximoClaves;
    private final ConcurrentHashMap<Object, Deque<Instant>> intentos = new ConcurrentHashMap<>();

    public LimiteDeFrecuencia(int maximo, Duration ventana, Clock reloj) {
        this(maximo, ventana, reloj, MAXIMO_CLAVES_POR_DEFECTO);
    }

    LimiteDeFrecuencia(int maximo, Duration ventana, Clock reloj, int maximoClaves) {
        if (maximo < 1 || ventana == null || ventana.isNegative() || ventana.isZero()) {
            throw new IllegalArgumentException("El límite necesita un máximo de al menos 1 y una ventana positiva");
        }
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
        this.maximoClaves = maximoClaves;
    }

    /**
     * Registra un intento de {@code clave} si cabe en la ventana.
     *
     * @return vacío si se registró; si no, cuánto falta para que se libere un cupo (al menos 1 s)
     */
    public Optional<Duration> intentar(Object clave) {
        Instant ahora = reloj.instant();
        if (intentos.size() > maximoClaves) {
            purgar(ahora);
        }
        Duration[] espera = new Duration[1];
        intentos.compute(clave, (k, registro) -> {
            Deque<Instant> lista = registro == null ? new ArrayDeque<>() : registro;
            quitarVencidos(lista, ahora);
            if (lista.size() >= maximo) {
                Duration falta = Duration.between(ahora, lista.peekFirst().plus(ventana));
                espera[0] = falta.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : falta;
            } else {
                lista.addLast(ahora);
            }
            return lista;
        });
        return Optional.ofNullable(espera[0]);
    }

    /** Deshace el último intento de {@code clave} (la operación falló antes de hacer algo que valga la pena limitar). */
    public void liberar(Object clave) {
        intentos.computeIfPresent(clave, (k, lista) -> {
            lista.pollLast();
            return lista.isEmpty() ? null : lista;
        });
    }

    /** Número de claves guardadas (para las pruebas). */
    int claves() {
        return intentos.size();
    }

    private void purgar(Instant ahora) {
        intentos.keySet().forEach(clave -> intentos.computeIfPresent(clave, (k, lista) -> {
            quitarVencidos(lista, ahora);
            return lista.isEmpty() ? null : lista;
        }));
    }

    private void quitarVencidos(Deque<Instant> lista, Instant ahora) {
        Instant limite = ahora.minus(ventana);
        while (!lista.isEmpty() && !lista.peekFirst().isAfter(limite)) {
            lista.pollFirst();
        }
    }
}
