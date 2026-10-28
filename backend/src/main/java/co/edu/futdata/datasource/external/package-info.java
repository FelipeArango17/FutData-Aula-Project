/**
 * <b>Fuente de datos: servicios externos</b>.
 *
 * <p>Interfaces y clientes de los sistemas externos del PPI: n8n para las notificaciones
 * ({@code INotificador} y su implementación {@code N8nClient}, tarea 340), Wompi para los pagos y la
 * API de Supabase. El servicio depende de la interfaz; la implementación hace la llamada HTTP
 * ({@code RestClient}) con las propiedades tipadas de {@code config}.
 *
 * <p>Contrato del webhook de n8n: {@code docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md}.
 */
package co.edu.futdata.datasource.external;
