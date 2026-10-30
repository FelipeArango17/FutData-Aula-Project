package co.edu.futdata.persistence.impl;

import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Omite la clase de prueba si no hay Docker ni un PostgreSQL local configurado ({@link PostgresDePrueba#VARIABLE_URL}),
 * con un mensaje claro en vez de un error: el build sigue pasando en un equipo sin Docker Desktop. En el CI
 * (GitHub Actions) Docker sí está, así que esas pruebas corren.
 *
 * <p>Uso: {@code @ExtendWith(DockerDisponible.class)} en la clase.
 */
public class DockerDisponible implements ExecutionCondition {

    static final String MENSAJE = "Docker no está disponible: se omiten las pruebas de integración con PostgreSQL "
            + "(Testcontainers). Abre Docker Desktop y vuelve a correr ./mvnw verify para ejecutarlas, o define "
            + PostgresDePrueba.VARIABLE_URL + " con un PostgreSQL local de pruebas.";

    private static Boolean disponible;

    private final BooleanSupplier hayDocker;

    public DockerDisponible() {
        this(() -> PostgresDePrueba.usaBaseLocal() || hayDocker());
    }

    /** Para las pruebas de esta condición. */
    DockerDisponible(BooleanSupplier hayDocker) {
        this.hayDocker = hayDocker;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext contexto) {
        if (hayDocker.getAsBoolean()) {
            return ConditionEvaluationResult.enabled("Docker disponible");
        }
        System.out.println("[AVISO] " + MENSAJE);
        return ConditionEvaluationResult.disabled(MENSAJE);
    }

    private static synchronized boolean hayDocker() {
        if (disponible == null) {
            try {
                disponible = DockerClientFactory.instance().isDockerAvailable();
            } catch (RuntimeException e) {
                disponible = false;
            }
        }
        return disponible;
    }
}
