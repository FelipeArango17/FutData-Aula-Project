package co.edu.futdata.domain.entities;

/** Estado guardado del cobro. "Vencido" no se guarda: es PENDIENTE con vencimiento anterior a hoy (vista pago_con_estado). */
public enum EstadoPago {
    PENDIENTE, APROBADO, RECHAZADO
}
