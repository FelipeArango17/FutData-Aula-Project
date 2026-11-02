package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FotosPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(FotosProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void porDefectoElBucketEsFotosYLaUrlDura60Minutos() {
        contexto.run(c -> {
            FotosProperties fotos = c.getBean(FotosProperties.class);
            assertThat(fotos.bucket()).isEqualTo("fotos");
            assertThat(fotos.vigenciaUrlFirmada()).isEqualTo(Duration.ofMinutes(60));
        });
    }

    @Test
    void seConfiguranPorPropiedades() {
        contexto.withPropertyValues("futdata.fotos.bucket=fotos-staging", "futdata.fotos.vigencia-url-firmada=15m")
                .run(c -> {
                    FotosProperties fotos = c.getBean(FotosProperties.class);
                    assertThat(fotos.bucket()).isEqualTo("fotos-staging");
                    assertThat(fotos.vigenciaUrlFirmada()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void unaVigenciaFueraDe10sA60mNoArranca() {
        contexto.withPropertyValues("futdata.fotos.vigencia-url-firmada=2h")
                .run(c -> assertThat(c).hasFailed());
        assertThatThrownBy(() -> new FotosProperties("fotos", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FotosProperties(" ", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
