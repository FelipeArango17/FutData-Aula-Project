package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.EstadoDocumento;
import co.edu.futdata.domain.rules.ReglasDocumento;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un documento de la inscripción, con la forma que ya usa el frontend ({@code documentosService}). No trae la ruta del
 * archivo: se descarga con una URL firmada de vida corta.
 *
 * @param cargadoPor       {@code Administrador} o {@code Acudiente}, derivado del rol de quien lo subió
 * @param cargadoPorCorreo correo de quien lo subió
 * @param comentario       el motivo del rechazo; {@code null} si no está rechazado
 * @param revisadoPor      correo de quien lo revisó; {@code null} mientras está PENDIENTE
 */
public record DocumentoResponseDTO(
        UUID id,
        UUID idDeportista,
        String tipo,
        String nombreOriginal,
        String tipoContenido,
        int tamanoBytes,
        EstadoDocumento estado,
        String comentario,
        LocalDate fechaVencimiento,
        String cargadoPor,
        String cargadoPorCorreo,
        Instant cargadoEn,
        String revisadoPor,
        Instant revisadoEn) {

    public static DocumentoResponseDTO de(Documento d) {
        return new DocumentoResponseDTO(d.id(), d.idDeportista(), d.tipo(), d.nombreOriginal(), d.tipoContenido(),
                d.tamanoBytes(), d.estado(), d.comentario(), d.fechaVencimiento(),
                ReglasDocumento.cargadoPor(d.rolCargadoPor()), d.cargadoPorCorreo(), d.cargadoEn(), d.revisadoPor(),
                d.revisadoEn());
    }
}
