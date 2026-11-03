package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.futdata.config.SupabaseProperties;
import co.edu.futdata.domain.rules.ReglasContrasena;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Prueba manual del enlace para crear la contraseña contra el proyecto de <b>PRUEBA</b> de Supabase (nunca el de
 * producción). <b>Desactivada por defecto</b>, con el mismo interruptor que {@link SupabaseFotosContraSupabaseTest}:
 * no corre en {@code ./mvnw verify} ni en el CI. Si el interruptor está encendido pero falta {@code SUPABASE_URL} o
 * {@code SUPABASE_SERVICE_ROLE_KEY}, se <b>salta</b>.
 *
 * <p>Crea una cuenta {@code futdata-prueba-{uuid}@example.com} (Supabase no envía correo: el enlace se genera con la
 * API admin) y comprueba: (1) un correo repetido da {@link CorreoYaRegistradoException}; (2) un enlace nuevo invalida
 * el anterior; (3) verificar devuelve el usuario, se fija la contraseña y se cierra la sesión; (4) el enlace ya
 * usado no sirve otra vez; (5) la contraseña quedó (se inicia sesión con ella y se cierra); (6) con la cuenta
 * bloqueada ({@code cambiarAcceso(id, false)}, {@code PUT} con {@code ban_duration}) el inicio de sesión falla, y
 * (7) al quitar el bloqueo vuelve a funcionar. Al final borra siempre la cuenta, aunque la prueba falle. La contraseña se genera al azar en cada corrida y no se imprime.
 *
 * <p>El vencimiento (1 hora) no se puede comprobar esperando; {@link #elEnlaceVenceEnUnaHoraYElLimiteDeVerificaciones}
 * lo lee de la configuración de Auth con la Management API si además está {@code SUPABASE_ACCESS_TOKEN} (token
 * personal de supabase.com/dashboard/account/tokens, <b>secreto</b>), y muestra el límite de verificaciones.
 *
 * <pre>
 * $env:FUTDATA_PRUEBA_SUPABASE_REAL='true'; $env:SUPABASE_URL='https://tu-ref-de-prueba.supabase.co'
 * $env:SUPABASE_SERVICE_ROLE_KEY='...'; $env:SUPABASE_ACCESS_TOKEN='...'   # el último es opcional
 * .\mvnw.cmd test "-Dtest=SupabaseAuthEnlaceContraSupabaseTest"
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "FUTDATA_PRUEBA_SUPABASE_REAL", matches = "true")
class SupabaseAuthEnlaceContraSupabaseTest {

    /** {@code mailer_otp_exp} esperado: el enlace vence en 1 hora (CuentasDeAcceso.VIGENCIA_ENLACE_HORAS). */
    private static final long UNA_HORA = Duration.ofHours(1).toSeconds();

    private String url;
    private String llave;
    private SupabaseAuthAdmin auth;
    private UUID creado;

    @BeforeEach
    void preparar() {
        url = System.getenv("SUPABASE_URL");
        llave = System.getenv("SUPABASE_SERVICE_ROLE_KEY");
        assumeTrue(url != null && !url.isBlank(), "Falta SUPABASE_URL: se salta la prueba del enlace contra Supabase");
        assumeTrue(llave != null && !llave.isBlank(), "Falta SUPABASE_SERVICE_ROLE_KEY: se salta la prueba del enlace contra Supabase");
        url = url.strip().replaceAll("/+$", "");
        auth = new SupabaseAuthAdmin(RestClient.builder(),
                new SupabaseProperties(url, llave, "comprobantes", Duration.ofMinutes(5), Duration.ofSeconds(15)));
    }

