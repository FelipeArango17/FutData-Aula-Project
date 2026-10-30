package co.edu.futdata.domain.rules;

import java.time.Duration;

public final class ReglasVerificacion {
    public static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);
    public static final Duration BLOQUEO = Duration.ofMinutes(15);
    public static final Duration INTERVALO_REENVIO = Duration.ofMinutes(1);
    public static final int MAX_INTENTOS = 5;

    private ReglasVerificacion() { }

    public static boolean formatoValido(String codigo) {
        return codigo != null && codigo.matches("\\d{6}");
    }
}
