package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.domain.rules.ReglasFranja.Entrada;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ReglasFranjaTest {

    private static final UUID CATEGORIA = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final String FORMATO =
            "debe tener el formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00";

    private static Entrada valida() {
        return new Entrada(CATEGORIA, 2, "16:30", "18:00", "UVA El Paraíso", null, null);
    }

    private static Entrada con(Integer dia, String inicio, String fin, String sede, String grupo) {
        return new Entrada(CATEGORIA, dia, inicio, fin, sede, null, grupo);
    }

    @Test
    void unaFranjaCompletaSinEntrenadorNiGrupoEsValida() {
        assertThat(ReglasFranja.validar(valida(), "Sub-12")).isEmpty();
    }

    @Test
    void conEntrenadorTambienEsValida() {
        Entrada e = new Entrada(CATEGORIA, 1, "19:00", "20:00", "Horizontes", UUID.randomUUID(), null);
        assertThat(ReglasFranja.validar(e, "Sub-13")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void losDiasDeLunesASabadoSonValidos(int dia) {
        assertThat(ReglasFranja.validar(con(dia, "08:00", "09:00", "Horizontes", null), "Sub-11")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, -1, 100})
    void unDiaFueraDe1A6SeRechaza(int dia) {
        assertThat(ReglasFranja.validar(con(dia, "08:00", "09:00", "Horizontes", null), "Sub-11"))
                .containsExactly(Map.entry("dia", "debe estar entre 1 (lunes) y 6 (sábado)"));
    }

    @Test
    void elDiaEsObligatorio() {
        assertThat(ReglasFranja.validar(con(null, "08:00", "09:00", "Horizontes", null), "Sub-11"))
                .containsExactly(Map.entry("dia", "es obligatorio (1 = lunes ... 6 = sábado)"));
    }

    @Test
    void laCategoriaEsObligatoria() {
        Entrada e = new Entrada(null, 2, "16:30", "18:00", "Horizontes", null, null);
        assertThat(ReglasFranja.validar(e, null)).containsExactly(Map.entry("idCategoria", "es obligatoria"));
    }

    @Test
    void lasHorasSonObligatorias() {
        Map<String, String> errores = ReglasFranja.validar(con(2, null, "  ", "Horizontes", null), "Sub-11");
        assertThat(errores).containsOnlyKeys("horaInicio", "horaFin");
        assertThat(errores.get("horaInicio")).startsWith("es obligatoria (formato HH:mm");
        // "  " llega vacío después de limpiar; sin limpiar, también se rechaza (no es HH:mm)
        assertThat(ReglasFranja.validar(ReglasFranja.limpiar(con(2, "08:00", "  ", "Horizontes", null)), "Sub-11"))
                .containsOnlyKeys("horaFin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"7:30", "07:30:00", "24:00", "19:60", "19h00", "7 pm", "0730", "07.30", "730"})
    void unaHoraQueNoEsHHmmSeRechazaConMensajeClaro(String hora) {
        assertThat(ReglasFranja.validar(con(2, hora, "23:00", "Horizontes", null), "Sub-11"))
                .containsExactly(Map.entry("horaInicio", FORMATO));
        assertThat(ReglasFranja.hora(hora)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "07:30", "19:00", "23:59"})
    void lasHorasHHmmSonValidas(String hora) {
        assertThat(ReglasFranja.hora(hora)).contains(LocalTime.parse(hora));
    }

    @Test
    void laHoraDeFinDebeSerPosteriorALaDeInicio() {
        assertThat(ReglasFranja.validar(con(2, "18:00", "16:30", "Horizontes", null), "Sub-11"))
                .containsExactly(Map.entry("horaFin", "debe ser posterior a horaInicio"));
        assertThat(ReglasFranja.validar(con(2, "18:00", "18:00", "Horizontes", null), "Sub-11"))
                .containsExactly(Map.entry("horaFin", "debe ser posterior a horaInicio"));
        assertThat(ReglasFranja.validar(con(2, "18:00", "18:01", "Horizontes", null), "Sub-11")).isEmpty();
    }

    @Test
    void lasDosSedesDelClubSonValidas() {
        assertThat(ReglasFranja.validar(con(2, "08:00", "09:00", "Horizontes", null), "Sub-11")).isEmpty();
        assertThat(ReglasFranja.validar(con(2, "08:00", "09:00", "UVA El Paraíso", null), "Sub-11")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"horizontes", "UVA El Paraiso", "Estadio", "Otra sede"})
    void otraSedeSeRechaza(String sede) {
        assertThat(ReglasFranja.validar(con(2, "08:00", "09:00", sede, null), "Sub-11"))
                .containsExactly(Map.entry("sede", "debe ser Horizontes o UVA El Paraíso"));
    }

    @Test
    void laSedeEsObligatoria() {
        assertThat(ReglasFranja.validar(con(2, "08:00", "09:00", null, null), "Sub-11"))
                .containsExactly(Map.entry("sede", "es obligatoria (Horizontes o UVA El Paraíso)"));
    }

    @Test
    void elGrupoEsValidoEnSemilleroYEnCachorros() {
        assertThat(ReglasFranja.validar(con(2, "07:30", "09:00", "UVA El Paraíso", "mañana"), "Semillero")).isEmpty();
        assertThat(ReglasFranja.validar(con(4, "16:30", "18:00", "UVA El Paraíso", "tarde"), "Cachorros")).isEmpty();
    }

    @Test
    void elGrupoEnSub12EsUnError() {
        assertThat(ReglasFranja.validar(con(2, "07:30", "09:00", "Horizontes", "mañana"), "Sub-12"))
                .containsExactly(Map.entry("grupo",
                        "solo se asigna en Cachorros y Semillero; la franja es de Sub-12"));
    }

    @Test
    void elGrupoEnParvulosEsUnError() {
        assertThat(ReglasFranja.validar(con(2, "07:30", "09:00", "Horizontes", "tarde"), "Párvulos"))
                .containsOnlyKeys("grupo");
    }

    @Test
    void unGrupoQueNoEsMananaNiTardeSeRechaza() {
        assertThat(ReglasFranja.validar(con(2, "07:30", "09:00", "Horizontes", "noche"), "Semillero"))
                .containsExactly(Map.entry("grupo", "debe ser mañana o tarde"));
    }

    @Test
    void sinCategoriaEncontradaElGrupoNoRepiteElError() {
        assertThat(ReglasFranja.validar(con(2, "07:30", "09:00", "Horizontes", "mañana"), null)).isEmpty();
    }

    @Test
    void limpiarQuitaEspaciosYPasaElGrupoAMinusculas() {
        Entrada limpia = ReglasFranja.limpiar(
                new Entrada(CATEGORIA, 2, " 07:30 ", "09:00 ", " UVA El Paraíso ", null, " Mañana "));
        assertThat(limpia).isEqualTo(new Entrada(CATEGORIA, 2, "07:30", "09:00", "UVA El Paraíso", null, "mañana"));
        assertThat(ReglasFranja.limpiar(con(2, "07:30", "09:00", "Horizontes", "  ")).grupo()).isNull();
    }

    @Test
    void todosLosErroresSalenJuntos() {
        Entrada e = new Entrada(null, 9, "7:30", "06:00", "Estadio", null, "noche");
        assertThat(ReglasFranja.validar(e, null))
                .containsOnlyKeys("idCategoria", "dia", "horaInicio", "sede", "grupo");
        Entrada horas = con(0, "19:00", "18:00", "", "tarde");
        assertThat(ReglasFranja.validar(horas, "Sub-14")).containsOnlyKeys("dia", "horaFin", "sede", "grupo");
    }

    @Test
    void formatearDevuelveHHmm() {
        assertThat(ReglasFranja.formatear(LocalTime.of(7, 30))).isEqualTo("07:30");
        assertThat(ReglasFranja.formatear(LocalTime.of(19, 0))).isEqualTo("19:00");
        assertThat(ReglasFranja.formatear(null)).isNull();
    }

    @Test
    void esSedeDistingueElNombreExacto() {
        assertThat(ReglasFranja.esSede("Horizontes")).isTrue();
        assertThat(ReglasFranja.esSede("UVA El Paraíso")).isTrue();
        assertThat(ReglasFranja.esSede("UVA")).isFalse();
        assertThat(ReglasFranja.esSede(null)).isFalse();
    }
}
