package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.DatosAcudiente;
import co.edu.futdata.services.facade.InscripcionEntrada;

/**
 * Cuerpo de {@code POST /api/inscripciones}: el deportista (los mismos campos de {@code POST /api/deportistas};
 * {@code activo} se ignora, nace activo) y su acudiente. Los campos desconocidos se ignoran, como en el resto de la API.
 */
public record InscripcionRequestDTO(DeportistaRequestDTO deportista, Acudiente acudiente) {

    /**
     * Datos del acudiente que pide la inscripción.
     *
     * @param parentesco Madre, Padre, Abuelo, Abuela, Tia, Tio u Otro ({@code Tía} y {@code Tío} también se aceptan)
     */
    public record Acudiente(String nombre, String apellido, String correo, String telefono, String parentesco) {
    }

    /** A la entrada del servicio; un acudiente ausente queda nulo y el servicio lo responde campo por campo. */
    public InscripcionEntrada aEntrada() {
        DatosAcudiente datos = acudiente == null ? null : new DatosAcudiente(acudiente.nombre(), acudiente.apellido(),
                acudiente.correo(), acudiente.telefono(), acudiente.parentesco());
        return new InscripcionEntrada(deportista, datos);
    }
}
