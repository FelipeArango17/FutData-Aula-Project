package co.edu.futdata.datasource.external;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Guarda archivos en un almacenamiento externo privado (Supabase Storage) y entrega enlaces temporales para
 * descargarlos. Los servicios ({@code services.impl}) dependen de esta interfaz, nunca de
 * {@link SupabaseStorageClient}.
 *
 * <p>Los tipos permitidos se indican <b>en cada llamada</b>: {@link #subir(String, String, byte[], String)} solo
 * acepta PDF (los comprobantes de pago) y {@link #subir(String, String, byte[], String, List)} recibe la lista,
 * por ejemplo {@link #IMAGENES} para las fotos. El tamaño máximo es {@link #TAMANO_MAXIMO} en ambos casos.
 *
 * <p>Los métodos {@code default} los implementa {@link SupabaseStorageClient}; un doble de prueba que solo sube PDF
 * y firma de a una URL no necesita sobrescribirlos.
 */
public interface IAlmacenamientoArchivos {

    String PDF = "application/pdf";
    String JPEG = "image/jpeg";
    String PNG = "image/png";
    /** Tipos de las fotos de los deportistas (bucket {@code fotos}). */
    List<String> IMAGENES = List.of(JPEG, PNG);
    /** 5 MB, el mismo límite que se configura en el bucket. */
    int TAMANO_MAXIMO = 5 * 1024 * 1024;

    /**
     * Sube un PDF nuevo. <b>Nunca sobrescribe</b>: si ya hay uno en esa ruta, lanza
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
     * Igual que {@link #subir(String, String, byte[], String)}, pero acepta los tipos de {@code tiposPermitidos}.
     * No revisa los bytes: quien llama ya comprobó que el contenido es del tipo que declara.
     *
     * @param tiposPermitidos por ejemplo {@link #IMAGENES}
     */
    default String subir(String bucket, String ruta, byte[] contenido, String contentType, List<String> tiposPermitidos) {
        throw new UnsupportedOperationException("Este almacenamiento solo sube PDF");
    }

    /**
     * Borra un archivo.
     *
     * @throws ArchivoNoEncontradoException         no hay un archivo en esa ruta
     * @throws ArchivoInvalidoException             bucket o ruta inválidos (no se llama a Storage)
     * @throws AlmacenamientoNoConfiguradoException falta la URL o la llave, o la llave no sirve
     * @throws AlmacenamientoNoDisponibleException  {@code 5xx}, tiempo de espera agotado o error de red
     */
    default void eliminar(String bucket, String ruta) {
        throw new UnsupportedOperationException("Este almacenamiento no borra archivos");
    }

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

    /**
     * URL firmadas de varios archivos del mismo bucket en <b>una sola</b> llamada (para una página de resultados).
     * Las URL sirven para mostrar el archivo (sin forzar la descarga).
     *
     * @return ruta → URL firmada; una ruta sin archivo no aparece en el mapa
     * @throws ArchivoInvalidoException            bucket o alguna ruta inválidos (no se llama a Storage)
     * @throws AlmacenamientoException             Storage falló para toda la petición
     * @throws IllegalArgumentException            vigencia fuera de rango
     */
    default Map<String, String> urlsFirmadas(String bucket, List<String> rutas, Duration vigencia) {
        throw new UnsupportedOperationException("Este almacenamiento no firma por lote");
    }
}
