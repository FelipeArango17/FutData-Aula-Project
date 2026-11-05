package co.edu.futdata.persistence.dao;

import java.util.List;
import java.util.UUID;

/**
 * Filtros, orden y página de la lista de deportistas, ya validados por el servicio. {@code listar} y {@code contar}
 * arman el mismo {@code where} con estos valores y con el mismo {@code anio}, así el total siempre coincide con la lista.
 *
 * @param activo               {@code null} = todos
 * @param categorias           nombres de categoría (ya validados); se compara con la categoría <b>calculada</b> por año
 *                             de nacimiento. Vacía (o {@code null}) = sin filtrar por nombre
 * @param idUsuarioEntrenador  {@code usuario.id} del entrenador que consulta: solo salen los deportistas de las
 *                             categorías que tiene a cargo ({@code categoria.id_entrenador}, sin mirar
 *                             {@code entrenador.activo}). {@code null} = el administrador, sin límite de propietario
 * @param anio                 año del reloj de la aplicación (hora de Colombia): define la categoría calculada
 * @param pagina               desde 0
 * @param tamano               de 1 a 100
 */
public record ConsultaDeportistas(Boolean activo, List<String> categorias, UUID idUsuarioEntrenador, int anio,
                                  OrdenDeportista orden, boolean descendente, int pagina, int tamano) {

    public ConsultaDeportistas {
        categorias = categorias == null ? List.of() : List.copyOf(categorias);
    }
}
