package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.FranjaHorarioMapper;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import co.edu.futdata.api.dto.SesionSemanaResponseDTO;
import co.edu.futdata.domain.rules.ReglasEntrenador;
import co.edu.futdata.domain.rules.ReglasSemana;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IHorarioSemanaService;
import co.edu.futdata.services.facade.SemanaNoPermitidaException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HorarioSemanaServiceImpl implements IHorarioSemanaService {

    static final String FECHA_INVALIDA = "debe ser una fecha válida con el formato AAAA-MM-DD";

    private static final Comparator<SesionSemanaResponseDTO> ORDEN = Comparator
            .comparing(SesionSemanaResponseDTO::fecha)
            .thenComparing(SesionSemanaResponseDTO::horaInicio)
            .thenComparingInt(s -> ReglasEntrenador.CATEGORIAS.indexOf(s.categoria()))
            .thenComparing(SesionSemanaResponseDTO::grupo, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final IFranjaHorarioDAO franjas;
    private final Clock reloj;

    public HorarioSemanaServiceImpl(IFranjaHorarioDAO franjas, Clock reloj) {
        this.franjas = franjas;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public HorarioSemanaResponseDTO consultar(String fecha, boolean soloEnCurso) {
        LocalDate enCurso = ReglasSemana.lunesDeLaSemana(LocalDate.now(reloj));
        LocalDate lunes = enCurso;
        if (fecha != null && !fecha.isBlank()) {
            LocalDate pedida = ReglasSemana.fecha(fecha.strip())
                    .orElseThrow(() -> DatosInvalidosException.de("fecha", FECHA_INVALIDA));
            lunes = ReglasSemana.lunesDeLaSemana(pedida);
            if (soloEnCurso && !lunes.equals(enCurso)) {
                throw new SemanaNoPermitidaException();
            }
        }
        LocalDate semana = lunes;
        List<SesionSemanaResponseDTO> sesiones = franjas.listar(null, null, null).stream()
                .map(FranjaHorarioMapper::toDTO)
                .map(f -> aSesion(f, semana))
                .sorted(ORDEN)
                .toList();
        return new HorarioSemanaResponseDTO(lunes.toString(), ReglasSemana.sabado(lunes).toString(), sesiones);
    }

    private static SesionSemanaResponseDTO aSesion(FranjaHorarioResponseDTO f, LocalDate lunes) {
        return new SesionSemanaResponseDTO("FRANJA", "ACTIVO", f.idFranja(), null,
                ReglasSemana.fechaDeLaFranja(lunes, f.dia()).toString(), f.dia(), f.horaInicio(), f.horaFin(),
                f.idCategoria(), f.categoria(), f.sede(), f.idEntrenador(), f.entrenador(), f.entrenadorActivo(),
                f.grupo(), null, false);
    }
}
