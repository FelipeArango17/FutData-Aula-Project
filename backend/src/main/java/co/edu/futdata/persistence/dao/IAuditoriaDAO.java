package co.edu.futdata.persistence.dao;

import java.util.Map;
import java.util.UUID;

/** Escribe en la tabla {@code auditoria}. El detalle nunca debe llevar llaves, secretos ni datos personales. */
public interface IAuditoriaDAO {

    /** @param idUsuario quien actuó; {@code null} si fue el sistema o el webhook de Wompi */
    void registrar(UUID idUsuario, String evento, Map<String, Object> detalle);
}
