package co.edu.futdata.persistence.dao;

import java.time.LocalDate;
import java.util.UUID;

/** Un deportista al que le toca el cobro por ciclo: su id, su nombre completo y la fecha de registro (el ancla del ciclo). */
public record DeportistaDeCiclo(UUID id, String nombre, LocalDate fechaRegistro) {
}
