/**
 * <b>Fuente de datos: servicios externos</b>.
 *
 * <p>Interfaces y clientes de los sistemas externos del PPI: n8n para las notificaciones
 * ({@link co.edu.futdata.datasource.external.INotificador} y su implementación
 * {@link co.edu.futdata.datasource.external.N8nClient}), Wompi para los pagos y la API de Supabase. El
 * servicio depende de la interfaz; la implementación hace la llamada HTTP ({@code RestClient}) con las
 * propiedades tipadas de {@code config}.
 *
 * <p><b>Por qué {@code INotificador} está aquí y no en {@code services.facade}.</b> En la Fig. 4
 * ({@code 03-autenticacion}) {@code INotificador} queda en la fila de las interfaces de acceso a datos, junto
 * a los {@code I<Recurso>DAO}, y {@code N8nClient} en la de implementaciones: es una dependencia que la
 * fachada <i>usa</i> ({@code AutenticacionServiceImpl -> INotificador}), no una operación que la fachada
 * <i>ofrece</i> al controlador. {@code services.facade} solo tiene las interfaces que ve el controlador. Así
 * como {@code persistence.dao} separa la interfaz del DAO de su implementación JDBC, aquí la interfaz y el
 * cliente HTTP comparten paquete porque los dos pertenecen a la capa de fuente de datos externa, y los
 * servicios solo importan la interfaz.
 *
 * <p>Las excepciones de {@code INotificador} ({@code NotificacionException} y sus subclases) viven junto a
 * la interfaz porque son parte de su contrato: el servicio las atrapa y decide qué decirle al usuario. No
 * llegan al controlador.
 *
 * <p>Contrato del webhook de n8n: {@code docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md}.
 */
package co.edu.futdata.datasource.external;
