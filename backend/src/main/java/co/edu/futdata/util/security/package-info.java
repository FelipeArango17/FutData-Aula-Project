/**
 * <b>Utilidades de seguridad</b>.
 *
 * <p>Componentes transversales de seguridad del PPI: la configuración de Spring Security
 * ({@code SeguridadConfig}), la validación del JWT de Supabase y los roles ({@code JwtValidator}) y las respuestas
 * {@code 401} y {@code 403} ({@code RespuestasSeguridad}). Con HU-002 llegan la generación y el hash de los códigos
 * 2FA ({@code GeneradorCodigo}) y la auditoría ({@code AuditoriaLogger}).
 */
package co.edu.futdata.util.security;
