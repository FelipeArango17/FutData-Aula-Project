package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import java.util.UUID;
import java.util.List;

public interface IDeportistaService {
    List<DeportistaResponseDTO> listar(boolean datosPrivados);
    DeportistaResponseDTO crear(DeportistaRequestDTO datos);
    DeportistaResponseDTO consultar(UUID id);
}
