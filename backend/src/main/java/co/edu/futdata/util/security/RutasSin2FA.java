package co.edu.futdata.util.security;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Las <b>únicas</b> rutas que no exigen una sesión con el segundo paso completado (PPI 4.2: "todos los servicios de
 * la API, excepto los de inicio de sesión, verificación del código y el webhook de Wompi, exigen un token válido de
 * una sesión que ya completó el segundo paso"), más crear la contraseña con el enlace del correo (regla 9), que es
 * parte del inicio de sesión. Todo lo que no esté aquí exige JWT válido, un rol en la base de
 * datos y la sesión verificada ({@link SesionVerificadaFilter}).
 *
 * <p>Las usan {@link SeguridadConfig} (las {@link Acceso#PUBLICA} van con {@code permitAll}) y
 * {@link SesionVerificadaFilter} (no les exige la sesión verificada). Agregar una ruta aquí la abre: hay que
 * justificarla en {@link Ruta#motivo()}.
 */
public final class RutasSin2FA {

    /** Perfil de Spring en el que se publica la documentación OpenAPI ({@link #DOCUMENTACION}). */
    public static final String PERFIL_DOCUMENTACION = "dev";

    /** Webhook de Wompi ({@code WompiWebhookController}). */
    public static final String WEBHOOK_WOMPI = "/api/pagos/webhook/wompi";

    /** Crear la contraseña con el enlace del correo ({@code ContrasenaController}). */
    public static final String CREAR_CONTRASENA = "/api/auth/crear-contrasena";

    /** Cómo se entra a una ruta sin 2FA. */
    public enum Acceso {
        /** Sin JWT ({@code permitAll}). */
        PUBLICA,
        /** Con el JWT del primer paso y un rol en la base de datos, pero sin haber verificado el código. */
        CON_TOKEN
    }

    /**
     * @param metodo método HTTP; {@code null} para cualquiera
     * @param patron patrón de ruta ({@code PathPattern}) sin el context path
     */
    public record Ruta(HttpMethod metodo, String patron, Acceso acceso, String motivo) {

        public RequestMatcher matcher() {
            PathPatternRequestMatcher.Builder rutas = PathPatternRequestMatcher.withDefaults();
            return metodo == null ? rutas.matcher(patron) : rutas.matcher(metodo, patron);
        }
    }

    /** Rutas sin 2FA en todos los perfiles. */
    public static final List<Ruta> RUTAS = List.of(
            new Ruta(HttpMethod.POST, "/api/auth/2fa/codigo", Acceso.CON_TOKEN,
                    "Segundo paso: pedir el código. El usuario sale del JWT"),
            new Ruta(HttpMethod.POST, "/api/auth/2fa/reenviar", Acceso.CON_TOKEN,
                    "Segundo paso: reenviar el código (uno por minuto)"),
            new Ruta(HttpMethod.POST, "/api/auth/2fa/verificar", Acceso.CON_TOKEN,
                    "Segundo paso: verificar el código; marca la sesión como verificada"),
            new Ruta(HttpMethod.POST, CREAR_CONTRASENA, Acceso.PUBLICA,
                    "Quien crea su contraseña aún no tiene sesión: la credencial es el token del enlace (de un solo uso, "
                            + "lo verifica Supabase) y hay límite de intentos por IP, por token y global"),
            new Ruta(HttpMethod.POST, WEBHOOK_WOMPI, Acceso.PUBLICA,
                    "Wompi no envía JWT: el controlador debe verificar la firma del evento antes de usarlo"),
            new Ruta(HttpMethod.GET, "/api/health", Acceso.PUBLICA,
                    "Monitoreo del despliegue; no expone datos"),
            new Ruta(null, "/error", Acceso.PUBLICA,
                    "Página de error de Spring; responde el JSON de errores sin datos"),
            new Ruta(HttpMethod.OPTIONS, "/**", Acceso.PUBLICA,
                    "Preflight de CORS: el navegador no envía el token"));

    /**
     * Documentación OpenAPI: solo con el perfil {@value #PERFIL_DOCUMENTACION}. En los demás perfiles springdoc está
     * apagado ({@code application.yml}) y estas rutas exigen sesión como cualquier otra.
     */
    public static final List<Ruta> DOCUMENTACION = List.of(
            new Ruta(HttpMethod.GET, "/v3/api-docs/**", Acceso.PUBLICA, "OpenAPI, solo en el perfil dev"),
            new Ruta(HttpMethod.GET, "/v3/api-docs", Acceso.PUBLICA, "OpenAPI, solo en el perfil dev"),
            new Ruta(HttpMethod.GET, "/swagger-ui.html", Acceso.PUBLICA, "Swagger UI, solo en el perfil dev"),
            new Ruta(HttpMethod.GET, "/swagger-ui/**", Acceso.PUBLICA, "Swagger UI, solo en el perfil dev"));

    private RutasSin2FA() {
    }

    /** Las rutas sin 2FA de este arranque: {@link #RUTAS} y, con el perfil dev, {@link #DOCUMENTACION}. */
    public static List<Ruta> vigentes(boolean perfilDocumentacion) {
        return perfilDocumentacion ? Stream.concat(RUTAS.stream(), DOCUMENTACION.stream()).toList() : RUTAS;
    }

    /** Las que van sin JWT. */
    public static RequestMatcher[] publicas(boolean perfilDocumentacion) {
        return vigentes(perfilDocumentacion).stream()
                .filter(ruta -> ruta.acceso() == Acceso.PUBLICA)
                .map(Ruta::matcher)
                .toArray(RequestMatcher[]::new);
    }
}
