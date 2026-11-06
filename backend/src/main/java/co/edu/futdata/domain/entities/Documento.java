package co.edu.futdata.domain.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un documento de la inscripción de un deportista (tabla {@code documento}). Inmutable: cada revisión se hace con el
 * DAO y se vuelve a leer.
 *
 * @param ruta             ruta del archivo en el bucket privado {@code documentos}; <b>nunca sale por la API</b>
 *                         (se entrega una URL firmada de vida corta)
 * @param rolCargadoPor    rol de quien lo subió ({@code ADMINISTRADOR} o {@code ACUDIENTE}); {@code null} si esa cuenta
 *                         ya no existe
 * @param cargadoPorCorreo correo de quien lo subió; {@code null} si la cuenta ya no existe
 * @param revisadoPor      correo de quien lo aprobó o rechazó; {@code null} mientras está PENDIENTE
 */
public record Documento(
        UUID id,
        UUID idDeportista,
        String tipo,
        String ruta,
        String nombreOriginal,
        String tipoContenido,
        int tamanoBytes,
        EstadoDocumento estado,
        String comentario,
        LocalDate fechaVencimiento,
        String rolCargadoPor,
        String cargadoPorCorreo,
        Instant cargadoEn,
        String revisadoPor,
        Instant revisadoEn) {
}
