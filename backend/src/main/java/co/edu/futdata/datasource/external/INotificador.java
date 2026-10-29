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

    /** Código de verificación al iniciar sesión. Respuesta síncrona: el correo ya salió. */
    String CODIGO_2FA = "CODIGO_2FA";

    /** Aviso de entrenamiento creado, modificado o cancelado. Respuesta asíncrona: recibido, no entregado. */
    String CAMBIO_ENTRENAMIENTO = "CAMBIO_ENTRENAMIENTO";

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
