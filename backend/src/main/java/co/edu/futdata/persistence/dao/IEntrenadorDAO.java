package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Entrenador;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a la tabla {@code entrenador}. Los entrenadores que devuelve traen los nombres de sus categorías. */
public interface IEntrenadorDAO {

    /** Todos, ordenados por apellido y nombre, cada uno con sus categorías en el orden oficial. */
    List<Entrenador> listar();

    Optional<Entrenador> buscarPorId(UUID id);

    /**
     * {@code true} si otro entrenador tiene el mismo tipo y número de documento.
     *
     * @param excluirId el entrenador que se edita, para que no choque consigo mismo; {@code null} al crear
     */
    boolean existeDocumento(String tipoDocumento, String documento, UUID excluirId);

    /** Igual que {@link #existeDocumento}, con el correo y sin distinguir mayúsculas. */
    boolean existeCorreo(String correo, UUID excluirId);

    /**
     * Inserta los datos personales (sin categorías) y devuelve el id que asignó la base de datos.
     *
     * @throws DatoDuplicadoException si la base de datos rechaza el documento o el correo por repetidos
     */
    UUID crear(Entrenador entrenador);

    /**
     * Actualiza los datos personales del entrenador con {@code entrenador.getId()}. No cambia sus categorías,
     * {@code activo}, {@code documentos} ni {@code id_usuario}.
     *
     * @return {@code false} si no existe
     * @throws DatoDuplicadoException si la base de datos rechaza el documento o el correo por repetidos
     */
    boolean actualizar(Entrenador entrenador);

    /**
     * Activa o desactiva al entrenador. No toca sus categorías: eso lo decide el servicio.
     *
     * @return {@code false} si no existe
     */
    boolean cambiarActivo(UUID id, boolean activo);
}
