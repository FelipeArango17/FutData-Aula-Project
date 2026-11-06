package co.edu.futdata.api.dto;

import co.edu.futdata.services.facade.InscripcionResultado;
import java.util.UUID;

/**
 * Respuesta de {@code POST /api/inscripciones}. No trae nada de Supabase Auth (ni el id de la cuenta).
 *
 * @param deportista    el mismo objeto que devuelve {@code POST /api/deportistas}
 * @param parentesco    el del vínculo, sin tilde
 * @param correoEnviado siempre presente: {@code true} n8n aceptó el correo con el enlace para crear la contraseña;
 *                      {@code false} no se pudo enviar (la inscripción quedó hecha); {@code null} no había cuenta nueva
 */
public record InscripcionResponseDTO(
        DeportistaResponseDTO deportista,
        Acudiente acudiente,
        String parentesco,
        Boolean correoEnviado) {

    /**
     * @param reutilizado  {@code true} si ya existía un acudiente con ese correo (sus datos no cambiaron)
     * @param cuentaCreada {@code true} si la inscripción le creó la cuenta de acceso
     */
    public record Acudiente(UUID id, String nombre, String apellido, String correo, boolean reutilizado,
                            boolean cuentaCreada) {
    }

    public static InscripcionResponseDTO de(InscripcionResultado r, DeportistaResponseDTO deportista) {
        var a = r.acudiente();
        return new InscripcionResponseDTO(deportista,
                new Acudiente(a.id(), a.nombre(), a.apellido(), a.correo(), r.reutilizado(), r.cuentaCreada()),
                r.parentesco(), r.correoEnviado());
    }
}
