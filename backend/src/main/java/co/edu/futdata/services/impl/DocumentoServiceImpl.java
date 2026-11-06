package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.DescargaDocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentoResponseDTO;
import co.edu.futdata.api.dto.DocumentosDeportistaResponseDTO;
import co.edu.futdata.api.dto.RechazoDocumentoResponseDTO;
import co.edu.futdata.api.dto.RevisionDocumentoResponseDTO;
import co.edu.futdata.config.DocumentosProperties;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.ArchivoNoEncontradoException;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.rules.ReglasDocumento;
import co.edu.futdata.persistence.dao.Destinatario;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.IDestinatariosDAO;
import co.edu.futdata.persistence.dao.IDocumentoDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IDocumentoService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Documentos de la inscripción: subir, listar, descargar, aprobar y rechazar (con aviso por correo al rechazar).
 *
 * <p><b>Subir</b> (decisiones 3, 4 y 12 de {@code docs/PRESENTACION-TECNICA.md} §9.1.1): autoriza, valida, hace una
 * revisión rápida sin bloqueo, sube a Storage <b>fuera de toda transacción</b> y solo después abre una transacción
 * corta que bloquea al deportista, repite la revisión e inserta. Si algo falla después de subir, borra el archivo y
 * relanza la excepción original.
 *
 * <p><b>Aprobar y rechazar</b>: solo el administrador. Autoriza, busca el documento (404), valida el comentario (400) y
 * hace el {@code update ... where estado = 'PENDIENTE'} con la relectura en una transacción corta; si ya no estaba
 * PENDIENTE, 409. Esos métodos no llevan {@code @Transactional}: al rechazar, el aviso {@code DOCUMENTO_RECHAZADO} a los
 * acudientes se envía <b>después</b> del commit, como en {@code HorarioSemanaServiceImpl}, y un aviso fallido nunca
 * deshace el rechazo. Aprobar no avisa.
 *
 * <p><b>Registros:</b> nunca correos, nombres, comentarios, rutas ni URL firmadas; ante una falla solo el evento, el
 * tipo de excepción y conteos.
 */
@Service
public class DocumentoServiceImpl implements IDocumentoService {

    static final String NO_EXISTE_DEPORTISTA = "No existe un deportista con ese id.";
    static final String NO_EXISTE_DOCUMENTO = "No existe un documento con ese id.";
    static final String YA_REVISADO = "estado: el documento ya fue revisado y no se puede revisar de nuevo";
    static final String YA_HAY_ACTIVO = "tipo: ya hay un documento PENDIENTE o APROBADO de ese tipo; "
            + "si está rechazado se puede volver a subir";

    private static final List<String> TIPOS_DE_CONTENIDO =
            List.of(ReglasDocumento.PDF, ReglasDocumento.JPEG, ReglasDocumento.PNG);
    private static final Logger log = LoggerFactory.getLogger(DocumentoServiceImpl.class);

    // Límites que valida el flujo de n8n para DOCUMENTO_RECHAZADO (ver el contrato en docs/PRESENTACION-TECNICA.md §9.1.1).
    // Duplicados a propósito de HorarioSemanaServiceImpl (CORREO_N8N, MAX_CORREO_N8N, MAX_TEXTO_N8N), donde son privados.
    static final int MAX_TEXTO_N8N = 120;
    static final int MAX_CORREO_N8N = 254;
    static final int MAX_COMENTARIO_N8N = ReglasDocumento.COMENTARIO_MAXIMO;
    static final int MAX_ENLACE_N8N = 2000;
    /** La misma expresión que usa n8n: los correos que no la cumplen los descarta o los rechaza con 400. */
    private static final Pattern CORREO_N8N = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Textos neutros cuando un nombre llega vacío (n8n rechaza con 400 un campo vacío). */
    static final String ACUDIENTE_SIN_NOMBRE = "Acudiente";
    static final String DEPORTISTA_SIN_NOMBRE = "tu deportista";

    private final IDocumentoDAO documentos;
    private final IDeportistaDAO deportistas;
    private final IAlmacenamientoArchivos almacenamiento;
    private final DocumentosProperties propiedades;
    private final Clock reloj;
    private final TransactionOperations transaccion;
    private final IDestinatariosDAO destinatarios;
    private final INotificador notificador;
    private final FrontendProperties frontend;

