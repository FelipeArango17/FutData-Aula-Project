package co.edu.futdata.persistence.dao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asistencia a los entrenamientos: las tablas {@code entrenamiento} y {@code asistencia} (migraciones 001, 004 y 012).
 * La sesión del horario (franja + fecha, o extra) se identifica en {@code entrenamiento} por
 * {@code (id_categoria, fecha, hora_inicio, lugar)}; esa fila se crea la primera vez que se pasa lista.
 */
public interface IAsistenciaDAO {

    /**
     * {@code true} si la franja tiene una CANCELACION en esa fecha. Es el mismo criterio del horario de la semana: la
     * cancelación cuenta por {@code (id_franja, fecha)}.
     */
    boolean franjaCancelada(UUID idFranja, LocalDate fecha);

    /** El entrenamiento ya creado de esa sesión, si lo hay (no lo crea: lo usa la consulta). */
    Optional<UUID> buscarEntrenamiento(UUID idCategoria, LocalDate fecha, LocalTime horaInicio, String sede);

    /**
     * Busca el entrenamiento de la sesión y, si no existe, lo crea (estado PROGRAMADO) con {@code idEntrenador}
     * (el asignado; {@code null} = "por turnos"). Si otra petición lo crea al mismo tiempo, devuelve ese mismo. Debe
     * llamarse dentro de la transacción del servicio: no deja la transacción abortada aunque haya un conflicto.
     *
     * @throws SolapeDeEntrenamientoException si la categoría (o el entrenador) ya tiene otro entrenamiento que se cruza
     */
    UUID buscarOCrearEntrenamiento(UUID idCategoria, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, String sede,
                                   UUID idEntrenador);

    /**
     * Los deportistas ACTIVOS de la categoría, por apellido y nombre, y si {@code grupo} no es {@code null}, solo los de
     * ese grupo. Cada uno trae su estado y observaciones guardados en {@code idEntrenamiento} ({@code null} = esa sesión
     * aún no tiene entrenamiento: nadie tiene estado).
     *
     * <p>La categoría es la <b>calculada</b> por año de nacimiento con el mismo criterio de {@code DeportistaDAOImpl}
     * ({@code anio - año de nacimiento} entre {@code edad_minima} y {@code edad_maxima}), nunca la columna heredada
     * {@code deportista.id_categoria}. Limitación: {@code anio} es el del reloj de HOY, así que para una fecha pasada la
     * categoría se calcula con la regla vigente hoy y no con la de ese día.
     */
    List<DeportistaDeSesion> listarDeportistas(UUID idCategoria, String grupo, int anio, UUID idEntrenamiento);

    /**
     * Guarda las asistencias en UNA sola sentencia (upsert por {@code (id_deportista, id_entrenamiento)}): o quedan
     * todas o ninguna, aunque no haya transacción abierta. Al corregir actualiza estado, observaciones,
     * {@code id_entrenador} y {@code registrado_en}.
     *
     * @param idEntrenador quien pasó lista; {@code null} si fue el administrador
     * @param registros    sin deportistas repetidos
     */
    void guardar(UUID idEntrenamiento, UUID idEntrenador, List<RegistroAsistencia> registros);
}
