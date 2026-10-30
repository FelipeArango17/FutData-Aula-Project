package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.CodigoVerificacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ICodigoVerificacionDAO {
    Optional<CodigoVerificacion> buscarUltimo(UUID usuarioId);
    UUID guardar(CodigoVerificacion codigo);
    int incrementarIntentos(UUID id);
    void bloquear(UUID id, Instant hasta);
    void marcarUsado(UUID id);
}