    /** @param reloj el de {@code RelojConfig} (hora de Colombia); las pruebas pasan uno fijo */
    public DocumentoServiceImpl(IDocumentoDAO documentos, IDeportistaDAO deportistas,
                                IAlmacenamientoArchivos almacenamiento, DocumentosProperties propiedades, Clock reloj,
                                TransactionOperations transaccion, IDestinatariosDAO destinatarios,
                                INotificador notificador, FrontendProperties frontend) {
        this.documentos = documentos;
        this.deportistas = deportistas;
        this.almacenamiento = almacenamiento;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.transaccion = transaccion;
        this.destinatarios = destinatarios;
        this.notificador = notificador;
        this.frontend = frontend;
    }

    // --- Subir --------------------------------------------------------------------------------------------------

    /** Sin {@code @Transactional}: Storage no puede estar dentro de una transacción. La transacción corta es programática. */
    @Override
    public Documento subir(Solicitante solicitante, UUID idDeportista, String tipo, String nombreOriginal,
                           byte[] contenido, String fechaVencimientoTexto) {
        autorizarSobreDeportista(solicitante, idDeportista);
        if (deportistas.buscarPorId(idDeportista, anioActual()).isEmpty()) {
            throw new RecursoNoEncontradoException(NO_EXISTE_DEPORTISTA);
        }

        // Validación: todos los errores juntos. Si falla solo el archivo, el mismo 400 que la foto (campos: ["archivo"])
        Map<String, String> errores = new LinkedHashMap<>();
        errores.putAll(ReglasDocumento.validarTipo(tipo));
        errores.putAll(ReglasDocumento.validarArchivo(contenido));
        errores.putAll(ReglasDocumento.validarFechaVencimiento(fechaVencimientoTexto, LocalDate.now(reloj)));
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        String tipoContenido = ReglasDocumento.tipoPorContenido(contenido).orElseThrow();
        LocalDate vencimiento = ReglasDocumento.parsearFecha(fechaVencimientoTexto).orElse(null);

        // Revisión rápida, sin bloqueo: evita subir un archivo que de todos modos se va a rechazar
        if (documentos.existeActivoDelTipo(idDeportista, tipo)) {
            throw conflicto();
        }

        // Storage fuera de toda transacción, con una ruta propia (nunca el nombre del cliente)
        String ruta = ReglasDocumento.ruta(idDeportista, UUID.randomUUID(), tipoContenido);
        almacenamiento.subir(propiedades.bucket(), ruta, contenido, tipoContenido, TIPOS_DE_CONTENIDO);

        try {
            return transaccion.execute(estado -> {
                if (!documentos.bloquearDeportista(idDeportista)) {
                    throw new RecursoNoEncontradoException(NO_EXISTE_DEPORTISTA);
                }
                if (documentos.existeActivoDelTipo(idDeportista, tipo)) {
                    throw conflicto();
                }
                return documentos.insertar(idDeportista, tipo, ruta, ReglasDocumento.limpiarNombre(nombreOriginal),
                        tipoContenido, contenido.length, vencimiento, solicitante.idUsuario());
            });
        } catch (RuntimeException e) {
            // Nada de lo que pasó después de subir debe dejar un archivo huérfano; se relanza la excepción original
            borrarSinFallar(ruta);
            throw e;
        }
    }

