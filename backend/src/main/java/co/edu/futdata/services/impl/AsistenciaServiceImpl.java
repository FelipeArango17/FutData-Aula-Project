package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.AsistenciaRequestDTO;
import co.edu.futdata.api.dto.DatosSesionAsistenciaDTO;
import co.edu.futdata.api.dto.DeportistaAsistenciaDTO;
import co.edu.futdata.api.dto.RegistroAsistenciaRequestDTO;
import co.edu.futdata.api.dto.SesionAsistenciaResponseDTO;
import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.ExcepcionHorario;
import co.edu.futdata.domain.entities.FranjaHorario;
import co.edu.futdata.domain.rules.ReglasAsistencia;
import co.edu.futdata.domain.rules.ReglasExcepcion;
import co.edu.futdata.domain.rules.ReglasFranja;
import co.edu.futdata.domain.rules.ReglasSemana;
import co.edu.futdata.persistence.dao.DeportistaDeSesion;
import co.edu.futdata.persistence.dao.IAsistenciaDAO;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.IExcepcionHorarioDAO;
import co.edu.futdata.persistence.dao.IFranjaHorarioDAO;
import co.edu.futdata.persistence.dao.RegistroAsistencia;
import co.edu.futdata.persistence.dao.SolapeDeEntrenamientoException;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IAsistenciaService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.services.facade.SinPermisoAsistenciaException;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class AsistenciaServiceImpl implements IAsistenciaService {

    private final IAsistenciaDAO asistencias;
    private final IFranjaHorarioDAO franjas;
    private final IExcepcionHorarioDAO excepciones;
    private final IEntrenadorDAO entrenadores;
    private final ICategoriaDAO categorias;
    private final Clock reloj;
    private final TransactionOperations transaccion;

    public AsistenciaServiceImpl(IAsistenciaDAO asistencias, IFranjaHorarioDAO franjas, IExcepcionHorarioDAO excepciones,
                                 IEntrenadorDAO entrenadores, ICategoriaDAO categorias, Clock reloj,
                                 TransactionOperations transaccion) {
        this.asistencias = asistencias;
        this.franjas = franjas;
        this.excepciones = excepciones;
        this.entrenadores = entrenadores;
        this.categorias = categorias;
        this.reloj = reloj;
        this.transaccion = transaccion;
    }

    // --- Consultar ------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SesionAsistenciaResponseDTO consultar(String fecha, String idFranja, String idExcepcion, Solicitante solicitante) {
        verificarRol(solicitante);
        Map<String, String> errores = new LinkedHashMap<>();
        Referencia referencia = referencia(fecha, idFranja, idExcepcion, errores);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        Sesion sesion = autorizada(referencia, solicitante);
        verificarFechaYEstado(sesion, LocalDate.now(reloj));
        // Solo lectura: si la sesión aún no tiene entrenamiento, la lista sale sin marcas (no se crea nada)
        UUID entrenamiento = asistencias.buscarEntrenamiento(sesion.idCategoria(), sesion.fecha(), sesion.horaInicio(),
                sesion.sede()).orElse(null);
        return respuesta(sesion, entrenamiento);
    }

    // --- Registrar ------------------------------------------------------------------------------------------

    @Override
    public SesionAsistenciaResponseDTO registrar(AsistenciaRequestDTO datos, Solicitante solicitante) {
        verificarRol(solicitante);
        // Todo en UNA transacción: la comprobación de la lista, el entrenamiento y el guardado
        return transaccion.execute(estado -> {
            Map<String, String> errores = new LinkedHashMap<>();
            Referencia referencia = referencia(datos == null ? null : datos.fecha(), datos == null ? null : datos.idFranja(),
                    datos == null ? null : datos.idExcepcion(), errores);
            List<RegistroAsistencia> registros = registros(datos == null ? null : datos.registros(), errores);
            if (!errores.isEmpty()) {
                throw new DatosInvalidosException(errores);
            }
            Sesion sesion = autorizada(referencia, solicitante);
            verificarFechaYEstado(sesion, LocalDate.now(reloj));

            Set<UUID> enLista = lista(sesion, null).stream().map(DeportistaDeSesion::idDeportista)
                    .collect(Collectors.toSet());
            if (!enLista.containsAll(registros.stream().map(RegistroAsistencia::idDeportista).toList())) {
                throw DatosInvalidosException.de("registros", ReglasAsistencia.DEPORTISTA_FUERA_DE_LISTA);
            }
            UUID entrenamiento;
            try {
                entrenamiento = asistencias.buscarOCrearEntrenamiento(sesion.idCategoria(), sesion.fecha(),
                        sesion.horaInicio(), sesion.horaFin(), sesion.sede(), sesion.idEntrenador());
            } catch (SolapeDeEntrenamientoException e) {
                throw new ConflictoException(ReglasAsistencia.SOLAPE, List.of());
            }
            asistencias.guardar(entrenamiento, quienPasaLista(solicitante), registros);
            return respuesta(sesion, entrenamiento);
        });
    }

    // --- Validaciones ---------------------------------------------------------------------------------------

    /** Falla cerrado: solo ADMINISTRADOR y ENTRENADOR identificados; cualquier otro rol (o ninguno) es 403. */
    private static void verificarRol(Solicitante solicitante) {
        if (solicitante == null || solicitante.idUsuario() == null
                || !(solicitante.esAdministrador() || solicitante.esEntrenador())) {
            throw new SinPermisoException();
        }
    }

    /** La sesión pedida ya con sus campos válidos (UUID y fecha); la sesión en sí aún no se buscó. */
    private record Referencia(LocalDate fecha, UUID idFranja, UUID idExcepcion) {
    }

    private static Referencia referencia(String fecha, String idFranja, String idExcepcion, Map<String, String> errores) {
        LocalDate dia = null;
        if (fecha == null || fecha.isBlank()) {
            errores.put("fecha", "es obligatoria");
        } else {
            dia = ReglasSemana.fecha(fecha.strip()).orElse(null);
            if (dia == null) {
                errores.put("fecha", HorarioSemanaServiceImpl.FECHA_INVALIDA);
            }
        }
        boolean hayFranja = idFranja != null && !idFranja.isBlank();
        boolean hayExtra = idExcepcion != null && !idExcepcion.isBlank();
        if (hayFranja == hayExtra) {
            errores.put("idFranja", ReglasAsistencia.SESION_A_ELEGIR);
            errores.put("idExcepcion", ReglasAsistencia.SESION_A_ELEGIR);
        }
        UUID franja = hayFranja ? uuid(idFranja, "idFranja", errores) : null;
        UUID extra = hayExtra ? uuid(idExcepcion, "idExcepcion", errores) : null;
        return new Referencia(dia, franja, extra);
    }

    private static UUID uuid(String texto, String campo, Map<String, String> errores) {
        try {
            return UUID.fromString(texto.strip());
        } catch (IllegalArgumentException e) {
            errores.put(campo, "debe ser un UUID válido");
            return null;
        }
    }

    /** Los registros del cuerpo, todos los errores juntos bajo {@code registros} (el primero de cada tipo, con su posición). */
    private static List<RegistroAsistencia> registros(List<RegistroAsistenciaRequestDTO> datos, Map<String, String> errores) {
        if (datos == null || datos.isEmpty()) {
            errores.put("registros", ReglasAsistencia.SIN_REGISTROS);
            return List.of();
        }
        List<RegistroAsistencia> registros = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (int i = 0; i < datos.size(); i++) {
            RegistroAsistenciaRequestDTO r = datos.get(i);
            String posicion = "registro " + (i + 1) + ": ";
            if (r == null) {
                errores.putIfAbsent("registros", posicion + "es obligatorio");
                continue;
            }
            UUID id = null;
            if (r.idDeportista() == null || r.idDeportista().isBlank()) {
                errores.putIfAbsent("registros", posicion + "el idDeportista es obligatorio");
            } else {
                try {
                    id = UUID.fromString(r.idDeportista().strip());
                } catch (IllegalArgumentException e) {
                    errores.putIfAbsent("registros", posicion + "el idDeportista debe ser un UUID válido");
                }
            }
            Optional<String> estado = ReglasAsistencia.estado(r.estado());
            if (estado.isEmpty()) {
                errores.putIfAbsent("registros", posicion + ReglasAsistencia.ESTADO_INVALIDO);
            }
            String observaciones = ReglasAsistencia.normalizarObservaciones(r.observaciones());
            if (ReglasAsistencia.observacionesDemasiadoLargas(observaciones)) {
                errores.putIfAbsent("registros", posicion + ReglasAsistencia.OBSERVACIONES_LARGAS);
            }
            if (id != null && !vistos.add(id)) {
                errores.putIfAbsent("registros", posicion + ReglasAsistencia.DEPORTISTA_REPETIDO);
            }
            if (id != null && estado.isPresent()) {
                registros.add(new RegistroAsistencia(id, estado.get(), observaciones));
            }
        }
        return registros;
    }

    /**
     * Busca la sesión (404 si no existe; 400 en {@code fecha} si no cae en el día de la franja o no es la del extra) y
     * comprueba el permiso sobre su categoría (403), en ese orden.
     */
    private Sesion autorizada(Referencia ref, Solicitante solicitante) {
        Sesion sesion = resolver(ref);
        Permisos permisos = permisosDe(solicitante);
        if (!(sesion.esExtra() ? permisos.puedeExtra(sesion.idCategoria())
                : permisos.puedeFranja(sesion.idCategoria(), sesion.idEntrenador()))) {
            throw new SinPermisoAsistenciaException();
        }
        return sesion;
    }

    private Sesion resolver(Referencia ref) {
        if (ref.idFranja() != null) {
            FranjaHorario f = franjas.buscarPorId(ref.idFranja())
                    .orElseThrow(() -> new RecursoNoEncontradoException(HorarioSemanaServiceImpl.FRANJA_NO_EXISTE));
            if (!ReglasExcepcion.coincideConElDia(ref.fecha(), f.getDia())) {
                throw DatosInvalidosException.de("fecha", ReglasExcepcion.DIA_NO_COINCIDE);
            }
            return new Sesion(ref.fecha(), f.getHoraInicio(), f.getHoraFin(), f.getIdCategoria(), f.getCategoria(),
                    f.getSede(), f.getGrupo(), f.getIdEntrenador(), f.getEntrenador(), f.getId(), false);
        }
        ExcepcionHorario x = excepciones.buscarPorId(ref.idExcepcion()).filter(ExcepcionHorario::esExtra)
                .orElseThrow(() -> new RecursoNoEncontradoException(HorarioSemanaServiceImpl.EXTRA_NO_EXISTE));
        if (!x.fecha().equals(ref.fecha())) {
            throw DatosInvalidosException.de("fecha", ReglasAsistencia.FECHA_NO_ES_DEL_EXTRA);
        }
        return new Sesion(x.fecha(), x.horaInicio(), x.horaFin(), x.idCategoria(), x.categoria(), x.sede(), x.grupo(),
                x.idEntrenador(), x.entrenador(), null, true);
    }

    /** Fecha futura (400) y, solo para una franja, cancelada ese día (409). */
    private void verificarFechaYEstado(Sesion sesion, LocalDate hoy) {
        if (ReglasAsistencia.esFechaFutura(sesion.fecha(), hoy)) {
            throw DatosInvalidosException.de("fecha", ReglasAsistencia.FECHA_FUTURA);
        }
        if (!sesion.esExtra() && asistencias.franjaCancelada(sesion.idFranja(), sesion.fecha())) {
            throw new ConflictoException(ReglasAsistencia.SESION_CANCELADA, List.of("fecha"));
        }
    }

    // --- Permisos -------------------------------------------------------------------------------------------

    /**
     * Lo que puede gestionar quien pide: la misma regla de {@code puedeGestionar} del horario de la semana (replicada, sin
     * tocar ese servicio). El administrador todo; el entrenador, las franjas de sus categorías o que él dirige, y los extras
     * de sus categorías (dirigirlo no da permiso); un entrenador sin ficha, nada.
     */
    private record Permisos(boolean todo, Set<UUID> idsCategorias, UUID idEntrenador) {

        boolean puedeFranja(UUID idCategoria, UUID idEntrenadorDeLaFranja) {
            return todo || idsCategorias.contains(idCategoria)
                    || (idEntrenador != null && idEntrenador.equals(idEntrenadorDeLaFranja));
        }

        boolean puedeExtra(UUID idCategoria) {
            return todo || idsCategorias.contains(idCategoria);
        }
    }

    private Permisos permisosDe(Solicitante solicitante) {
        if (solicitante.esAdministrador()) {
            return new Permisos(true, Set.of(), null);
        }
        return entrenadores.buscarPorIdUsuario(solicitante.idUsuario())
                .map(e -> new Permisos(false, categorias.listarPorEntrenador(e.getId()).stream()
                        .map(Categoria::getId).collect(Collectors.toSet()), e.getId()))
                .orElse(new Permisos(false, Set.of(), null));
    }

    /** Quien pasó lista: la ficha del entrenador; {@code null} si fue el administrador. */
    private UUID quienPasaLista(Solicitante solicitante) {
        if (solicitante.esAdministrador()) {
            return null;
        }
        return entrenadores.buscarPorIdUsuario(solicitante.idUsuario()).map(Entrenador::getId).orElse(null);
    }

    // --- Respuesta ------------------------------------------------------------------------------------------

    private record Sesion(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, UUID idCategoria, String categoria,
                          String sede, String grupo, UUID idEntrenador, String entrenador, UUID idFranja, boolean esExtra) {
    }

    private List<DeportistaDeSesion> lista(Sesion sesion, UUID entrenamiento) {
        return asistencias.listarDeportistas(sesion.idCategoria(), sesion.grupo(), LocalDate.now(reloj).getYear(),
                entrenamiento);
    }

    /** Si hay 200 la fecha no es futura y quien pide tiene permiso, así que {@code puedeRegistrar} es siempre {@code true}. */
    private SesionAsistenciaResponseDTO respuesta(Sesion s, UUID entrenamiento) {
        List<DeportistaAsistenciaDTO> deportistas = lista(s, entrenamiento).stream()
                .map(d -> new DeportistaAsistenciaDTO(d.idDeportista(), d.nombre(), d.apellido(), d.estado(),
                        d.observaciones()))
                .toList();
        return new SesionAsistenciaResponseDTO(new DatosSesionAsistenciaDTO(s.fecha().toString(),
                ReglasFranja.formatear(s.horaInicio()), ReglasFranja.formatear(s.horaFin()), s.idCategoria(), s.categoria(),
                s.sede(), s.grupo(), s.idEntrenador(), s.entrenador()), true, deportistas);
    }
}
