package co.edu.futdata.datasource.external;

import java.time.Duration;

/**
 * Guarda archivos en un almacenamiento externo privado (Supabase Storage) y entrega enlaces temporales para
 * descargarlos. Los servicios ({@code services.impl}) dependen de esta interfaz, nunca de
 * {@link SupabaseStorageClient}.
 *
 * <p>Por ahora solo acepta PDF de hasta {@link #TAMANO_MAXIMO} bytes (los comprobantes de pago).
 */
public interface IAlmacenamientoArchivos {

    String PDF = "application/pdf";
    /** 5 MB, el mismo límite que se configura en el bucket. */
    int TAMANO_MAXIMO = 5 * 1024 * 1024;

    /**
     * Sube un archivo nuevo. <b>Nunca sobrescribe</b>: si ya hay uno en esa ruta, lanza
     * {@link ArchivoYaExisteException}.
     *
     * @param bucket      bucket privado, por ejemplo {@code comprobantes}
     * @param ruta        ruta dentro del bucket, sin barra inicial ni {@code ..}, por ejemplo {@code 2026/FD-2026-000123.pdf}
     * @param contenido   bytes del archivo, de 1 a {@link #TAMANO_MAXIMO}
     * @param contentType tipo del archivo; solo {@link #PDF}
     * @return la ruta con la que quedó guardado (la misma {@code ruta}); es lo que se guarda en la base de datos
     * @throws ArchivoInvalidoException             tipo, tamaño, bucket o ruta inválidos (no se llama a Storage)
     * @throws ArchivoYaExisteException             ya hay un archivo en esa ruta
     * @throws AlmacenamientoNoConfiguradoException falta la URL o la llave, la llave no sirve o el bucket no existe
     * @throws AlmacenamientoRechazadoException     Storage rechazó la petición (por ejemplo, demasiado grande)
     * @throws AlmacenamientoNoDisponibleException  {@code 5xx}, tiempo de espera agotado o error de red
     */
    String subir(String bucket, String ruta, byte[] contenido, String contentType);

    /**
     * URL firmada para descargar un archivo durante {@code vigencia}. Quien tenga la URL puede descargar el
     * archivo hasta que venza: solo se entrega a quien tiene permiso y nunca se guarda ni se registra.
     *
     * @param vigencia de 10 s a 1 h
     * @throws ArchivoNoEncontradoException         no hay un archivo en esa ruta
     * @throws ArchivoInvalidoException             bucket o ruta inválidos (no se llama a Storage)
     * @throws AlmacenamientoNoConfiguradoException falta la URL o la llave, o la llave no sirve
     * @throws AlmacenamientoRechazadoException     Storage rechazó la petición
     * @throws AlmacenamientoNoDisponibleException  {@code 5xx}, tiempo de espera agotado o error de red
     * @throws IllegalArgumentException             vigencia fuera de rango
     */
    String urlFirmada(String bucket, String ruta, Duration vigencia);
}
