package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.DeportistaMapper;
import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.rules.ReglasDeportista;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.FormatoColombiano;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeportistaServiceImpl implements IDeportistaService {

    private final IDeportistaDAO deportistas;
    private final Clock reloj;

    @Autowired
    public DeportistaServiceImpl(IDeportistaDAO deportistas) {
        this(deportistas, Clock.system(FormatoColombiano.ZONA));
    }

    DeportistaServiceImpl(IDeportistaDAO deportistas, Clock reloj) {
        this.deportistas = deportistas;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeportistaResponseDTO> listar(boolean datosPrivados) {
        return deportistas.listar().stream()
                .map(d -> respuesta(d, datosPrivados)).toList();
    }

    @Override
    @Transactional
    public DeportistaResponseDTO crear(DeportistaRequestDTO datos) {
        if (datos == null) throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        Deportista limpio = ReglasDeportista.limpiar(DeportistaMapper.toEntity(datos));
        Map<String, String> errores = ReglasDeportista.validar(limpio, LocalDate.now(reloj));
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        if (deportistas.existeDocumento(limpio.getDocumento())) {
            throw new ConflictoException("documento: ya está registrado en otro deportista", java.util.List.of("documento"));
        }
        try {
            return consultar(deportistas.crear(limpio));
        } catch (DatoDuplicadoException e) {
            throw new ConflictoException("documento: ya está registrado en otro deportista", java.util.List.of("documento"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DeportistaResponseDTO consultar(UUID id) {
        Deportista d = deportistas.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un deportista con ese id."));
        return respuesta(d, true);
    }

    private DeportistaResponseDTO respuesta(Deportista d, boolean datosPrivados) {
        int edad = d.getFechaNacimiento().until(LocalDate.now(reloj)).getYears();
        return new DeportistaResponseDTO(d.getId(), d.getNombre(), d.getApellido(), d.getTipoDocumento(),
                d.getDocumento(), d.getFechaNacimiento(), edad,
                datosPrivados ? d.getTelefono() : null, datosPrivados ? d.getDireccion() : null, d.getIdCategoria());
    }
}
