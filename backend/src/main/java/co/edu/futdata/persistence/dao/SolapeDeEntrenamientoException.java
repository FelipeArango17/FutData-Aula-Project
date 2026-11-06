package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.rules.ReglasAsistencia;

/**
 * La base de datos rechazó crear el entrenamiento de una sesión por una restricción de exclusión
 * ({@code entrenamiento_cruce_categoria_ex} o {@code entrenamiento_cruce_entrenador_ex}, SQLState {@code 23P01}) y la
 * sesión misma no existe: es un solape real con OTRO entrenamiento (otra hora que se cruza). La lanza el DAO para que
 * el servicio responda {@code 409}. Si la sesión sí existe, fue una carrera con otra petición y el DAO la devuelve sin
 * lanzar nada. El mensaje ya es el que ve el usuario.
 */
public class SolapeDeEntrenamientoException extends RuntimeException {

    public SolapeDeEntrenamientoException() {
        super(ReglasAsistencia.SOLAPE);
    }
}
