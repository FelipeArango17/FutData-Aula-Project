/**
 * <b>Utilidades de seguridad</b>.
 *
 * <p>Componentes transversales de seguridad del PPI: validación del JWT de Supabase
 * ({@code JwtValidator}), generación y hash de códigos 2FA ({@code GeneradorCodigo}) y auditoría
 * ({@code AuditoriaLogger}). Llegan con las historias HU-001, HU-002 y HU-005.
 *
 * <p>Mientras tanto la API no tiene autenticación: no debe desplegarse en un entorno público.
 */
package co.edu.futdata.util.security;
