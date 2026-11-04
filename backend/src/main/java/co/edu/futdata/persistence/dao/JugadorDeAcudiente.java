package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Deportista;

/**
 * Un deportista vinculado a un acudiente, con el parentesco de ese vínculo (sin tilde, como en la base; puede ser
 * {@code null}). El deportista trae la categoría y el {@code estadoInscripcion} calculados.
 */
public record JugadorDeAcudiente(Deportista deportista, String parentesco) {
}
