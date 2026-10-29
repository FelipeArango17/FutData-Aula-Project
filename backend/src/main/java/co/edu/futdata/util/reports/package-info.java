/**
 * <b>Utilidades de reportes</b>.
 *
 * <p>Generación de reportes y exportaciones del panel administrativo (por ejemplo, asistencias o
 * pagos en PDF o Excel). La consulta de los datos la hace el servicio a través de los DAO; aquí solo
 * se da formato al archivo.
 *
 * <p><b>Comprobante de pago (HU-018).</b> {@link co.edu.futdata.util.reports.IGeneradorComprobante} y su
 * implementación con OpenPDF, {@link co.edu.futdata.util.reports.ComprobantePdfGenerator}, viven aquí porque
 * así lo define el PPI ("util.reports genera el comprobante en PDF") y porque generar el archivo no es una regla
 * de negocio ni un acceso a datos: es un servicio técnico que la fachada de pagos usa por su interfaz
 * ({@code PagoServiceImpl -> IGeneradorComprobante} en la Fig. 02-pagos), igual que {@code INotificador}.
 * La interfaz y la implementación comparten paquete como las demás utilidades transversales
 * ({@code util.security}); el servicio solo importa la interfaz y el DTO.
 *
 * <p>{@link co.edu.futdata.util.reports.ComprobantePagoDTO} está aquí y no en {@code api.dto}: no viaja por HTTP,
 * es la entrada del generador, y así {@code util} no depende de la capa de presentación. Guardar el PDF y
 * entregar el enlace (tarea 328) no se hace aquí.
 *
 * <p>{@link co.edu.futdata.util.reports.PlantillaReportePdf} tiene el encabezado, el pie, los colores y las
 * fuentes comunes, para reutilizarlos en el estado de cuenta en PDF.
 */
package co.edu.futdata.util.reports;
