package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.Deportista;

/** Conversión entre DTOs y la entidad de deportista. */
public final class DeportistaMapper {

    private DeportistaMapper() {
    }

    /** {@code activo} nulo queda en {@code true}; el servicio exige que venga en el {@code PUT}. */
    public static Deportista toEntity(DeportistaRequestDTO dto) {
        Deportista d = new Deportista();
        d.setNombre(dto.nombre());
        d.setApellido(dto.apellido());
        d.setTipoDocumento(dto.tipoDocumento());
        d.setDocumento(dto.documento());
        d.setFechaNacimiento(dto.fechaNacimiento());
        d.setTelefono(dto.telefono());
        d.setDireccion(dto.direccion());
        d.setPosicion(dto.posicion());
        d.setGrupo(dto.grupo());
        d.setActivo(dto.activo() == null || dto.activo());
        return d;
    }
}
