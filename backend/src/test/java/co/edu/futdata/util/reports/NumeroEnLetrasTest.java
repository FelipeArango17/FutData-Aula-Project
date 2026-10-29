package co.edu.futdata.util.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NumeroEnLetrasTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            0             | cero
            1             | uno
            15            | quince
            16            | dieciséis
            21            | veintiuno
            22            | veintidós
            30            | treinta
            31            | treinta y uno
            99            | noventa y nueve
            100           | cien
            101           | ciento uno
            115           | ciento quince
            200           | doscientos
            555           | quinientos cincuenta y cinco
            1000          | mil
            1001          | mil uno
            2000          | dos mil
            21000         | veintiún mil
            90000         | noventa mil
            100000        | cien mil
            101000        | ciento un mil
            150000        | ciento cincuenta mil
            999999        | novecientos noventa y nueve mil novecientos noventa y nueve
            1000000       | un millón
            1001000       | un millón mil
            1250500       | un millón doscientos cincuenta mil quinientos
            2000000       | dos millones
            21000000      | veintiún millones
            100000000     | cien millones
            999999999999  | novecientos noventa y nueve mil novecientos noventa y nueve millones novecientos noventa y nueve mil novecientos noventa y nueve
            """)
    void convierteEnLetras(long numero, String esperado) {
        assertThat(NumeroEnLetras.convertir(numero)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            1        | un peso M/CTE
            21       | veintiún pesos M/CTE
            100      | cien pesos M/CTE
            101      | ciento un pesos M/CTE
            1000     | mil pesos M/CTE
            90000    | noventa mil pesos M/CTE
            150000   | ciento cincuenta mil pesos M/CTE
            1000000  | un millón de pesos M/CTE
            3000000  | tres millones de pesos M/CTE
            1250500  | un millón doscientos cincuenta mil quinientos pesos M/CTE
            """)
    void escribeElMontoEnPesos(long monto, String esperado) {
        assertThat(NumeroEnLetras.enPesos(monto)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, NumeroEnLetras.MAXIMO + 1})
    void rechazaNumerosFueraDeRango(long numero) {
        assertThatThrownBy(() -> NumeroEnLetras.convertir(numero)).isInstanceOf(IllegalArgumentException.class);
    }
}
