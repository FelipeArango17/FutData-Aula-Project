package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

/** Sin Docker, las pruebas con Testcontainers se omiten con un mensaje claro (no fallan). */
class DockerDisponibleTest {

    @Test
    void sinDockerOmiteLaPruebaConUnMensajeClaro() {
        ConditionEvaluationResult resultado = new DockerDisponible(() -> false).evaluateExecutionCondition(null);

        assertThat(resultado.isDisabled()).isTrue();
        assertThat(resultado.getReason()).hasValue(DockerDisponible.MENSAJE);
        assertThat(DockerDisponible.MENSAJE).contains("Docker no está disponible", "se omiten", "./mvnw verify");
    }

    @Test
    void conDockerLaPruebaCorre() {
        assertThat(new DockerDisponible(() -> true).evaluateExecutionCondition(null).isDisabled()).isFalse();
    }
}
