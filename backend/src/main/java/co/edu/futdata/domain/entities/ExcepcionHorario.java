package co.edu.futdata.domain.entities;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Una excepción por fecha del horario fijo (tabla {@code excepcion_horario}, migración 010): la cancelación de una
 * franja un día concreto, o un entrenamiento extra.
 *
 * <p>Al <b>leer</b>, el DAO completa los datos de una cancelación con los de su franja (categoría, horas, sede,
 * entrenador y grupo) y trae {@code diaDeLaFranja}; en un extra {@code diaDeLaFranja} es {@code null}.
 * {@code categoria}, {@code entrenador} y {@code entrenadorActivo} no son columnas: salen de {@code categoria} y
 * {@code entrenador}. Al <b>escribir</b>, el DAO solo usa los campos propios de cada tipo.
 *
 * @param id               {@code null} al crear
 * @param tipo             {@link #CANCELACION} o {@link #EXTRA}
 * @param idFranja         solo en una cancelación
 * @param idEntrenador     {@code null} = "por turnos"
 * @param grupo            solo Cachorros y Semillero
 * @param creadoPor        el usuario que la registró; puede ser {@code null}
 */
public record ExcepcionHorario(UUID id, String tipo, LocalDate fecha, UUID idFranja, UUID idCategoria, String categoria,
                               Integer diaDeLaFranja, LocalTime horaInicio, LocalTime horaFin, String sede,
                               UUID idEntrenador, String entrenador, Boolean entrenadorActivo, String grupo,
                               String motivo, UUID creadoPor) {

    public static final String CANCELACION = "CANCELACION";
    public static final String EXTRA = "EXTRA";

    public boolean esCancelacion() {
        return CANCELACION.equals(tipo);
    }

    public boolean esExtra() {
        return EXTRA.equals(tipo);
    }
}
