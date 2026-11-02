package co.edu.futdata.api.dto;

/** Rechazo manual de un cobro PENDIENTE; la nota interna es opcional. */
public record RechazarPagoRequestDTO(String nota) {
}
