package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.AccionSemanaResponseDTO;
import co.edu.futdata.api.dto.CancelacionRequestDTO;
import co.edu.futdata.api.dto.ExtraRequestDTO;
import co.edu.futdata.api.dto.FranjaHorarioMapper;
import co.edu.futdata.api.dto.FranjaHorarioResponseDTO;
import co.edu.futdata.api.dto.HorarioSemanaResponseDTO;
import co.edu.futdata.api.dto.SesionSemanaResponseDTO;
import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.domain.rules.ReglasEntrenador;
import co.edu.futdata.domain.rules.ReglasExcepcion;
import co.edu.futdata.domain.rules.ReglasFranja;
import co.edu.futdata.domain.rules.ReglasSemana;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.persistence.dao.CruceDeHorarioException;
import co.edu.futdata.persistence.dao.Destinatario;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IDestinatariosDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IExcepcionHorarioDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IHorarioSemanaService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.SemanaNoPermitidaException;
import co.edu.futdata.services.facade.SinPermisoHorarioException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class HorarioSemanaServiceImpl implements IHorarioSemanaService {

    static final String FECHA_INVALIDA = "debe ser una fecha válida con el formato AAAA-MM-DD";
    static final String FRANJA_NO_EXISTE = "No existe una franja con ese id.";
    static final String CATEGORIA_NO_EXISTE = "No existe una categoría con ese id.";
    static final String CANCELACION_NO_EXISTE = "No existe una cancelación con ese id.";
    static final String EXTRA_NO_EXISTE = "No existe un extra con ese id.";

    /** Límites que valida el flujo de n8n para CAMBIO_ENTRENAMIENTO (ver el contrato del webhook). */
    static final int MAX_TEXTO_N8N = 120;
    static final int MAX_DESTINATARIOS_POR_LLAMADA = 200;
    static final int MAX_CORREO_N8N = 254;
    /** La misma expresión que usa n8n: los correos que no la cumplen los descarta sin avisar. */
    private static final Pattern CORREO_N8N = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final String POR_TURNOS = "Por turnos";
    static final String CREADO = "CREADO";
    static final String CANCELADO_AVISO = "CANCELADO";

    private static final Logger log = LoggerFactory.getLogger(HorarioSemanaServiceImpl.class);

    private static final String TIPO_FRANJA = "FRANJA";
    private static final String TIPO_EXTRA = "EXTRA";
    private static final String ACTIVO = "ACTIVO";
    private static final String CANCELADO = "CANCELADO";

    private static final Comparator<SesionSemanaResponseDTO> ORDEN = Comparator
            .comparing(SesionSemanaResponseDTO::fecha)
            .thenComparing(SesionSemanaResponseDTO::horaInicio)
            .thenComparingInt(s -> ReglasEntrenador.CATEGORIAS.indexOf(s.categoria()))
            .thenComparing(SesionSemanaResponseDTO::grupo, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final IFranjaHorarioDAO franjas;
    private final IExcepcionHorarioDAO excepciones;
    private final IEntrenadorDAO entrenadores;
    private final ICategoriaDAO categorias;
    private final Clock reloj;
    private final TransactionOperations transaccion;
    private final IDestinatariosDAO destinatarios;
    private final INotificador notificador;

    public HorarioSemanaServiceImpl(IFranjaHorarioDAO franjas, IExcepcionHorarioDAO excepciones,
                                    IEntrenadorDAO entrenadores, ICategoriaDAO categorias, Clock reloj,
                                    TransactionOperations transaccion, IDestinatariosDAO destinatarios,
                                    INotificador notificador) {
        this.franjas = franjas;
        this.excepciones = excepciones;
        this.entrenadores = entrenadores;
        this.categorias = categorias;
        this.reloj = reloj;
        this.transaccion = transaccion;
        this.destinatarios = destinatarios;
        this.notificador = notificador;
    }

    // --- Leer la semana -------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public HorarioSemanaResponseDTO consultar(String fecha, Solicitante solicitante) {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate enCurso = ReglasSemana.lunesDeLaSemana(hoy);
        LocalDate lunes = enCurso;
        if (fecha != null && !fecha.isBlank()) {
            LocalDate pedida = ReglasSemana.fecha(fecha.strip())
                    .orElseThrow(() -> DatosInvalidosException.de("fecha", FECHA_INVALIDA));
            lunes = ReglasSemana.lunesDeLaSemana(pedida);
            if (solicitante.esAcudiente() && !lunes.equals(enCurso)) {
                throw new SemanaNoPermitidaException();
            }
        }
        LocalDate semana = lunes;
        LocalDate sabado = ReglasSemana.sabado(lunes);

        List<ExcepcionHorario> deLaSemana = excepciones.listar(lunes, sabado);
        // Una cancelación solo cuenta si cae en la fecha que la franja tiene en esta semana: si el 4A le cambió el día a
        // la franja, la cancelación ya no coincide y simplemente no se aplica.
        Map<String, ExcepcionHorario> canceladas = deLaSemana.stream()
                .filter(ExcepcionHorario::esCancelacion)
                .collect(Collectors.toMap(x -> clave(x.idFranja(), x.fecha()), x -> x, (a, b) -> a));
        Permisos permisos = permisosDe(solicitante);

        List<SesionSemanaResponseDTO> sesiones = new ArrayList<>(franjas.listar(null, null, null).stream()
                .map(FranjaHorarioMapper::toDTO)
                .map(f -> {
                    LocalDate dia = ReglasSemana.fechaDeLaFranja(semana, f.dia());
                    ExcepcionHorario cancelacion = canceladas.get(clave(f.idFranja(), dia));
                    boolean puede = permisos.puedeFranja(f.idCategoria(), f.idEntrenador()) && !esPasada(dia, hoy);
                    return aSesion(f, dia, cancelacion, puede);
                })
                .toList());
        deLaSemana.stream().filter(ExcepcionHorario::esExtra)
                .map(x -> sesionDeExtra(x, permisos.puedeExtra(x.idCategoria()) && !esPasada(x.fecha(), hoy)))
                .forEach(sesiones::add);
        sesiones.sort(ORDEN);
        return new HorarioSemanaResponseDTO(lunes.toString(), sabado.toString(), List.copyOf(sesiones));
    }

    // --- Cancelar y deshacer --------------------------------------------------------------------------------

    @Override
    public AccionSemanaResponseDTO cancelar(CancelacionRequestDTO datos, Solicitante solicitante) {
        Cancelacion entrada = validar(datos);
        // Todo en una transacción que se confirma al salir; el aviso a n8n va DESPUÉS, fuera de ella.
        Hecho hecho = transaccion.execute(estado -> {
            FranjaHorario franja = franjas.buscarPorId(entrada.idFranja())
                    .orElseThrow(() -> new RecursoNoEncontradoException(FRANJA_NO_EXISTE));
            LocalDate hoy = LocalDate.now(reloj);
            if (esPasada(entrada.fecha(), hoy)) {
                throw DatosInvalidosException.de("fecha", ReglasExcepcion.FECHA_PASADA);
            }
            if (!ReglasExcepcion.coincideConElDia(entrada.fecha(), franja.getDia())) {
                throw DatosInvalidosException.de("fecha", ReglasExcepcion.DIA_NO_COINCIDE);
            }
            // El administrador se salta la consulta de propiedad por su rol; los demás roles ni siquiera llegan al controlador
            if (!solicitante.esAdministrador()
                    && !(solicitante.esEntrenador() && excepciones.esDuenoDeFranja(solicitante.idUsuario(), franja.getId()))) {
                throw new SinPermisoHorarioException();
            }
            UUID id = excepciones.crearCancelacion(franja.getId(), entrada.fecha(), entrada.motivo(), solicitante.idUsuario());
            ExcepcionHorario creada = excepciones.buscarPorId(id).orElseThrow();
            return new Hecho(aSesion(creada, creada.fecha(), creada, !esPasada(creada.fecha(), hoy)),
                    aviso(CANCELADO_AVISO, creada, creada.motivo()));
        });
        return respuesta(hecho);
    }

    @Override
    public AccionSemanaResponseDTO deshacerCancelacion(UUID idExcepcion, Solicitante solicitante) {
        Hecho hecho = transaccion.execute(estado -> {
            ExcepcionHorario cancelacion = excepciones.buscarPorId(idExcepcion)
                    .filter(ExcepcionHorario::esCancelacion)
                    .orElseThrow(() -> new RecursoNoEncontradoException(CANCELACION_NO_EXISTE));
            LocalDate hoy = LocalDate.now(reloj);
            if (esPasada(cancelacion.fecha(), hoy)) {
                throw DatosInvalidosException.de("fecha", ReglasExcepcion.FECHA_PASADA);
            }
            if (!solicitante.esAdministrador()
                    && !(solicitante.esEntrenador() && excepciones.esDuenoDeExcepcion(solicitante.idUsuario(), idExcepcion))) {
                throw new SinPermisoHorarioException();
            }
            excepciones.eliminar(idExcepcion);
            // La franja vuelve a darse: para los acudientes es como un entrenamiento nuevo (CREADO), sin motivo
            return new Hecho(aSesion(cancelacion, cancelacion.fecha(), null, !esPasada(cancelacion.fecha(), hoy)),
                    aviso(CREADO, cancelacion, null));
        });
        return respuesta(hecho);
    }

    // --- Extras ---------------------------------------------------------------------------------------------

    @Override
    public AccionSemanaResponseDTO crearExtra(ExtraRequestDTO datos, Solicitante solicitante) {
        // Todo en UNA transacción: las lecturas, el bloqueo de la fila del entrenador, la comprobación del cruce con las
        // franjas y el insert. El aviso a n8n va DESPUÉS de este bloque, ya confirmada.
        Hecho hecho = transaccion.execute(estado -> {
            Extra extra = validar(datos);
            LocalDate hoy = LocalDate.now(reloj);
            if (esPasada(extra.fecha(), hoy)) {
                throw DatosInvalidosException.de("fecha", ReglasExcepcion.FECHA_PASADA);
            }
            if (!ReglasExcepcion.esDiaDeEntrenamiento(extra.fecha())) {
                throw DatosInvalidosException.de("fecha", ReglasExcepcion.FECHA_FUERA_DE_SEMANA);
            }
            if (!solicitante.esAdministrador()
                    && !(solicitante.esEntrenador() && excepciones.esDuenoDeCategoria(solicitante.idUsuario(), extra.idCategoria()))) {
                throw new SinPermisoHorarioException();
            }
            if (extra.idEntrenador() != null) {
                Optional<Entrenador> asignado = entrenadores.buscarPorId(extra.idEntrenador());
                if (asignado.isEmpty()) {
                    throw DatosInvalidosException.de("idEntrenador", FranjaHorarioServiceImpl.ENTRENADOR_NO_EXISTE);
                }
                if (!asignado.get().isActivo()) {
                    throw DatosInvalidosException.de("idEntrenador", FranjaHorarioServiceImpl.ENTRENADOR_INACTIVO);
                }
                // Bloquea la fila del entrenador y mira sus franjas no canceladas ese día; el cruce con otros extras lo decide la base
                if (excepciones.hayFranjaQueSeCruza(extra.idEntrenador(), extra.fecha(), extra.horaInicio(), extra.horaFin())) {
                    throw cruce();
                }
            }
            UUID id;
            try {
                id = excepciones.crearExtra(new ExcepcionHorario(null, ExcepcionHorario.EXTRA, extra.fecha(), null,
                        extra.idCategoria(), extra.categoria(), null, extra.horaInicio(), extra.horaFin(), extra.sede(),
                        extra.idEntrenador(), null, null, extra.grupo(), extra.motivo(), solicitante.idUsuario()));
            } catch (CruceDeHorarioException e) {
                throw cruce();
            }
            ExcepcionHorario creado = excepciones.buscarPorId(id).orElseThrow();
            return new Hecho(sesionDeExtra(creado, !esPasada(creado.fecha(), hoy)), aviso(CREADO, creado, null));
        });
        return respuesta(hecho);
    }

    @Override
    public AccionSemanaResponseDTO eliminarExtra(UUID idExcepcion, Solicitante solicitante) {
        Hecho hecho = transaccion.execute(estado -> {
            ExcepcionHorario extra = excepciones.buscarPorId(idExcepcion)
                    .filter(ExcepcionHorario::esExtra)
                    .orElseThrow(() -> new RecursoNoEncontradoException(EXTRA_NO_EXISTE));
            if (esPasada(extra.fecha(), LocalDate.now(reloj))) {
                throw DatosInvalidosException.de("fecha", ReglasExcepcion.FECHA_PASADA);
            }
            // Para un extra solo cuenta la categoría (la consulta de propiedad no mira al entrenador asignado)
            if (!solicitante.esAdministrador()
                    && !(solicitante.esEntrenador() && excepciones.esDuenoDeExcepcion(solicitante.idUsuario(), idExcepcion))) {
                throw new SinPermisoHorarioException();
            }
            // Los datos del aviso se arman ANTES de borrar: después el extra ya no se puede leer
            Aviso aviso = aviso(CANCELADO_AVISO, extra, extra.motivo());
            excepciones.eliminar(idExcepcion);
            return new Hecho(null, aviso);
        });
        return respuesta(hecho);
    }

    // --- Avisos a los acudientes (CAMBIO_ENTRENAMIENTO) -----------------------------------------------------

    /** Lo que dejó una acción confirmada: la sesión que se responde y los datos del aviso (ya leídos, sin transacción). */
    private record Hecho(SesionSemanaResponseDTO sesion, Aviso aviso) {
    }

    /** Los datos de {@code entrenamiento} del evento, más el tipo y el motivo. No lleva horaFin: n8n no la usa. */
    private record Aviso(String tipoCambio, UUID idCategoria, String categoria, LocalDate fecha, String horaInicio,
                         String lugar, String entrenador, String motivo) {
    }

    private AccionSemanaResponseDTO respuesta(Hecho hecho) {
        // La transacción ya hizo commit: lo que pase con el aviso no la deshace ni cambia el código de la respuesta
        return new AccionSemanaResponseDTO(hecho.sesion(), avisar(hecho.aviso()));
    }

    /**
     * El aviso de una sesión ({@code d} trae categoría, fecha, hora, sede y entrenador; en una cancelación, los de su
     * franja). El motivo solo se manda si hay y el evento lo usa: n8n lo ignora en CREADO.
     */
    private static Aviso aviso(String tipoCambio, ExcepcionHorario d, String motivo) {
        String categoria = d.grupo() == null ? d.categoria() : d.categoria() + " (" + d.grupo() + ")";
        return new Aviso(tipoCambio, d.idCategoria(), recortar(categoria), d.fecha(), ReglasFranja.formatear(d.horaInicio()),
                recortar(d.sede()), recortar(d.entrenador() == null ? POR_TURNOS : d.entrenador()),
                CANCELADO_AVISO.equals(tipoCambio) ? motivo : null);
    }

    private static String recortar(String texto) {
        return texto.length() <= MAX_TEXTO_N8N ? texto : texto.substring(0, MAX_TEXTO_N8N).strip();
    }

    /**
     * Avisa a los acudientes de la categoría, después del commit.
     *
     * @return {@code true} si n8n aceptó todas las llamadas; {@code false} si alguna falló (lanzó cualquier
     *         {@link RuntimeException}); {@code null} si no había a quién avisar (no se llama a n8n)
     */
    private Boolean avisar(Aviso aviso) {
        List<Destinatario> destino;
        try {
            destino = destinatarios.acudientesDeCategoria(aviso.idCategoria(), LocalDate.now(reloj).getYear()).stream()
                    .filter(d -> d.correo() != null && d.correo().length() <= MAX_CORREO_N8N
                            && CORREO_N8N.matcher(d.correo()).matches())
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Aviso {} no enviado: no se pudieron leer los destinatarios ({})", INotificador.CAMBIO_ENTRENAMIENTO,
                    e.getClass().getSimpleName());
            return false;
        }
        if (destino.isEmpty()) {
            return null;
        }
        int bloques = (destino.size() + MAX_DESTINATARIOS_POR_LLAMADA - 1) / MAX_DESTINATARIOS_POR_LLAMADA;
        for (int i = 0; i < bloques; i++) {
            List<Destinatario> bloque = destino.subList(i * MAX_DESTINATARIOS_POR_LLAMADA,
                    Math.min(destino.size(), (i + 1) * MAX_DESTINATARIOS_POR_LLAMADA));
            try {
                notificador.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datosDelAviso(aviso, bloque));
            } catch (RuntimeException e) {
                // Sin correos ni nombres: solo el evento, el tipo de excepción y cuántos destinatarios. No se reintenta
                // ni se sigue con los bloques que faltan: si n8n falla, el siguiente bloque esperaría otro timeout.
                log.warn("Aviso {} no enviado: {} ({} destinatarios en el bloque {} de {}; {} en total)",
                        INotificador.CAMBIO_ENTRENAMIENTO, e.getClass().getSimpleName(), bloque.size(), i + 1, bloques,
                        destino.size());
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> datosDelAviso(Aviso aviso, List<Destinatario> bloque) {
        Map<String, Object> entrenamiento = new LinkedHashMap<>();
        entrenamiento.put("categoria", aviso.categoria());
        entrenamiento.put("fecha", aviso.fecha().toString());
        entrenamiento.put("horaInicio", aviso.horaInicio());
        entrenamiento.put("lugar", aviso.lugar());
        entrenamiento.put("entrenador", aviso.entrenador());
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipoCambio", aviso.tipoCambio());
        datos.put("entrenamiento", entrenamiento);
        if (aviso.motivo() != null) {
            datos.put("motivo", aviso.motivo());
        }
        datos.put("destinatarios", bloque.stream().map(d -> {
            Map<String, Object> destinatario = new LinkedHashMap<>();
            destinatario.put("correo", d.correo());
            if (d.nombre() != null && !d.nombre().isBlank()) {
                destinatario.put("nombre", recortar(d.nombre()));
            }
            return destinatario;
        }).toList());
        return datos;
    }

    private static ConflictoException cruce() {
        return new ConflictoException(ReglasExcepcion.CRUCE, List.of("idEntrenador"));
    }

    // --- Validación de los cuerpos --------------------------------------------------------------------------

    private record Cancelacion(LocalDate fecha, UUID idFranja, String motivo) {
    }

    /** Valida todo el cuerpo junto: un solo 400 con todos los campos que fallan. */
    private static Cancelacion validar(CancelacionRequestDTO datos) {
        if (datos == null) {
            throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        }
        Map<String, String> errores = new LinkedHashMap<>();
        LocalDate fecha = fecha(datos.fecha(), errores);
        UUID idFranja = uuid(datos.idFranja(), "idFranja", true, errores);
        String motivo = motivo(datos.motivo(), errores);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return new Cancelacion(fecha, idFranja, motivo);
    }

    private record Extra(LocalDate fecha, UUID idCategoria, String categoria, LocalTime horaInicio, LocalTime horaFin,
                         String sede, UUID idEntrenador, String grupo, String motivo) {
    }

    /**
     * Valida todo el cuerpo del extra junto (un solo 400) con las reglas de {@link ReglasFranja} para horas, sede y grupo, y
     * después comprueba que la categoría exista (404). El grupo solo se revisa contra la categoría si esta existe.
     */
    private Extra validar(ExtraRequestDTO datos) {
        if (datos == null) {
            throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        }
        Map<String, String> errores = new LinkedHashMap<>();
        LocalDate fecha = fecha(datos.fecha(), errores);
        UUID idCategoria = uuid(datos.idCategoria(), "idCategoria", true, errores);
        UUID idEntrenador = uuid(datos.idEntrenador(), "idEntrenador", false, errores);
        String motivo = motivo(datos.motivo(), errores);

        Optional<String> categoria = idCategoria == null ? Optional.empty() : franjas.nombreDeCategoria(idCategoria);
        // La fecha pone el día de la semana (que ReglasFranja pide); si la fecha no es válida ya tiene su propio error
        ReglasFranja.Entrada e = ReglasFranja.limpiar(new ReglasFranja.Entrada(idCategoria,
                fecha == null ? Integer.valueOf(ReglasFranja.DIA_MINIMO) : Integer.valueOf(fecha.getDayOfWeek().getValue()),
                datos.horaInicio(), datos.horaFin(), datos.sede(), idEntrenador, datos.grupo()));
        Map<String, String> reglas = new LinkedHashMap<>(ReglasFranja.validar(e, categoria.orElse(null)));
        reglas.remove("dia");
        reglas.forEach(errores::putIfAbsent);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        if (categoria.isEmpty()) {
            throw new RecursoNoEncontradoException(CATEGORIA_NO_EXISTE);
        }
        return new Extra(fecha, idCategoria, categoria.get(), ReglasFranja.hora(e.horaInicio()).orElseThrow(),
                ReglasFranja.hora(e.horaFin()).orElseThrow(), e.sede(), idEntrenador, e.grupo(), motivo);
    }

    private static LocalDate fecha(String texto, Map<String, String> errores) {
        if (texto == null || texto.isBlank()) {
            errores.put("fecha", "es obligatoria");
            return null;
        }
        LocalDate fecha = ReglasSemana.fecha(texto.strip()).orElse(null);
        if (fecha == null) {
            errores.put("fecha", FECHA_INVALIDA);
        }
        return fecha;
    }

    private static UUID uuid(String texto, String campo, boolean obligatorio, Map<String, String> errores) {
        if (texto == null || texto.isBlank()) {
            if (obligatorio) {
                errores.put(campo, "es obligatorio");
            }
            return null;
        }
        try {
            return UUID.fromString(texto.strip());
        } catch (IllegalArgumentException e) {
            errores.put(campo, "debe ser un UUID válido");
            return null;
        }
    }

    private static String motivo(String texto, Map<String, String> errores) {
        String motivo = ReglasExcepcion.normalizarMotivo(texto);
        if (ReglasExcepcion.motivoDemasiadoLargo(motivo)) {
            errores.put("motivo", ReglasExcepcion.MOTIVO_LARGO);
        }
        return motivo;
    }

    // --- Permisos y armado de sesiones ----------------------------------------------------------------------

    /**
     * Qué puede gestionar quien consulta, leído UNA vez por petición: el administrador todo; el entrenador, las franjas de
     * sus categorías o que él dirige, y los extras de sus categorías; los demás roles nada.
     */
    private record Permisos(boolean todo, UUID idEntrenador, Set<UUID> idsCategorias) {

        static final Permisos NINGUNO = new Permisos(false, null, Set.of());

        boolean puedeFranja(UUID idCategoria, UUID idEntrenadorDeLaFranja) {
            return todo || idsCategorias.contains(idCategoria)
                    || (idEntrenador != null && idEntrenador.equals(idEntrenadorDeLaFranja));
        }

        /** Un extra es de la categoría: dirigirlo no da permiso. */
        boolean puedeExtra(UUID idCategoria) {
            return todo || idsCategorias.contains(idCategoria);
        }
    }

    private Permisos permisosDe(Solicitante solicitante) {
        if (solicitante.esAdministrador()) {
            return new Permisos(true, null, Set.of());
        }
        if (!solicitante.esEntrenador()) {
            return Permisos.NINGUNO;
        }
        return entrenadores.buscarPorIdUsuario(solicitante.idUsuario())
                .map(e -> new Permisos(false, e.getId(), categorias.listarPorEntrenador(e.getId()).stream()
                        .map(Categoria::getId).collect(Collectors.toSet())))
                .orElse(Permisos.NINGUNO);
    }

    private static boolean esPasada(LocalDate fecha, LocalDate hoy) {
        return ReglasExcepcion.esFechaPasada(fecha, hoy);
    }

    private static String clave(UUID idFranja, LocalDate fecha) {
        return idFranja + "|" + fecha;
    }

    /** Una franja en su fecha; con {@code cancelacion} sale CANCELADO con su {@code idExcepcion} y su motivo. */
    private static SesionSemanaResponseDTO aSesion(FranjaHorarioResponseDTO f, LocalDate fecha, ExcepcionHorario cancelacion,
                                                   boolean puedeGestionar) {
        return new SesionSemanaResponseDTO(TIPO_FRANJA, cancelacion == null ? ACTIVO : CANCELADO, f.idFranja(),
                cancelacion == null ? null : cancelacion.id(), fecha.toString(), f.dia(), f.horaInicio(), f.horaFin(),
                f.idCategoria(), f.categoria(), f.sede(), f.idEntrenador(), f.entrenador(), f.entrenadorActivo(),
                f.grupo(), cancelacion == null ? null : cancelacion.motivo(), puedeGestionar);
    }

    /**
     * La misma sesión a partir de una cancelación leída con los datos de su franja. {@code estadoDe} es la cancelación
     * vigente ({@code null} = la franja quedó ACTIVA, por ejemplo después de deshacerla).
     */
    private static SesionSemanaResponseDTO aSesion(ExcepcionHorario franja, LocalDate fecha, ExcepcionHorario estadoDe,
                                                   boolean puedeGestionar) {
        return new SesionSemanaResponseDTO(TIPO_FRANJA, estadoDe == null ? ACTIVO : CANCELADO, franja.idFranja(),
                estadoDe == null ? null : estadoDe.id(), fecha.toString(), franja.diaDeLaFranja(),
                ReglasFranja.formatear(franja.horaInicio()), ReglasFranja.formatear(franja.horaFin()), franja.idCategoria(),
                franja.categoria(), franja.sede(), franja.idEntrenador(), franja.entrenador(), franja.entrenadorActivo(),
                franja.grupo(), estadoDe == null ? null : estadoDe.motivo(), puedeGestionar);
    }

    /** Un extra: sin franja, con su propio {@code idExcepcion}, y el día de la semana sale de su fecha (1..6). */
    private static SesionSemanaResponseDTO sesionDeExtra(ExcepcionHorario x, boolean puedeGestionar) {
        return new SesionSemanaResponseDTO(TIPO_EXTRA, ACTIVO, null, x.id(), x.fecha().toString(),
                x.fecha().getDayOfWeek().getValue(), ReglasFranja.formatear(x.horaInicio()), ReglasFranja.formatear(x.horaFin()),
                x.idCategoria(), x.categoria(), x.sede(), x.idEntrenador(), x.entrenador(), x.entrenadorActivo(), x.grupo(),
                x.motivo(), puedeGestionar);
    }
}
