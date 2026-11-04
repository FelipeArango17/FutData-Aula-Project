package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
                        + "N8N_BASE_URL, N8N_WEBHOOK_TOKEN, SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, FRONTEND_URL. "
                        + "Ver backend/.env.example.");
    }

    @Test
    void enProdConTodasLasVariablesArranca() {
        MockEnvironment entorno = new MockEnvironment();
        VerificadorVariablesProd.OBLIGATORIAS.forEach(variable -> entorno.setProperty(variable, "valor"));
        entorno.setActiveProfiles("prod");

        assertThatCode(() -> verificador.postProcessEnvironment(entorno, null)).doesNotThrowAnyException();
    }

    /** Sin n8n o Supabase Storage, prod no arranca: no se podría enviar el código 2FA ni guardar comprobantes. */
    @ParameterizedTest
    @ValueSource(strings = {"N8N_BASE_URL", "N8N_WEBHOOK_TOKEN", "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY", "FRONTEND_URL"})
    void enProdSinUnaVariableDeN8nOSupabaseNoArranca(String variable) {
        MockEnvironment entorno = new MockEnvironment();
        VerificadorVariablesProd.OBLIGATORIAS.forEach(obligatoria -> entorno.setProperty(obligatoria, "valor"));
        entorno.setProperty(variable, " ");
        entorno.setActiveProfiles("prod");

        assertThatThrownBy(() -> verificador.postProcessEnvironment(entorno, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Perfil prod: faltan las variables de entorno " + variable + ". Ver backend/.env.example.");
    }

    /** N8N_CRON_TOKEN es opcional: sin ella la vía por token queda deshabilitada, pero prod arranca. */
    @Test
    void elTokenDeCronNoEsObligatorio() {
        assertThat(VerificadorVariablesProd.OBLIGATORIAS).doesNotContain("N8N_CRON_TOKEN");
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
