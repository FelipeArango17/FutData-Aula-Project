package co.edu.futdata.domain.entities;

/**
 * Los datos del acudiente que pide la inscripción única: solo contacto y parentesco con el deportista (el documento y
 * la fecha de nacimiento no se piden; migración 011).
 *
 * @param parentesco uno de {@code ReglasAcudiente.PARENTESCOS}, sin tilde una vez limpio
 */
public record DatosAcudiente(String nombre, String apellido, String correo, String telefono, String parentesco) {
}
