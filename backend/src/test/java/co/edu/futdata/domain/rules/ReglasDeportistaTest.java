package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.api.dto.DeportistaMapper;
import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.domain.entities.Deportista;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReglasDeportistaTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** 29 de octubre de 2026, 10:00 en Bogotá. */
    private static final Clock RELOJ_2026 = Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), BOGOTA);
    /** 15 de marzo de 2027, 10:00 en Bogotá. */
    private static final Clock RELOJ_2027 = Clock.fixed(Instant.parse("2027-03-15T15:00:00Z"), BOGOTA);
    /** 31 de diciembre de 2026, 23:59:59 en Bogotá (en UTC ya es 1 de enero de 2027). */
    private static final Clock ULTIMO_SEGUNDO_2026 = Clock.fixed(Instant.parse("2027-01-01T04:59:59Z"), BOGOTA);
    /** 1 de enero de 2027, 00:00 en Bogotá. */
    private static final Clock PRIMER_SEGUNDO_2027 = Clock.fixed(Instant.parse("2027-01-01T05:00:00Z"), BOGOTA);

    private static DeportistaRequestDTO datos() {
        return new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678",
                LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30");
    }

    private static Deportista nacidoEl(LocalDate fecha, String grupo) {
        Deportista d = DeportistaMapper.toEntity(new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678",
                fecha, "3001234567", "Calle 10 # 20-30"));
        d.setGrupo(grupo);
        return ReglasDeportista.limpiar(d);
    }

    private static int anio(Clock reloj) {
        return Year.now(reloj).getValue();
    }

    // --- Datos personales -----------------------------------------------------------------------------------

    @Test
    void aceptaDatosCompletos() {
        var deportista = ReglasDeportista.limpiar(DeportistaMapper.toEntity(datos()));

        assertThat(ReglasDeportista.validar(deportista, LocalDate.now(RELOJ_2026))).isEmpty();
    }

    @Test
    void devuelveTodosLosCamposObligatoriosAusentes() {
        var deportista = ReglasDeportista.limpiar(DeportistaMapper.toEntity(new DeportistaRequestDTO(
                "", "", "", "", null, "", "")));

        assertThat(ReglasDeportista.validar(deportista, LocalDate.now(RELOJ_2026)))
                .containsKeys("nombre", "apellido", "tipoDocumento", "documento",
                        "fechaNacimiento", "telefono", "direccion");
    }

    @Test
    void aceptaPasaporteAlfanumericoYLoNormaliza() {
        var deportista = ReglasDeportista.limpiar(DeportistaMapper.toEntity(
                new DeportistaRequestDTO("Ana", "Pérez", "pasaporte", "ab123456",
                        LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30")));

        assertThat(deportista.getDocumento()).isEqualTo("AB123456");
        assertThat(ReglasDeportista.validar(deportista, LocalDate.now(RELOJ_2026))).isEmpty();
    }

    // --- Tipo de documento: TI, RC, CE y PASAPORTE, sin CC --------------------------------------------------

    @ParameterizedTest
    @CsvSource({"TI,1098765432", "RC,1098765432", "CE,123456", "PASAPORTE,AB123456", "rc,1098765432"})
    void aceptaLosCuatroTiposDeDocumento(String tipo, String documento) {
        var d = ReglasDeportista.limpiar(DeportistaMapper.toEntity(new DeportistaRequestDTO("Ana", "Pérez", tipo,
                documento, LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30")));

        assertThat(ReglasDeportista.validar(d, LocalDate.now(RELOJ_2026))).doesNotContainKey("tipoDocumento");
    }

    @Test
    void laCedulaYaNoEsUnTipoDeDocumentoDelDeportista() {
        var d = ReglasDeportista.limpiar(DeportistaMapper.toEntity(new DeportistaRequestDTO("Ana", "Pérez", "CC",
                "1098765432", LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30")));

        assertThat(ReglasDeportista.validar(d, LocalDate.now(RELOJ_2026)))
                .containsEntry("tipoDocumento", "debe ser TI, RC, CE o PASAPORTE");
    }

    // --- Categoría por año de nacimiento --------------------------------------------------------------------

    @ParameterizedTest(name = "nacido en {0} → {1} en 2026")
    @CsvSource({
            "2022, Párvulos", "2020, Párvulos", "2019, Cachorros", "2018, Cachorros", "2017, Semillero",
            "2016, Semillero", "2015, Sub-11", "2014, Sub-12", "2013, Sub-13", "2012, Sub-14", "2011, Sub-15",
            "2010, Sub-16"})
    void lasNueveCategoriasEn2026(int anioNacimiento, String categoria) {
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(LocalDate.of(anioNacimiento, 6, 15), anio(RELOJ_2026)))
                .contains(categoria);
    }

    @Test
    void unNinoDe2016EsSemilleroEn2026YSub11En2027() {
        LocalDate nacimiento = LocalDate.of(2016, 8, 20);

        assertThat(ReglasDeportista.edadPorAnio(nacimiento, anio(RELOJ_2026))).isEqualTo(10);
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(nacimiento, anio(RELOJ_2026))).contains("Semillero");
        assertThat(ReglasDeportista.edadPorAnio(nacimiento, anio(RELOJ_2027))).isEqualTo(11);
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(nacimiento, anio(RELOJ_2027))).contains("Sub-11");
    }

    /** El cambio es el 1 de enero en hora de Colombia, no en UTC ni por el día del cumpleaños. */
    @Test
    void elCambioDeCategoriaEsElPrimeroDeEneroEnHoraDeColombia() {
        for (LocalDate nacimiento : new LocalDate[] {LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)}) {
            assertThat(ReglasDeportista.categoriaPorAnioNacimiento(nacimiento, anio(ULTIMO_SEGUNDO_2026)))
                    .as("%s el 31/12/2026 23:59:59", nacimiento).contains("Semillero");
            assertThat(ReglasDeportista.categoriaPorAnioNacimiento(nacimiento, anio(PRIMER_SEGUNDO_2027)))
                    .as("%s el 01/01/2027 00:00", nacimiento).contains("Sub-11");
        }
    }

    @Test
    void elDiaDelCumpleanosNoCambiaLaCategoria() {
        // Nacido el 31/12/2016: en octubre de 2026 aún no cumple 10, pero por año de nacimiento tiene 10
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(LocalDate.of(2016, 12, 31), anio(RELOJ_2026)))
                .contains("Semillero");
    }

    @Test
    void bordesDelRango4A16() {
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(LocalDate.of(2022, 12, 31), 2026)).contains("Párvulos");
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(LocalDate.of(2023, 1, 1), 2026)).isEmpty();
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(LocalDate.of(2010, 1, 1), 2026)).contains("Sub-16");
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(LocalDate.of(2009, 12, 31), 2026)).isEmpty();
    }

    @Test
    void unNacidoEn2009QuedaFueraEn2026AlLeerYAlEscribir() {
        LocalDate nacimiento = LocalDate.of(2009, 5, 5);

        // Al leer: 17 años por año de nacimiento, sin categoría y sin error
        assertThat(ReglasDeportista.edadPorAnio(nacimiento, anio(RELOJ_2026))).isEqualTo(17);
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(nacimiento, anio(RELOJ_2026))).isEmpty();
        // Al escribir: error en fechaNacimiento (el servicio lo convierte en 400)
        assertThat(ReglasDeportista.validar(nacidoEl(nacimiento, null), LocalDate.now(RELOJ_2026)))
                .containsEntry("fechaNacimiento",
                        "por año de nacimiento tiene 17 años en 2026; el club solo inscribe de 4 a 16 años");
    }

    @Test
    void unMenorDe4AniosNoSeInscribe() {
        assertThat(ReglasDeportista.validar(nacidoEl(LocalDate.of(2023, 2, 1), null), LocalDate.now(RELOJ_2026)))
                .containsKey("fechaNacimiento");
    }

    @Test
    void sinFechaNoHayCategoria() {
        assertThat(ReglasDeportista.categoriaPorAnioNacimiento(null, 2026)).isEmpty();
    }

    // --- Grupo (mañana/tarde) -------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} en {1}")
    @CsvSource({"mañana, 2019", "tarde, 2018", "mañana, 2017", "tarde, 2016", "Tarde, 2016", "' mañana ', 2017"})
    void elGrupoSeAceptaEnCachorrosYSemillero(String grupo, int anioNacimiento) {
        var d = nacidoEl(LocalDate.of(anioNacimiento, 3, 3), grupo);

        assertThat(ReglasDeportista.validar(d, LocalDate.now(RELOJ_2026))).isEmpty();
        assertThat(d.getGrupo()).isIn("mañana", "tarde");
    }

    @ParameterizedTest(name = "nacido en {0} ({1})")
    @CsvSource({"2021, Párvulos", "2015, Sub-11", "2010, Sub-16"})
    void elGrupoSeRechazaEnOtrasCategorias(int anioNacimiento, String categoria) {
        var d = nacidoEl(LocalDate.of(anioNacimiento, 3, 3), "mañana");

        assertThat(ReglasDeportista.validar(d, LocalDate.now(RELOJ_2026))).containsEntry("grupo",
                "solo se asigna en Cachorros y Semillero; por año de nacimiento está en " + categoria);
    }

    @Test
    void unGrupoQueNoEsMananaNiTardeSeRechaza() {
        assertThat(ReglasDeportista.validar(nacidoEl(LocalDate.of(2017, 3, 3), "noche"), LocalDate.now(RELOJ_2026)))
                .containsEntry("grupo", "debe ser mañana o tarde");
    }

    @Test
    void unNinoDe2016ConGrupoPasaASerInvalidoEn2027() {
        var d = nacidoEl(LocalDate.of(2016, 8, 20), "tarde");

        assertThat(ReglasDeportista.validar(d, LocalDate.now(RELOJ_2026))).isEmpty();
        assertThat(ReglasDeportista.validar(d, LocalDate.now(RELOJ_2027))).containsKey("grupo");
    }

    @Test
    void elGrupoVacioEsSinGrupo() {
        var d = nacidoEl(LocalDate.of(2012, 3, 3), "  ");

        assertThat(d.getGrupo()).isNull();
        assertThat(ReglasDeportista.validar(d, LocalDate.now(RELOJ_2026))).isEmpty();
    }
}
