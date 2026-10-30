package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import java.util.UUID;

public interface IDeportistaService {
    DeportistaResponseDTO crear(DeportistaRequestDTO datos);
    DeportistaResponseDTO consultar(UUID id);
}
