package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Deportista;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** Acceso a la tabla deportista. */
public interface IDeportistaDAO {
    List<Deportista> listar();
    boolean existeDocumento(String documento);
    UUID crear(Deportista deportista);
    Optional<Deportista> buscarPorId(UUID id);
}
