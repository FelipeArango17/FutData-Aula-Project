package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.ExcepcionHorario;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la tabla {@code excepcion_horario} (migración 010): cancelaciones de una franja un día y entrenamientos
 * extras. Las excepciones que devuelve traen los datos para armar la sesión (ver {@link ExcepcionHorario}). Las
 * consultas {@code esDueno...} deciden la propiedad en SQL: un entrenador es dueño de una categoría cuando
 * {@code categoria.id_entrenador} es su fila de {@code entrenador} (la que tiene su {@code id_usuario}).
 */
public interface IExcepcionHorarioDAO {

    /**
     * Cancela la franja en esa fecha y devuelve el id de la excepción.
     *
     * @param motivo    ya normalizado; puede ser {@code null}
     * @param creadoPor puede ser {@code null}
     * @throws FranjaYaCanceladaException si esa franja ya está cancelada en esa fecha
     */
    UUID crearCancelacion(UUID idFranja, LocalDate fecha, String motivo, UUID creadoPor);

    /**
     * Inserta un extra (usa {@code fecha}, {@code idCategoria}, horas, {@code sede}, {@code idEntrenador}, {@code grupo},
     * {@code motivo} y {@code creadoPor}) y devuelve su id.
     *
     * @throws CruceDeHorarioException si el entrenador ya tiene otro extra que se solapa ese día
     */
    UUID crearExtra(ExcepcionHorario extra);

    Optional<ExcepcionHorario> buscarPorId(UUID id);

    /** @return {@code false} si no existe */
    boolean eliminar(UUID id);

    /** Las excepciones con fecha entre {@code desde} y {@code hasta} (ambas incluidas), por fecha, hora y categoría. */
    List<ExcepcionHorario> listar(LocalDate desde, LocalDate hasta);

    /** El usuario es el entrenador de la categoría de la franja o el entrenador de la franja. */
    boolean esDuenoDeFranja(UUID idUsuario, UUID idFranja);

    /** El usuario es el entrenador de esa categoría. */
    boolean esDuenoDeCategoria(UUID idUsuario, UUID idCategoria);

    /** Cancelación: como {@link #esDuenoDeFranja} con su franja. Extra: como {@link #esDuenoDeCategoria} con su categoría. */
    boolean esDuenoDeExcepcion(UUID idUsuario, UUID idExcepcion);

    /**
     * {@code true} si el entrenador tiene una franja ese día de la semana que se solapa con {@code [inicio, fin)} y
     * <b>no</b> está cancelada en esa fecha. Antes bloquea la fila del entrenador ({@code SELECT ... FOR UPDATE}) para
     * serializar a quienes crean extras del mismo entrenador: debe llamarse dentro de la transacción que después inserta
     * el extra. El cruce contra otros extras lo decide la base ({@link #crearExtra}).
     */
    boolean hayFranjaQueSeCruza(UUID idEntrenador, LocalDate fecha, LocalTime inicio, LocalTime fin);
}
