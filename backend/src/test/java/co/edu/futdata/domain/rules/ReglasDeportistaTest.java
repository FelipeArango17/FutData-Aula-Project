package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReglasDeportistaTest {

    private static DeportistaRequestDTO datos() {
        return new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678",
                LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30");
    }

    @Test
    void aceptaDatosCompletos() {
        var deportista = ReglasDeportista.limpiar(DeportistaMapper.toEntity(datos()));

        assertThat(ReglasDeportista.validar(deportista, LocalDate.of(2026, 10, 29))).isEmpty();
    }

    @Test
    void devuelveTodosLosCamposObligatoriosAusentes() {
        var deportista = ReglasDeportista.limpiar(DeportistaMapper.toEntity(new DeportistaRequestDTO(
                "", "", "", "", null, "", "")));

        assertThat(ReglasDeportista.validar(deportista, LocalDate.of(2026, 10, 29)))
                .containsKeys("nombre", "apellido", "tipoDocumento", "documento",
                        "fechaNacimiento", "telefono", "direccion");
    }

    @Test
    void aceptaPasaporteAlfanumericoYLoNormaliza() {
        var deportista = ReglasDeportista.limpiar(DeportistaMapper.toEntity(
                new DeportistaRequestDTO("Ana", "Pérez", "pasaporte", "ab123456",
                        LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30")));

        assertThat(deportista.getDocumento()).isEqualTo("AB123456");
        assertThat(ReglasDeportista.validar(deportista, LocalDate.of(2026, 10, 29))).isEmpty();
    }
}
