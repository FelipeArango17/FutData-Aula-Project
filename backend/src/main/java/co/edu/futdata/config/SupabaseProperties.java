package co.edu.futdata.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Conexión con Supabase Storage ({@code futdata.supabase.*}). La usa
 * {@link co.edu.futdata.datasource.external.SupabaseStorageClient}.
 *
 * <p>{@code url} y {@code serviceRoleKey} no se validan aquí para que la aplicación arranque en el perfil
 * local sin Supabase: si faltan, el cliente lanza {@code AlmacenamientoNoConfiguradoException} al usarlo. En
 * el perfil {@code prod} las exige {@link VerificadorVariablesProd} antes de arrancar.
 *
 * @param url                URL del proyecto, sin barra final ({@code SUPABASE_URL}), por ejemplo
 *                           {@code https://abcd.supabase.co}
 * @param serviceRoleKey     llave {@code service_role} ({@code SUPABASE_SERVICE_ROLE_KEY}). <b>Secreta</b>: salta
 *                           las políticas de acceso; solo la usa el backend y nunca va al frontend
 * @param bucketComprobantes bucket privado de los comprobantes de pago
 * @param vigenciaUrlFirmada cuánto dura una URL firmada de descarga (corta: 5 min por defecto)
 * @param timeout            tiempo máximo de conexión y de lectura de cada llamada a Storage
 */
@ConfigurationProperties(prefix = "futdata.supabase")
public record SupabaseProperties(
        String url,
        String serviceRoleKey,
        @DefaultValue("comprobantes") String bucketComprobantes,
        @DefaultValue("5m") Duration vigenciaUrlFirmada,
        @DefaultValue("10s") Duration timeout) {

    /** No muestra la llave: estas propiedades pueden terminar en un log. */
    @Override
    public String toString() {
        boolean conLlave = serviceRoleKey != null && !serviceRoleKey.isBlank();
        return "SupabaseProperties[url=" + url + ", serviceRoleKey=" + (conLlave ? "****" : "(vacía)")
                + ", bucketComprobantes=" + bucketComprobantes + ", vigenciaUrlFirmada=" + vigenciaUrlFirmada
                + ", timeout=" + timeout + "]";
    }
}
