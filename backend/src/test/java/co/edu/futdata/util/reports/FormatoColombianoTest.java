package co.edu.futdata.util.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FormatoColombianoTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            1        | $ 1
            900      | $ 900
            90000    | $ 90.000
            1250500  | $ 1.250.500
            """)
    void pesosConPuntoDeMiles(long monto, String esperado) {
        assertThat(FormatoColombiano.pesos(monto)).isEqualTo(esperado);
    }

    @Test
    void fechaEnEspanol() {
        assertThat(FormatoColombiano.fecha(LocalDate.of(2026, 10, 28))).isEqualTo("28 de octubre de 2026");
        assertThat(FormatoColombiano.fecha(LocalDate.of(2026, 1, 5))).isEqualTo("5 de enero de 2026");
    }

    @Test
    void fechaHoraEnLaZonaDeColombia() {
        // 21:35 UTC = 4:35 p. m. en Bogotá (UTC-5)
        assertThat(FormatoColombiano.fechaHora(Instant.parse("2026-10-28T21:35:00Z"))).isEqualTo("28 de octubre de 2026, 4:35 p. m.");
        // 03:10 UTC del 29 = 10:10 p. m. del 28 en Bogotá: cambia el día
        assertThat(FormatoColombiano.fechaHora(Instant.parse("2026-10-29T03:10:00Z"))).isEqualTo("28 de octubre de 2026, 10:10 p. m.");
        assertThat(FormatoColombiano.fechaHora(Instant.parse("2026-10-28T05:05:00Z"))).isEqualTo("28 de octubre de 2026, 12:05 a. m.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            811032309-1    | NIT 811.032.309-1
            811.032.309-1  | NIT 811.032.309-1
            90012345       | NIT 90.012.345
            NIT-raro       | NIT NIT-raro
            """)
    void nitConPuntos(String nit, String esperado) {
        assertThat(FormatoColombiano.nit(nit)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            3137317366    | 313 731 7366
            313 731 7366  | 313 731 7366
            6044445555 | 604 444 5555
            4445555       | 4445555
            """)
    void celularEnGruposDeTresTresCuatro(String telefono, String esperado) {
        assertThat(FormatoColombiano.telefono(telefono)).isEqualTo(esperado);
    }
}
