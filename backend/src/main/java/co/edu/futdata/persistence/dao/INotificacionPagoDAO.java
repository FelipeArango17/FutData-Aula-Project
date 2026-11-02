package co.edu.futdata.persistence.dao;

import java.util.UUID;

/** Avisos de pago para el acudiente (tabla {@code notificacion_pago}). */
public interface INotificacionPagoDAO {

    void crear(UUID idAcudiente, String mensaje);
}
