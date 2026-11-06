package co.edu.futdata.persistence.dao;

import java.util.UUID;

/**
 * Un deportista de la lista de una sesión, con lo que ya se guardó de su asistencia en ese entrenamiento.
 *
 * @param estado        {@code PRESENTE}, {@code AUSENTE} o {@code JUSTIFICADO}; {@code null} si aún no se registró
 * @param observaciones {@code null} si no hay
 */
public record DeportistaDeSesion(UUID idDeportista, String nombre, String apellido, String estado, String observaciones) {
}
