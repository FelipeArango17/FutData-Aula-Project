package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;

/** El horario de una semana (lunes a sábado) armado con las franjas fijas del club (bloque 4B-1, sin excepciones). */
public interface IHorarioSemanaService {

    /**
     * @param fecha       {@code AAAA-MM-DD} de cualquier día de la semana pedida; {@code null} o vacío = hoy en Bogotá
     * @param soloEnCurso {@code true} para el acudiente: solo puede pedir la semana en curso
     * @throws DatosInvalidosException    si {@code fecha} no es una fecha válida
     * @throws SemanaNoPermitidaException si {@code soloEnCurso} y la semana pedida no es la en curso
     */
    HorarioSemanaResponseDTO consultar(String fecha, boolean soloEnCurso);
}