    @Test
    void enlaceNuevoInvalidaElAnteriorYElUsadoNoSirveOtraVez() {
        String correo = "futdata-prueba-" + UUID.randomUUID() + "@example.com";
        String contrasena = "Prueba" + UUID.randomUUID().toString().replace("-", "").substring(0, 20) + "7";
        assertThat(ReglasContrasena.cumple(contrasena)).isTrue();

        // Cuenta sin contraseña, con el correo confirmado
        creado = auth.crearUsuario(correo);
        assertThatThrownBy(() -> auth.crearUsuario(correo)).as("(1) correo repetido")
                .isInstanceOf(CorreoYaRegistradoException.class);

        // (2) El segundo enlace invalida el primero
        String primero = auth.generarTokenRecuperacion(correo);
        String segundo = auth.generarTokenRecuperacion(correo);
        assertThat(segundo).isNotEqualTo(primero);
        assertThatThrownBy(() -> auth.verificarTokenRecuperacion(primero)).as("(2) el enlace anterior ya no sirve")
                .isInstanceOf(TokenRecuperacionInvalidoException.class);

        // (3) Verificar el vigente, fijar la contraseña y cerrar la sesión que abrió verify
        SesionRecuperacion sesion = auth.verificarTokenRecuperacion(segundo);
        assertThat(sesion.idUsuario()).isEqualTo(creado);
        auth.fijarContrasena(sesion.idUsuario(), contrasena);
        auth.cerrarSesion(sesion.tokenAcceso());

        // (4) El enlace ya usado no sirve otra vez
        assertThatThrownBy(() -> auth.verificarTokenRecuperacion(segundo)).as("(4) enlace ya usado")
                .isInstanceOf(TokenRecuperacionInvalidoException.class);

        // (5) La contraseña quedó: se inicia sesión con ella y se cierra esa sesión
        Ingreso ingreso = iniciarSesion(correo, contrasena);
        assertThat(ingreso.codigo()).as("(5) inicio de sesión con la contraseña creada").isEqualTo(200);
        assertThat(ingreso.cuerpo().path("user").path("id").asText()).isEqualTo(creado.toString());
        auth.cerrarSesion(ingreso.cuerpo().path("access_token").asText());

        // (6) Bloquear la cuenta (PUT con ban_duration): ya no puede iniciar sesión
        auth.cambiarAcceso(creado, false);
        assertThat(iniciarSesion(correo, contrasena).codigo()).as("(6) bloqueada: el inicio de sesión falla")
                .isBetween(400, 499);

        // (7) Quitar el bloqueo: vuelve a entrar con la misma contraseña
        auth.cambiarAcceso(creado, true);
        Ingreso otraVez = iniciarSesion(correo, contrasena);
        assertThat(otraVez.codigo()).as("(7) sin bloqueo: vuelve a entrar").isEqualTo(200);
        auth.cerrarSesion(otraVez.cuerpo().path("access_token").asText());
    }

    /** {@code POST /token?grant_type=password} sin lanzar por los 4xx: devuelve el código y el cuerpo. */
    private Ingreso iniciarSesion(String correo, String contrasena) {
        return RestClient.create().post()
                .uri(url + "/auth/v1/token?grant_type=password")
                .header("apikey", llave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", correo, "password", contrasena))
                .exchange((peticion, respuesta) -> new Ingreso(respuesta.getStatusCode().value(),
                        respuesta.getStatusCode().is2xxSuccessful() ? respuesta.bodyTo(JsonNode.class) : null));
    }

    private record Ingreso(int codigo, JsonNode cuerpo) {
        @Override
        public String toString() {
            return "Ingreso[codigo=" + codigo + "]"; // sin el cuerpo: trae tokens
        }
    }

    /**
     * Lee {@code mailer_otp_exp} (vencimiento de los enlaces de correo, incluido el de recuperación) y
     * {@code rate_limit_verify} con la Management API. Se salta sin {@code SUPABASE_ACCESS_TOKEN}.
     */
    @Test
    void elEnlaceVenceEnUnaHoraYElLimiteDeVerificaciones() {
        String tokenPersonal = System.getenv("SUPABASE_ACCESS_TOKEN");
        assumeTrue(tokenPersonal != null && !tokenPersonal.isBlank(),
                "Falta SUPABASE_ACCESS_TOKEN: no se puede leer la configuración de Auth");
        String ref = URI.create(url).getHost().split("\\.")[0];

        JsonNode config = RestClient.create().get()
                .uri("https://api.supabase.com/v1/projects/" + ref + "/config/auth")
                .header("Authorization", "Bearer " + tokenPersonal)
                .retrieve()
                .body(JsonNode.class);

        assertThat(config).isNotNull();
        // Solo números de configuración, nada secreto
        System.out.println("Auth del proyecto de prueba: mailer_otp_exp=" + config.path("mailer_otp_exp").asText()
                + " s, rate_limit_verify=" + config.path("rate_limit_verify").asText() + " por 5 min por IP");
        assertThat(config.path("mailer_otp_exp").asLong()).as("vencimiento del enlace en segundos")
                .isEqualTo(UNA_HORA)
                .isEqualTo(Duration.ofHours(co.edu.futdata.services.impl.CuentasDeAcceso.VIGENCIA_ENLACE_HORAS).toSeconds());
    }

    /** Borra siempre la cuenta creada, sin depender de {@code auth.eliminar}. */
    @AfterEach
    void borrarLaCuenta() {
        if (creado == null) return;
        RestClient.create().delete()
                .uri(url + "/auth/v1/admin/users/" + creado)
                .header("apikey", llave)
                .header("Authorization", "Bearer " + llave)
                .retrieve()
                .toBodilessEntity();
    }
}
