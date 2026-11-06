package co.edu.futdata.services.facade;

import co.edu.futdata.domain.entities.Acudiente;
import co.edu.futdata.domain.entities.Deportista;

/**
 * Resultado interno de la inscripción única (aún no es una respuesta de la API).
 *
 * @param deportista   como lo devuelve el DAO: con la categoría calculada y el {@code estadoInscripcion}
 * @param acudiente    el reutilizado (con sus datos de antes) o el recién creado
 * @param reutilizado  {@code true} si ya existía un acudiente con ese correo
 * @param cuentaCreada {@code true} si la inscripción creó la cuenta de acceso (hoy siempre {@code false})
 * @param parentesco   el del vínculo, sin tilde
 * @param correoEnviado {@code true} o {@code false} si se intentó enviar el enlace para crear la contraseña;
 *                     {@code null} si no había nada que enviar (hoy siempre {@code null})
 */
public record InscripcionResultado(
        Deportista deportista,
        Acudiente acudiente,
        boolean reutilizado,
        boolean cuentaCreada,
        String parentesco,
        Boolean correoEnviado) {
}
