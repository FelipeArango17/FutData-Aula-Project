package co.edu.futdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos públicos del club que se imprimen en los comprobantes y reportes en PDF ({@code futdata.club.*}).
 * Tienen valor por defecto en {@code application.yml} y se cambian con {@code CLUB_NOMBRE}, {@code CLUB_NIT},
 * {@code CLUB_DIRECCION}, {@code CLUB_TELEFONO} y {@code CLUB_CORREO}. Un dato vacío no se imprime.
 *
 * @param nombre    razón social, por ejemplo "Asociación Deportiva Prado Fútbol Club"
 * @param nit       NIT sin puntos y con dígito de verificación ({@code 811032309-1}); se imprime
 *                  {@code NIT 811.032.309-1}
 * @param direccion dirección completa en una línea
 * @param telefono  celular de 10 dígitos ({@code 3137317366}); se imprime {@code 313 731 7366}
 * @param correo    correo de contacto del club
 */
@ConfigurationProperties(prefix = "futdata.club")
public record ClubProperties(String nombre, String nit, String direccion, String telefono, String correo) {
}
