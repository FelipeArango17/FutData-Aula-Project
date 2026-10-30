package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;

/** DTO del entrenador y de la categoría a entidad y al revés. No valida ni limpia: eso es de ReglasEntrenador. */
public final class EntrenadorMapper {

    private EntrenadorMapper() {
    }

    public static Entrenador toEntity(EntrenadorRequestDTO dto) {
        Entrenador e = new Entrenador();
        e.setNombre(dto.nombre());
        e.setApellido(dto.apellido());
        e.setTipoDocumento(dto.tipoDocumento());
        e.setDocumento(dto.documento());
        e.setFechaNacimiento(dto.fechaNacimiento());
        e.setTelefono(dto.telefono());
        e.setCorreo(dto.correo());
        e.setEspecialidad(dto.especialidad());
        e.setHorarioTrabajo(dto.horarioTrabajo());
        e.setDireccion(dto.direccion());
        return e;
    }

    public static EntrenadorResponseDTO toDTO(Entrenador e) {
        return new EntrenadorResponseDTO(e.getId(), e.getNombre(), e.getApellido(), e.getTipoDocumento(),
                e.getDocumento(), e.getFechaNacimiento(), e.getTelefono(), e.getCorreo(), e.getEspecialidad(),
                e.getCategorias(), e.getHorarioTrabajo(), e.getDireccion());
    }

    /** @param entrenador quien la dirige; {@code null} solo si no se encontró (no debería pasar: es NOT NULL) */
    public static CategoriaResponseDTO toDTO(Categoria c, Entrenador entrenador) {
        CategoriaResponseDTO.EntrenadorResumenDTO resumen = entrenador == null ? null
                : new CategoriaResponseDTO.EntrenadorResumenDTO(entrenador.getId(), entrenador.getNombre(), entrenador.getApellido());
        return new CategoriaResponseDTO(c.getId(), c.getNombre(), c.getEdadMinima(), c.getEdadMaxima(), resumen);
    }
}
