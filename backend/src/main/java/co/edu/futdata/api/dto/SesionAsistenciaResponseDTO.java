package co.edu.futdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La sesión, si quien consulta puede pasar lista, y la lista de deportistas (activos, por apellido y nombre). */
public record SesionAsistenciaResponseDTO(
        DatosSesionAsistenciaDTO sesion,
        @Schema(description = "true si quien consulta puede pasar lista de esta sesión y la fecha no es futura")
        boolean puedeRegistrar,
        List<DeportistaAsistenciaDTO> deportistas) {
}
