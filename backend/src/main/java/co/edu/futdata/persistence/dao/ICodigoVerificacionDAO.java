package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.CodigoVerificacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ICodigoVerificacionDAO {
    Optional<CodigoVerificacion> buscarUltimo(UUID usuarioId);
    UUID guardar(CodigoVerificacion codigo);
    void bloquearUsuario(UUID usuarioId);
    void invalidarPendientes(UUID usuarioId);
    void limpiarVencidos(UUID usuarioId, Instant ahora);
    int incrementarIntentos(UUID id);
    void bloquear(UUID id, Instant hasta);
    void marcarUsado(UUID id);
    void marcarSesionVerificada(UUID usuarioId, Instant hasta);
    boolean sesionVerificada(UUID usuarioId, Instant ahora);
}
