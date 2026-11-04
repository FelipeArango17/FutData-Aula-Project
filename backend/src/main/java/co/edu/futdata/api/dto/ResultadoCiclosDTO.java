package co.edu.futdata.api.dto;

/**
 * Resultado de generar los cobros por ciclo de ingreso.
 *
 * @param creadas           cobros creados (mensualidades, inscripciones y uniformes)
 * @param yaExistentes      cobros que tocaba crear y ya existían (incluye los que otra ejecución simultánea creó antes)
 * @param avisosEnviados    recordatorios por correo que n8n aceptó
 * @param avisosFallidos    recordatorios que no se pudieron enviar (el cobro se creó igual)
 * @param deportistasConError deportistas que fallaron por un error inesperado; los demás se procesaron
 */
public record ResultadoCiclosDTO(int creadas, int yaExistentes, int avisosEnviados, int avisosFallidos, int deportistasConError) {
}
