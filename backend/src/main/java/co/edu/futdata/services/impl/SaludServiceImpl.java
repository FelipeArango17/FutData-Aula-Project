package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.SaludResponseDTO;
import co.edu.futdata.persistence.dao.ISaludDAO;
import co.edu.futdata.services.facade.ISaludService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class SaludServiceImpl implements ISaludService {

    private final ISaludDAO saludDAO;
    private final String version;

    public SaludServiceImpl(ISaludDAO saludDAO, ObjectProvider<BuildProperties> build) {
        this.saludDAO = saludDAO;
        // build-info.properties lo genera Maven; al ejecutar desde el IDE sin compilar con Maven puede no estar.
        BuildProperties info = build.getIfAvailable();
        this.version = info != null ? info.getVersion() : "desarrollo";
    }

    @Override
    public SaludResponseDTO consultar() {
        String baseDeDatos = saludDAO.baseDeDatosDisponible() ? SaludResponseDTO.UP : SaludResponseDTO.DOWN;
        return new SaludResponseDTO(SaludResponseDTO.UP, baseDeDatos, version);
    }
}