    // --- Listar -------------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public DocumentosDeportistaResponseDTO listar(Solicitante solicitante, UUID idDeportista) {
        autorizarSobreDeportista(solicitante, idDeportista);
        Deportista deportista = deportistas.buscarPorId(idDeportista, anioActual())
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE_DEPORTISTA));
        List<DocumentoResponseDTO> lista = documentos.listarPorDeportista(idDeportista).stream()
                .map(DocumentoResponseDTO::de).toList();
        return new DocumentosDeportistaResponseDTO(lista, deportista.getEstadoInscripcion(),
                deportistas.documentosObligatoriosFaltantes(idDeportista));
    }

    // --- Descargar ----------------------------------------------------------------------------------------------

    @Override
    public DescargaDocumentoResponseDTO descargar(Solicitante solicitante, UUID idDocumento) {
        exigirRolPermitido(solicitante);
        Documento documento = documentos.buscarPorId(idDocumento)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE_DOCUMENTO));
        // Un documento de otro acudiente responde igual que uno que no existe
        if (!solicitante.esAdministrador()
                && !documentos.esAcudienteDe(solicitante.idUsuario(), documento.idDeportista())) {
            throw new RecursoNoEncontradoException(NO_EXISTE_DOCUMENTO);
        }
        // La URL no se registra: quien la tenga puede descargar el archivo. Si el archivo ya no está en el bucket,
        // Storage lanza ArchivoNoEncontradoException y el manejo global responde 404.
        String url = almacenamiento.urlFirmada(propiedades.bucket(), documento.ruta(), propiedades.vigenciaUrlFirmada());
        return new DescargaDocumentoResponseDTO(url, propiedades.vigenciaUrlFirmada().toSeconds());
    }

    // --- Aprobar y rechazar -------------------------------------------------------------------------------------

    /**
     * Lo que dejó la revisión, leído dentro de la transacción: el documento releído, el estado de la inscripción del
     * deportista después del cambio y su nombre completo (para el aviso, así no hace falta otra consulta después).
     */
    private record Revisado(Documento documento, String estadoInscripcion, String nombreDeportista) {
    }

    /** Sin {@code @Transactional}: la transacción es corta y programática, y el aviso (paso 5) va después del commit. */
    @Override
    public RevisionDocumentoResponseDTO aprobar(Solicitante solicitante, UUID idDocumento) {
        exigirAdministrador(solicitante);
        Documento actual = buscarDocumento(idDocumento);
        Revisado revisado = revisar(actual, () -> documentos.aprobar(idDocumento, solicitante.idUsuario()));
        // Aprobar no envía ningún aviso
        return new RevisionDocumentoResponseDTO(DocumentoResponseDTO.de(revisado.documento()), revisado.estadoInscripcion());
    }

    /** Sin {@code @Transactional}, por lo mismo que {@link #aprobar}. */
    @Override
    public RechazoDocumentoResponseDTO rechazar(Solicitante solicitante, UUID idDocumento, String comentario) {
        exigirAdministrador(solicitante);
        Documento actual = buscarDocumento(idDocumento);
        String limpio = ReglasDocumento.limpiarComentario(comentario);
        Map<String, String> errores = ReglasDocumento.validarComentario(limpio);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        Revisado revisado = revisar(actual, () -> documentos.rechazar(idDocumento, solicitante.idUsuario(), limpio));
        // La transacción ya hizo commit: lo que pase con el aviso no deshace el rechazo ni cambia el código de la respuesta
        Boolean aviso = avisar(actual.idDeportista(), revisado, limpio);
        return new RechazoDocumentoResponseDTO(DocumentoResponseDTO.de(revisado.documento()),
                revisado.estadoInscripcion(), aviso);
    }

    // --- Aviso a los acudientes (DOCUMENTO_RECHAZADO) -----------------------------------------------------------

    /**
     * Avisa a cada acudiente del deportista con un correo válido, después del commit: un {@code notificar} por acudiente
     * (el evento lleva un solo {@code correo}). Si una llamada falla no se sigue con las demás (un n8n caído haría esperar
     * el tiempo de espera por cada una) y se devuelve {@code false}.
     *
     * <p>Registros: solo el evento, el tipo de excepción y conteos; nunca correos, nombres, comentarios ni el enlace.
     *
     * @return {@code true} si n8n aceptó todas las llamadas; {@code false} si algo falló (la lectura de destinatarios, la
     *         configuración de {@code FRONTEND_URL} o cualquier llamada); {@code null} si no había a quién avisar (no se
     *         llama a n8n)
     */
    private Boolean avisar(UUID idDeportista, Revisado revisado, String comentario) {
        List<Destinatario> destino;
        try {
            destino = destinatarios.acudientesDeDeportista(idDeportista).stream()
                    .filter(d -> d.correo() != null && d.correo().length() <= MAX_CORREO_N8N
                            && CORREO_N8N.matcher(d.correo()).matches())
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Aviso {} no enviado: no se pudieron leer los destinatarios ({})", INotificador.DOCUMENTO_RECHAZADO,
                    e.getClass().getSimpleName());
            return false;
        }
        if (destino.isEmpty()) {
            return null;
        }

        String enlace;
        String documento;
        try {
            enlace = frontend.enlaceDocumentos();
            if (enlace.length() > MAX_ENLACE_N8N) {
                // Un enlace cortado llevaría a una página que no existe: es un error de configuración, no se recorta
                throw new IllegalStateException("El enlace de documentos pasa del máximo de n8n");
            }
            documento = recortar(ReglasDocumento.etiqueta(revisado.documento().tipo()), MAX_TEXTO_N8N);
        } catch (RuntimeException e) {
            log.warn("Aviso {} no enviado: no se pudieron armar los datos ({}; {} destinatarios)",
                    INotificador.DOCUMENTO_RECHAZADO, e.getClass().getSimpleName(), destino.size());
            return false;
        }
        String deportista = lineaONeutro(revisado.nombreDeportista(), DEPORTISTA_SIN_NOMBRE);
        String motivo = recortar(comentario, MAX_COMENTARIO_N8N);

        for (int i = 0; i < destino.size(); i++) {
            Destinatario d = destino.get(i);
            try {
                notificador.notificar(INotificador.DOCUMENTO_RECHAZADO, Map.of(
                        "correo", d.correo(),
                        "nombreAcudiente", lineaONeutro(d.nombre(), ACUDIENTE_SIN_NOMBRE),
                        "nombreDeportista", deportista,
                        "documento", documento,
                        "comentario", motivo,
                        "enlaceDocumentos", enlace));
            } catch (RuntimeException e) {
                log.warn("Aviso {} no enviado: {} ({} enviados antes, {} destinatarios en total)",
                        INotificador.DOCUMENTO_RECHAZADO, e.getClass().getSimpleName(), i, destino.size());
                return false;
            }
        }
        return true;
    }

    /**
     * Un texto de una sola línea para n8n: los saltos de línea y demás caracteres de control pasan a un espacio, se
     * quitan los espacios repetidos y de los extremos y se recorta al máximo. Vacío o nulo, el texto neutro.
     */
    private static String lineaONeutro(String texto, String neutro) {
        if (texto == null) return neutro;
        String linea = recortar(texto.replaceAll("[\\p{Cntrl}\\s]+", " ").strip(), MAX_TEXTO_N8N);
        return linea.isEmpty() ? neutro : linea;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo).strip();
    }

    /**
     * El update condicional y la relectura, en una transacción corta. Si el update no tocó ninguna fila es porque el
     * documento ya no está PENDIENTE (lo revisó otro administrador, o ya estaba revisado): 409 y nada cambia.
     */
    private Revisado revisar(Documento actual, BooleanSupplier actualizar) {
        return transaccion.execute(estado -> {
            if (!actualizar.getAsBoolean()) {
                throw new ConflictoException(YA_REVISADO, List.of("estado"));
            }
            Documento revisado = documentos.buscarPorId(actual.id())
                    .orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE_DOCUMENTO));
            Deportista deportista = deportistas.buscarPorId(actual.idDeportista(), anioActual())
                    .orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE_DEPORTISTA));
            String nombre = ((deportista.getNombre() == null ? "" : deportista.getNombre()) + " "
                    + (deportista.getApellido() == null ? "" : deportista.getApellido())).strip();
            return new Revisado(revisado, deportista.getEstadoInscripcion(), nombre);
        });
    }

    private Documento buscarDocumento(UUID idDocumento) {
        return documentos.buscarPorId(idDocumento).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE_DOCUMENTO));
    }

    // --- Autorización -------------------------------------------------------------------------------------------

    /** Revisar documentos es solo del ADMINISTRADOR; falla cerrado ante cualquier otro rol o un solicitante sin identificar. */
    private static void exigirAdministrador(Solicitante solicitante) {
        if (solicitante == null || solicitante.idUsuario() == null || !solicitante.esAdministrador()) {
            throw new SinPermisoException();
        }
    }

    /** Falla cerrado: solo ADMINISTRADOR y ACUDIENTE con un usuario identificado. */
    private static void exigirRolPermitido(Solicitante solicitante) {
        if (solicitante == null || solicitante.idUsuario() == null
                || !(solicitante.esAdministrador() || solicitante.esAcudiente())) {
            throw new SinPermisoException();
        }
    }

    /** El administrador puede con cualquier deportista; el acudiente solo con los suyos (si no, 404 como si no existiera). */
    private void autorizarSobreDeportista(Solicitante solicitante, UUID idDeportista) {
        exigirRolPermitido(solicitante);
        if (solicitante.esAcudiente() && !documentos.esAcudienteDe(solicitante.idUsuario(), idDeportista)) {
            throw new RecursoNoEncontradoException(NO_EXISTE_DEPORTISTA);
        }
    }

    // --- Ayudas -------------------------------------------------------------------------------------------------

    private static ConflictoException conflicto() {
        return new ConflictoException(YA_HAY_ACTIVO, List.of("tipo"));
    }

    /** Borra el archivo recién subido; si no puede, registra solo el tipo de excepción (ni ruta ni nombres). */
    private void borrarSinFallar(String ruta) {
        try {
            almacenamiento.eliminar(propiedades.bucket(), ruta);
        } catch (ArchivoNoEncontradoException e) {
            // Ya no estaba.
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar del bucket {} un documento recién subido: {}", propiedades.bucket(),
                    e.getClass().getSimpleName());
        }
    }

    private int anioActual() {
        return LocalDate.now(reloj).getYear();
    }
}
