package co.edu.futdata.persistence.dao;

import java.util.List;
import java.util.UUID;

/** A quién avisar de un cambio en el horario de una categoría. */
public interface IDestinatariosDAO {

    /**
     * Los acudientes con correo de los deportistas <b>activos</b> de la categoría, sin duplicados por correo (sin
     * distinguir mayúsculas; un acudiente con dos hijos en la categoría sale una vez), por apellido y nombre. No se
     * filtra por grupo. La categoría de cada deportista se calcula por año de nacimiento, como en el 2A
     * ({@code anio - año de nacimiento} entre la edad mínima y máxima), nunca con {@code deportista.id_categoria}.
     *
     * @param anioActual el año del {@code Clock} de Bogotá
     */
    List<Destinatario> acudientesDeCategoria(UUID idCategoria, int anioActual);

    /**
     * Los acudientes vinculados a <b>un</b> deportista que tienen correo (nulo o en blanco se ignoran), sin duplicados
     * por correo (sin distinguir mayúsculas ni espacios), por apellido y nombre. No filtra por {@code activo}: un
     * deportista inactivo también puede tener documentos. Vacía si el deportista no existe o no tiene acudientes con correo.
     */
    List<Destinatario> acudientesDeDeportista(UUID idDeportista);
}
