package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class VerificadorVariablesProdTest {

    private final VerificadorVariablesProd verificador = new VerificadorVariablesProd();

    @Test
    void enProdSinVariablesDiceCualesFaltan() {
        MockEnvironment entorno = new MockEnvironment().withProperty("DB_URL", "jdbc:postgresql://db/postgres");
        entorno.setActiveProfiles("prod");

        assertThatThrownBy(() -> verificador.postProcessEnvironment(entorno, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Perfil prod: faltan las variables de entorno DB_USER, DB_PASSWORD, CORS_ORIGINS, "
                        + "N8N_BASE_URL, N8N_WEBHOOK_TOKEN. Ver backend/.env.example.");
    }

    @Test
    void enProdConTodasLasVariablesArranca() {
        MockEnvironment entorno = new MockEnvironment();
        VerificadorVariablesProd.OBLIGATORIAS.forEach(variable -> entorno.setProperty(variable, "valor"));
        entorno.setActiveProfiles("prod");

        assertThatCode(() -> verificador.postProcessEnvironment(entorno, null)).doesNotThrowAnyException();
    }

    @Test
    void enLocalNoExigeVariables() {
        MockEnvironment entorno = new MockEnvironment();
        entorno.setActiveProfiles("local");

        assertThatCode(() -> verificador.postProcessEnvironment(entorno, null)).doesNotThrowAnyException();
    }
}
