package co.edu.futdata.persistence.dao;

import java.util.UUID;

/** Una asistencia por guardar (estado ya validado por el servicio; {@code observaciones} ya normalizadas, puede ser {@code null}). */
public record RegistroAsistencia(UUID idDeportista, String estado, String observaciones) {
}
