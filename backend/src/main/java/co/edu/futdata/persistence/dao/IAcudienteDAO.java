package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Acudiente;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la tabla {@code acudiente} y a su vínculo con los deportistas ({@code acudiente_deportista}) para la
 * inscripción única. No abre transacciones: quien la usa decide qué va junto.
 *
 * <p>Sin registros: ni correos, ni nombres, ni teléfonos (son datos de familias de menores).
 */
public interface IAcudienteDAO {

    /** Busca por correo sin distinguir mayúsculas ni espacios al principio o al final. */
    Optional<Acudiente> buscarPorCorreo(String correo);

    /** El acudiente enlazado a esa cuenta ({@code acudiente.id_usuario}). */
    Optional<Acudiente> buscarPorUsuario(UUID idUsuario);

    /**
     * Inserta un acudiente sin documento ni fecha de nacimiento, con el correo en minúsculas.
     *
     * @param idUsuario la cuenta ya existente, o {@code null} si todavía no tiene
     * @throws DatoDuplicadoException con campo {@code correo} si ya hay un acudiente con ese correo, o con campo
     *                                {@code cuenta} si esa cuenta ya está enlazada a otro acudiente
     */
    Acudiente insertar(String nombre, String apellido, String correo, String telefono, UUID idUsuario);

    /**
     * Vincula al acudiente con el deportista. Si el vínculo ya existe no cambia nada (ni su parentesco).
     *
     * @param parentesco uno de los 7 valores de la base, sin tilde, o {@code null}
     * @return {@code true} si creó el vínculo; {@code false} si ya existía
     * @throws org.springframework.dao.DataIntegrityViolationException si el acudiente o el deportista no existen, o
     *                                                                 el parentesco no es uno de la lista
     */
    boolean vincular(UUID idAcudiente, UUID idDeportista, String parentesco);

    /**
     * Enlaza la cuenta solo si el acudiente todavía no tiene una ({@code id_usuario} nulo): nunca reemplaza una cuenta.
     *
     * @return {@code true} si lo enlazó; {@code false} si ya tenía cuenta o no existe
     * @throws DatoDuplicadoException con campo {@code cuenta} si esa cuenta ya es de otro acudiente
     */
    boolean enlazarUsuario(UUID idAcudiente, UUID idUsuario);
}
