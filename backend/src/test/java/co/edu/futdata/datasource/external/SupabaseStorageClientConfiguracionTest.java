package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.config.SupabaseProperties;
import co.edu.futdata.services.facade.IComprobanteService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Perfil local sin SUPABASE_URL ni SUPABASE_SERVICE_ROLE_KEY: la aplicación arranca y usar Storage lo explica. */
@SpringBootTest
@TestPropertySource(locations = "classpath:pruebas.properties")
class SupabaseStorageClientConfiguracionTest {

    @Autowired
    private IAlmacenamientoArchivos almacenamiento;

    @Autowired
    private IComprobanteService comprobantes;

    @Autowired
    private SupabaseProperties supabase;

    @Test
    void losValoresPorDefectoSonElBucketComprobantesYCincoMinutos() {
        assertThat(supabase.bucketComprobantes()).isEqualTo("comprobantes");
        assertThat(supabase.vigenciaUrlFirmada()).isEqualTo(Duration.ofMinutes(5));
        assertThat(supabase.timeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void elContextoArrancaYUsarStorageLanzaNoConfigurado() {
        assertThat(almacenamiento).isInstanceOf(SupabaseStorageClient.class);

        assertThatThrownBy(() -> comprobantes.enlaceDescarga("2026/FD-2026-000123.pdf"))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class)
                .hasMessageContaining("falta configurar SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY");
    }
}
