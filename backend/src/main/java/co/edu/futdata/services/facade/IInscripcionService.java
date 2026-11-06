package co.edu.futdata.services.facade;

/** Inscripción única: el deportista, su acudiente y el vínculo entre ambos, todo o nada. Solo el ADMINISTRADOR. */
public interface IInscripcionService {

    /**
     * @throws SinPermisoException       si el solicitante no es ADMINISTRADOR (falla cerrado: también si es nulo)
     * @throws DatosInvalidosException   {@code 400}, todos los errores juntos, con los campos prefijados
     *                                   {@code deportista.<campo>} y {@code acudiente.<campo>}
     * @throws ConflictoException        {@code 409}: documento del deportista repetido ({@code deportista.documento}) o
     *                                   correo del acudiente registrado por otra inscripción al mismo tiempo
     *                                   ({@code acudiente.correo})
     */
    InscripcionResultado inscribir(InscripcionEntrada entrada, Solicitante solicitante);
}
