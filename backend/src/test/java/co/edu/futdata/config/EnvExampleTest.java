package co.edu.futdata.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * backend/.env.example se lee como .properties (ISO-8859-1): las líneas de ejemplo del club, al descomentarlas,
 * deben dar los mismos datos que application.yml, con las tildes bien.
 */
class EnvExampleTest {

    @Test
    void losDatosDelClubDeEjemploSeLeenConTildes() throws IOException {
        String descomentadas = Files.readAllLines(Path.of(".env.example"), StandardCharsets.UTF_8).stream()
                .filter(linea -> linea.startsWith("# CLUB_"))
                .map(linea -> linea.substring(2))
                .collect(Collectors.joining("\n"));
        assertThat(descomentadas).matches("\\p{ASCII}*");

        Properties propiedades = new Properties();
        propiedades.load(new ByteArrayInputStream(descomentadas.getBytes(StandardCharsets.ISO_8859_1)));

        assertThat(propiedades.getProperty("CLUB_NOMBRE")).isEqualTo("Asociación Deportiva Prado Fútbol Club");
        assertThat(propiedades.getProperty("CLUB_DIRECCION")).isEqualTo("Calle 40 Sur 75b-81, San Antonio de Prado, Medellín, Antioquia");
        assertThat(propiedades.getProperty("CLUB_CORREO")).isEqualTo("pradofutbolclub@gmail.com");
    }
}
