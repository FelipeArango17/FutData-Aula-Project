package co.edu.futdata.api.dto;

import java.time.LocalDate;

/**
 * Datos de un deportista para crearlo ({@code POST}) o reemplazar sus campos editables ({@code PUT}).
 *
 * @param tipoDocumento TI, RC, CE o PASAPORTE
 * @param posicion      opcional, máximo 50 caracteres
 * @param grupo         {@code mañana} o {@code tarde}, solo en Cachorros y Semillero; {@code null} = sin grupo
 * @param activo        solo en el {@code PUT}, obligatorio; en el {@code POST} se ignora (el deportista se crea activo)
 */
public record DeportistaRequestDTO(
        String nombre,
        String apellido,
        String tipoDocumento,
        String documento,
        LocalDate fechaNacimiento,
        String telefono,
        String direccion,
        String posicion,
        String grupo,
        Boolean activo) {

    /** Solo los datos personales: sin posición ni grupo, y activo. */
    public DeportistaRequestDTO(String nombre, String apellido, String tipoDocumento, String documento,
                                LocalDate fechaNacimiento, String telefono, String direccion) {
        this(nombre, apellido, tipoDocumento, documento, fechaNacimiento, telefono, direccion, null, null, true);
    }
}
