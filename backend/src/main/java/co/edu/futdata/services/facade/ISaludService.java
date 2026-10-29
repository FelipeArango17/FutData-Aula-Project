package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.SaludResponseDTO;

/** Fachada del estado del backend. */
public interface ISaludService {

    /** Revisa la base de datos y devuelve el estado general con la versión del backend. */
    SaludResponseDTO consultar();
}
