package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.domain.entities.DatosAcudiente;

/**
 * Lo que pide la inscripción única: el deportista (los mismos campos del {@code POST /api/deportistas}; {@code activo}
 * se ignora, el deportista nace activo) y su acudiente. Interno del servicio: aún no es el cuerpo de ninguna ruta.
 */
public record InscripcionEntrada(DeportistaRequestDTO deportista, DatosAcudiente acudiente) {
}
