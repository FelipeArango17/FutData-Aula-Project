package co.edu.futdata.datasource.external;

import java.util.Map;

/**
 * Envía notificaciones a los usuarios por un servicio externo (n8n). Los servicios
 * ({@code services.impl}) dependen de esta interfaz, nunca de {@link N8nClient}.
 *
 * <p>Contrato: {@code docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md}. Cada servicio
 * arma los {@code datos} de su evento (HU-002 para {@link #CODIGO_2FA}, HU-026 para
 * {@link #CAMBIO_ENTRENAMIENTO}); el notificador solo los entrega.
 */
public interface INotificador {

    /**
     * Código de verificación al iniciar sesión. Respuesta asíncrona ({@code 202}): recibido, no entregado. Si el
     * SMTP falla después, n8n solo lo registra y el usuario debe usar "Reenviar código".
     */
    String CODIGO_2FA = "CODIGO_2FA";

    /** Aviso de entrenamiento creado, modificado o cancelado. Respuesta asíncrona: recibido, no entregado. */
    String CAMBIO_ENTRENAMIENTO = "CAMBIO_ENTRENAMIENTO";

    /**
     * Enlace para crear la contraseña (alta de entrenador y su reenvío; regla 9). Respuesta <b>síncrona</b>, a
     * diferencia de los otros dos: n8n responde después de entregar el correo al SMTP, {@code 200} si salió y
     * {@code 502} si no. No usa el {@code 202}. Por eso tiene su propio tiempo de espera
     * ({@code futdata.n8n.timeout-enlace}, 15 s).
     *
     * <p>Datos: {@code plantilla} (hoy solo {@code BIENVENIDA_ENTRENADOR}), {@code correo}, {@code nombre},
     * {@code enlace} y {@code vigenciaHoras}. El {@code enlace} es secreto: no se registra en logs ni en el registro
     * de fallas de n8n.
     */
    String ENLACE_CONTRASENA = "ENLACE_CONTRASENA";

    /**
     * Recordatorio de un cobro de mensualidad creado con anticipación (cobros por ciclo de ingreso). Un correo por acudiente.
     * Respuesta asíncrona: recibido, no entregado.
     *
     * <p>Datos: {@code correo}, {@code nombreAcudiente}, {@code nombreDeportista}, {@code concepto}, {@code monto} (pesos
     * enteros), {@code fechaVencimiento} ({@code AAAA-MM-DD}), {@code diasRestantes} y {@code enlacePagos}
     * ({@code {FRONTEND_URL}/acudiente/pagos}, sin datos secretos).
     */
    String RECORDATORIO_PAGO = "RECORDATORIO_PAGO";

    /**
     * Aviso al acudiente de que el administrador rechazó un documento de la inscripción de su deportista (bloque 3). Un
     * correo por acudiente. Respuesta asíncrona ({@code 202}): recibido, no entregado; usa el tiempo de espera normal.
     *
     * <p>Datos, todos de una línea salvo el comentario: {@code correo} (máx. 254), {@code nombreAcudiente} (máx. 120),
     * {@code nombreDeportista} (máx. 120), {@code documento} (etiqueta legible del tipo, máx. 120), {@code comentario}
     * (el motivo del rechazo, máx. 300) y {@code enlaceDocumentos} ({@code {FRONTEND_URL}/acudiente/documentos}, sin datos
     * secretos, máx. 2000). El comentario y los nombres son datos de menores y de sus familias: no se registran.
     */
    String DOCUMENTO_RECHAZADO = "DOCUMENTO_RECHAZADO";

    /**
     * Pide el envío de una notificación. Retorna normalmente si n8n la aceptó ({@code 200} o {@code 202}).
     * No reintenta.
     *
     * @param evento nombre del evento, por ejemplo {@link #CODIGO_2FA}
     * @param datos  campos del evento según el contrato; no vacío
     * @throws NotificacionRechazadaException      n8n respondió {@code 400}: los datos no cumplen el contrato
     * @throws NotificadorMalConfiguradoException  falta la URL o el token, o n8n respondió {@code 401}
     * @throws NotificacionNoEnviadaException      {@code 502}, tiempo de espera agotado, error de red u otro código
     * @throws IllegalArgumentException            {@code evento} vacío o {@code datos} vacío (error de programación)
     */
    void notificar(String evento, Map<String, Object> datos);
}
