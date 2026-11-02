package co.edu.futdata.api.dto;

import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.domain.rules.ReglasFranja;

/** Convierte entre los DTO del horario del club y {@link FranjaHorario} / {@link ReglasFranja.Entrada}. */
public final class FranjaHorarioMapper {

    private FranjaHorarioMapper() {
    }

    public static ReglasFranja.Entrada toEntrada(FranjaHorarioRequestDTO d) {
        return new ReglasFranja.Entrada(d.idCategoria(), d.dia(), d.horaInicio(), d.horaFin(), d.sede(),
                d.idEntrenador(), d.grupo());
    }

    public static FranjaHorarioResponseDTO toDTO(FranjaHorario f) {
        return new FranjaHorarioResponseDTO(f.getId(), f.getIdCategoria(), f.getCategoria(), f.getDia(),
                ReglasFranja.formatear(f.getHoraInicio()), ReglasFranja.formatear(f.getHoraFin()), f.getSede(),
                f.getIdEntrenador(), f.getEntrenador(), f.getEntrenadorActivo(), f.getGrupo());
    }
}
