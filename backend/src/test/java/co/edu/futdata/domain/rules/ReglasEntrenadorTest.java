package co.edu.futdata.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.domain.entities.Entrenador;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Limpieza y validación de los datos personales del entrenador, sin Spring ni base de datos. */
class ReglasEntrenadorTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);

    static Entrenador valido() {
        Entrenador e = new Entrenador();
        e.setNombre("Luis");
        e.setApellido("Pérez");
        e.setTipoDocumento("CC");
        e.setDocumento("1098765432");
        e.setFechaNacimiento(LocalDate.of(1990, 5, 14));
        e.setTelefono("3001112233");
        e.setCorreo("luis.perez@pradofc.co");
        e.setEspecialidad("Porteros");
        return e;
    }

    private static Map<String, String> errores(Entrenador e) {
        return ReglasEntrenador.validar(ReglasEntrenador.limpiar(e), HOY);
    }

    @Test
    void unEntrenadorCompletoNoTieneErrores() {
        assertThat(errores(valido())).isEmpty();
    }

    @Test
    void limpiarQuitaEspaciosSobrantesYNormalizaTipoYCorreo() {
        Entrenador e = valido();
        e.setNombre("  Luis   Fernando ");
        e.setApellido(" Pérez ");
        e.setTipoDocumento(" cc ");
        e.setDocumento(" 1098765432 ");
        e.setTelefono(" 3001112233 ");
        e.setCorreo("  Luis.Perez@PradoFC.co ");
        e.setEspecialidad(" Porteros ");
        e.setDireccion("   ");
        e.setHorarioTrabajo(" Lunes  a viernes ");

        Entrenador limpio = ReglasEntrenador.limpiar(e);

        assertThat(limpio.getNombre()).isEqualTo("Luis Fernando");
        assertThat(limpio.getApellido()).isEqualTo("Pérez");
        assertThat(limpio.getTipoDocumento()).isEqualTo("CC");
        assertThat(limpio.getDocumento()).isEqualTo("1098765432");
        assertThat(limpio.getTelefono()).isEqualTo("3001112233");
        assertThat(limpio.getCorreo()).isEqualTo("luis.perez@pradofc.co");
        assertThat(limpio.getEspecialidad()).isEqualTo("Porteros");
        assertThat(limpio.getDireccion()).isNull();
        assertThat(limpio.getHorarioTrabajo()).isEqualTo("Lunes a viernes");
        assertThat(ReglasEntrenador.validar(limpio, HOY)).isEmpty();
    }

    @Test
    void losObligatoriosVaciosOConSoloEspaciosDanError() {
        Entrenador e = new Entrenador();
        e.setNombre("   ");
        e.setApellido("");

        assertThat(errores(e)).containsOnlyKeys("nombre", "apellido", "tipoDocumento", "documento", "telefono",
                "correo", "especialidad", "fechaNacimiento")
                .containsEntry("nombre", "es obligatorio")
                .containsEntry("especialidad", "es obligatoria")
                .containsEntry("fechaNacimiento", "es obligatoria");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CC", "CE", "PASAPORTE", "pasaporte", " cc "})
    void aceptaCedulasYPasaporte(String tipo) {
        Entrenador e = valido();
        e.setTipoDocumento(tipo);
        assertThat(errores(e)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TI", "ti", " TI "})
    void rechazaLaTarjetaDeIdentidadPorqueEsDeMenores(String tipo) {
        Entrenador e = valido();
        e.setTipoDocumento(tipo);
        assertThat(errores(e)).containsExactly(
                Map.entry("tipoDocumento", "TI no se acepta: la tarjeta de identidad es de menores de edad"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Cédula de ciudadanía", "NIT", "RC"})
    void rechazaOtrosTiposDeDocumento(String tipo) {
        Entrenador e = valido();
        e.setTipoDocumento(tipo);
        assertThat(errores(e)).containsExactly(Map.entry("tipoDocumento", "debe ser CC, CE o PASAPORTE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CC", "CE"})
    void cedulasDe6A12DigitosSinLetrasPuntosNiEspacios(String tipo) {
        Entrenador e = valido();
        e.setTipoDocumento(tipo);
        for (String bueno : List.of("123456", "123456789012", "0012345")) {
            e.setDocumento(bueno);
            assertThat(errores(e)).as(bueno).isEmpty();
        }
        e.setDocumento("12345");
        assertThat(errores(e)).containsEntry("documento", "debe tener entre 6 y 12 dígitos");
        e.setDocumento("1234567890123");
        assertThat(errores(e)).containsEntry("documento", "debe tener entre 6 y 12 dígitos");
        e.setDocumento("1.017.234");
        assertThat(errores(e)).containsEntry("documento", "solo puede tener números, sin puntos ni espacios");
        e.setDocumento("AB123456");
        assertThat(errores(e)).containsEntry("documento", "solo puede tener números, sin puntos ni espacios");
    }

    @Test
    void pasaporteDe6A12LetrasYNumerosEnMayusculas() {
        Entrenador e = valido();
        e.setTipoDocumento("PASAPORTE");
        for (String bueno : List.of("AB123456", "123456", "ABCDEF", "X1Y2Z3W4V5U6")) {
            e.setDocumento(bueno);
            assertThat(errores(e)).as(bueno).isEmpty();
        }

        e.setDocumento(" ab123456 ");
        assertThat(ReglasEntrenador.limpiar(e).getDocumento()).isEqualTo("AB123456");
        assertThat(errores(e)).isEmpty();

        e.setDocumento("AB123");
        assertThat(errores(e)).containsEntry("documento", "debe tener entre 6 y 12 caracteres");
        e.setDocumento("AB12345678901");
        assertThat(errores(e)).containsEntry("documento", "debe tener entre 6 y 12 caracteres");
        for (String malo : List.of("AB 123456", "AB-123456", "AB.123456", "ÑB123456", "AÉ123456")) {
            e.setDocumento(malo);
            assertThat(errores(e)).as(malo)
                    .containsEntry("documento", "solo puede tener letras y números, sin espacios ni otros signos");
        }
    }

    @Test
    void enCedulasLasLetrasNoSePasanAMayusculas() {
        Entrenador e = valido();
        e.setDocumento("ab123456");
        assertThat(ReglasEntrenador.limpiar(e).getDocumento()).isEqualTo("ab123456");
    }

    @ParameterizedTest
    @ValueSource(strings = {"300111223", "30011122334", "300 111 2233", "+573001112233", "300-111-2233"})
    void telefonoDeExactamente10Digitos(String telefono) {
        Entrenador e = valido();
        e.setTelefono(telefono);
        assertThat(errores(e)).containsExactly(Map.entry("telefono", "debe tener exactamente 10 dígitos, sin espacios"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"luis", "luis@", "luis@pradofc", "luis perez@pradofc.co", "@pradofc.co", "a@@b.co"})
    void correoConFormatoInvalido(String correo) {
        Entrenador e = valido();
        e.setCorreo(correo);
        assertThat(errores(e)).containsExactly(Map.entry("correo", "no es válido"));
    }

    @Test
    void fechaDeNacimientoNoFuturaYMayorDe18() {
        Entrenador e = valido();
        e.setFechaNacimiento(HOY.minusYears(18));
        assertThat(errores(e)).as("cumple 18 hoy").isEmpty();

        e.setFechaNacimiento(HOY.minusYears(18).plusDays(1));
        assertThat(errores(e)).containsExactly(Map.entry("fechaNacimiento", "el entrenador debe tener al menos 18 años"));

        e.setFechaNacimiento(HOY);
        assertThat(errores(e)).containsExactly(Map.entry("fechaNacimiento", "el entrenador debe tener al menos 18 años"));

        e.setFechaNacimiento(HOY.plusDays(1));
        assertThat(errores(e)).containsExactly(Map.entry("fechaNacimiento", "no puede ser una fecha futura"));
    }

    @Test
    void textosDentroDeLosLargosDeLaTabla() {
        Entrenador e = valido();
        e.setNombre("a".repeat(100));
        e.setApellido("ñ".repeat(100));
        e.setEspecialidad("b".repeat(100));
        e.setDireccion("c".repeat(250));
        e.setHorarioTrabajo("d".repeat(250));
        e.setCorreo("x".repeat(309) + "@pradofc.co");
        assertThat(errores(e)).isEmpty();

        e.setNombre("a".repeat(101));
        e.setApellido("ñ".repeat(101));
        e.setEspecialidad("b".repeat(101));
        e.setDireccion("c".repeat(251));
        e.setHorarioTrabajo("d".repeat(251));
        e.setCorreo("x".repeat(310) + "@pradofc.co");
        assertThat(errores(e))
                .containsEntry("nombre", "debe tener como máximo 100 caracteres")
                .containsEntry("apellido", "debe tener como máximo 100 caracteres")
                .containsEntry("especialidad", "debe tener como máximo 100 caracteres")
                .containsEntry("direccion", "debe tener como máximo 250 caracteres")
                .containsEntry("horarioTrabajo", "debe tener como máximo 250 caracteres")
                .containsEntry("correo", "debe tener como máximo 320 caracteres");
    }

    @Test
    void elLargoSeCuentaSinLosEspaciosSobrantes() {
        Entrenador e = valido();
        e.setNombre("  " + "a".repeat(100) + "  ");
        assertThat(errores(e)).isEmpty();
    }

    @Test
    void losMensajesNoRepitenElValor() {
        Entrenador e = valido();
        e.setDocumento("ABC-secreto");
        e.setCorreo("secreto");
        assertThat(errores(e).values()).noneMatch(mensaje -> mensaje.contains("secreto"));
    }

    @Test
    void categoriasEnElOrdenOficial() {
        assertThat(ReglasEntrenador.CATEGORIAS).containsExactly("Párvulos", "Cachorros", "Semillero", "Sub-11", "Sub-12",
                "Sub-13", "Sub-14", "Sub-15", "Sub-16");
        assertThat(ReglasEntrenador.enOrdenOficial(List.of("Sub-16", "Cachorros", "Sub-12")))
                .containsExactly("Cachorros", "Sub-12", "Sub-16");
        assertThat(ReglasEntrenador.enOrdenOficial(List.of("Sub-99", "Semillero")))
                .containsExactly("Semillero", "Sub-99");
        assertThat(ReglasEntrenador.esCategoria("Sub-12")).isTrue();
        assertThat(ReglasEntrenador.esCategoria("sub-12")).isFalse();
        assertThat(ReglasEntrenador.esCategoria("Sub-18")).isFalse();
    }

    @Test
    void semilleroEsCategoriaYSub9YSub10YaNo() {
        assertThat(ReglasEntrenador.esCategoria("Semillero")).isTrue();
        assertThat(ReglasEntrenador.esCategoria("Sub-9")).isFalse();
        assertThat(ReglasEntrenador.esCategoria("Sub-10")).isFalse();
        assertThat(ReglasEntrenador.enOrdenOficial(List.of("Sub-11", "Sub-10", "Semillero", "Sub-9")))
                .startsWith("Semillero", "Sub-11");
    }
}
