package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.api.dto.EntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import java.util.List;
import java.util.UUID;

/**
 * Fachada de entrenadores y de la asignación de sus categorías (HU-025, CU-10).
 *
 * <p>Errores: {@link DatosInvalidosException} ({@code 400}), {@link RecursoNoEncontradoException} ({@code 404}) y
 * {@link ConflictoException} ({@code 409}).
 */
public interface IEntrenadorService {

    /** Todos los entrenadores, ordenados por apellido y nombre, con sus categorías. */
    List<EntrenadorResponseDTO> listar();

    EntrenadorResponseDTO consultar(UUID id);

    /**
     * Valida, revisa duplicados de documento y correo, y registra los datos personales (sin categorías) junto con su
     * cuenta de acceso sin contraseña. Después envía el enlace para crear la contraseña: el resultado va en
     * {@code correoEnviado} y una falla del envío no deshace el alta. {@code 409} si el correo ya es de otra cuenta;
     * {@code 503} si Supabase Auth no responde (no se guarda nada).
     */
    EntrenadorResponseDTO crear(EntrenadorRequestDTO datos);

    /**
     * Reenvía el enlace para crear la contraseña (si no tiene cuenta, la crea). {@code 409} si está inactivo, si no
     * tiene correo o si el correo ya es de otra cuenta; {@code 429} si ya se le envió uno hace menos de un minuto;
     * {@code 503} si Supabase Auth o el correo fallan. Nunca devuelve el enlace.
     */
    void reenviarEnlace(UUID id);

    /**
     * Igual que {@link #crear}, sobre un entrenador existente. No cambia sus categorías. Si tiene cuenta de acceso, el
     * correo no se puede cambiar ({@code 409}; deuda: no se sincroniza con {@code usuario} ni con Supabase Auth). Si
     * viene el mismo, con otras mayúsculas o espacios, pasa.
     */
    EntrenadorResponseDTO editar(UUID id, EntrenadorRequestDTO datos);

    /**
     * Deja al entrenador con exactamente las categorías pedidas, en una sola transacción. Según las decisiones D1
     * y D7, cada categoría tiene como máximo un entrenador, así que:
     * <ul>
     *   <li>una categoría sin entrenador pasa a este directamente;</li>
     *   <li>una categoría de otro entrenador pasa a este solo si {@code reasignar} es {@code true};</li>
     *   <li>una categoría que tenía y no está en la lista queda sin entrenador.</li>
     * </ul>
     * Un entrenador inactivo no puede recibir categorías ({@code 409}).
     */
    EntrenadorResponseDTO asignarCategorias(UUID id, AsignarCategoriasRequestDTO peticion);

    /**
     * Activa o desactiva al entrenador (CU-10, camino alternativo 2; decisión D8). Desactivarlo deja sin
     * entrenador las categorías que dirigía, en la misma transacción; su historial se conserva. Pedir el estado
     * que ya tiene no cambia nada. Si tiene cuenta, también revoca (baneo en Supabase Auth, {@code usuario.activo} y
     * sus sesiones verificadas) o restaura su acceso; {@code 503} si Supabase Auth no responde (no cambia nada).
     */
    EntrenadorResponseDTO cambiarEstado(UUID id, CambiarEstadoEntrenadorRequestDTO peticion);

    /** Las categorías en el orden oficial, cada una con el entrenador que la dirige. */
    List<CategoriaResponseDTO> listarCategorias();
}
