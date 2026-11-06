package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DocumentosPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(DocumentosProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void porDefectoElBucketEsDocumentosYLaUrlDura300Segundos() {
        contexto.run(c -> {
            DocumentosProperties documentos = c.getBean(DocumentosProperties.class);
            assertThat(documentos.bucket()).isEqualTo("documentos");
            assertThat(documentos.vigenciaUrlFirmada()).isEqualTo(Duration.ofSeconds(300));
        });
    }

    @Test
    void seConfiguranPorPropiedades() {
        contexto.withPropertyValues("futdata.documentos.bucket=documentos-staging",
                        "futdata.documentos.vigencia-url-firmada=60s")
                .run(c -> {
                    DocumentosProperties documentos = c.getBean(DocumentosProperties.class);
                    assertThat(documentos.bucket()).isEqualTo("documentos-staging");
                    assertThat(documentos.vigenciaUrlFirmada()).isEqualTo(Duration.ofSeconds(60));
                });
    }

    @Test
    void losLimitesDeLaVigenciaSonDe10SegundosA1Hora() {
        assertThat(new DocumentosProperties("documentos", Duration.ofSeconds(10)).vigenciaUrlFirmada())
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(new DocumentosProperties("documentos", Duration.ofHours(1)).vigenciaUrlFirmada())
                .isEqualTo(Duration.ofHours(1));
        assertThatThrownBy(() -> new DocumentosProperties("documentos", Duration.ofSeconds(9)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentosProperties("documentos", Duration.ofHours(1).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentosProperties("documentos", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unBucketVacioNoSePermite() {
        assertThatThrownBy(() -> new DocumentosProperties(" ", Duration.ofSeconds(300)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentosProperties(null, Duration.ofSeconds(300)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaVigenciaFueraDeRangoImpideArrancar() {
        contexto.withPropertyValues("futdata.documentos.vigencia-url-firmada=2h")
                .run(c -> assertThat(c).hasFailed());
    }
}
