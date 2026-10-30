package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.Deportista;

/** Conversión entre DTOs y la entidad de deportista. */
public final class DeportistaMapper {

    private DeportistaMapper() {
    }

    public static Deportista toEntity(DeportistaRequestDTO dto) {
        Deportista d = new Deportista();
        d.setNombre(dto.nombre());
        d.setApellido(dto.apellido());
        d.setTipoDocumento(dto.tipoDocumento());
        d.setDocumento(dto.documento());
        d.setFechaNacimiento(dto.fechaNacimiento());
        d.setTelefono(dto.telefono());
        d.setDireccion(dto.direccion());
        return d;
    }
}
