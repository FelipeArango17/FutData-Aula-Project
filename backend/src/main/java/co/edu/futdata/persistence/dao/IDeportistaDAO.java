package co.edu.futdata.persistence.dao;

import co.edu.futdata.domain.entities.Deportista;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a la tabla deportista. Las lecturas reciben el año actual (del {@code Clock} del servicio, nunca
 * {@code current_date}) y devuelven la categoría calculada por año de nacimiento.
 */
public interface IDeportistaDAO {

    /**
     * Una página de deportistas, en una sola consulta.
     *
     * @param conEstadoInscripcion {@code true} calcula {@code estadoInscripcion} desde los documentos (solo para el
     *                             administrador); {@code false} lo deja en {@code null} sin consultar documentos
     */
    List<Deportista> listar(ConsultaDeportistas consulta, boolean conEstadoInscripcion);

    /**
     * Total de deportistas que cumplen los filtros de {@code consulta} (sin página ni orden), con el mismo {@code where}
     * y el mismo año que {@link #listar}.
     */
    long contar(ConsultaDeportistas consulta);

    /**
     * Inserta con {@code activo} en su valor por defecto ({@code true}).
     *
     * @throws DatoDuplicadoException el número de documento ya existe (lo garantiza el índice único)
     */
    UUID crear(Deportista deportista);

    /**
     * Reemplaza los campos editables (no toca foto, fecha de registro ni las columnas heredadas).
     *
     * @return {@code false} si no existe un deportista con ese id
     * @throws DatoDuplicadoException el número de documento ya es de otro deportista
     */
    boolean actualizar(UUID id, Deportista deportista);

    /**
     * Guarda la ruta de la foto en el bucket ({@code null} = sin foto).
     *
     * @return {@code false} si no existe un deportista con ese id
     */
    boolean actualizarFoto(UUID id, String ruta);

    /** Con la categoría calculada y el {@code estadoInscripcion}. */
    Optional<Deportista> buscarPorId(UUID id, int anioActual);

    /**
     * Los deportistas vinculados al usuario acudiente (el {@code id_usuario} del token), con categoría calculada,
     * {@code estadoInscripcion} y el parentesco del vínculo. Ordenados por apellido, nombre e id. Vacía si el usuario
     * no tiene fila de acudiente o no tiene hijos vinculados.
     */
    List<JugadorDeAcudiente> listarDeAcudiente(UUID idUsuario, int anioActual);

    /**
     * Uno de los deportistas del acudiente. La propiedad se comprueba en la misma consulta: si el deportista no existe
     * o es de otro acudiente, el resultado es el mismo (vacío).
     */
    Optional<JugadorDeAcudiente> buscarDeAcudiente(UUID idUsuario, UUID idDeportista, int anioActual);

    /**
     * Los tipos de {@code ReglasDeportista.DOCUMENTOS_OBLIGATORIOS} que el deportista aún no tiene APROBADOS, en el
     * orden de esa constante. Vacía si y solo si su {@code estadoInscripcion} es COMPLETA.
     *
     * <p>No comprueba propiedad: llamarlo solo después de {@link #buscarDeAcudiente}.
     */
    List<String> documentosObligatoriosFaltantes(UUID idDeportista);
}
