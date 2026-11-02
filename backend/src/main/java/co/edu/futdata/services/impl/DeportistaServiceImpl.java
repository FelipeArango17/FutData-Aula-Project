package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.DeportistaMapper;
import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.PaginaDTO;
import co.edu.futdata.config.FotosProperties;
import co.edu.futdata.datasource.external.ArchivoNoEncontradoException;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.rules.ReglasDeportista;
import co.edu.futdata.domain.rules.ReglasEntrenador;
import co.edu.futdata.domain.rules.ReglasFoto;
import co.edu.futdata.persistence.dao.ConsultaDeportistas;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.OrdenDeportista;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IDeportistaService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deportistas: lista paginada, consulta, alta, edición y foto.
 *
 * <p><b>Registros:</b> nunca el número de documento, el nombre ni la ruta de la foto de un menor; ante una falla
 * de Storage solo el tipo de error.
 */
@Service
public class DeportistaServiceImpl implements IDeportistaService {

    static final int TAMANO_POR_DEFECTO = 10;
    static final int TAMANO_MAXIMO = 100;
    /** El entrenador no ve el número de documento: tampoco puede ordenar por él. */
    static final Set<OrdenDeportista> ORDENES_ENTRENADOR =
            EnumSet.of(OrdenDeportista.NOMBRE, OrdenDeportista.APELLIDO, OrdenDeportista.FECHA_NACIMIENTO);

    private static final Logger log = LoggerFactory.getLogger(DeportistaServiceImpl.class);
    private static final String NO_EXISTE = "No existe un deportista con ese id.";

    private final IDeportistaDAO deportistas;
    private final IAlmacenamientoArchivos almacenamiento;
    private final FotosProperties fotos;
    private final Clock reloj;

    /** @param reloj el de {@code RelojConfig} (hora de Colombia); las pruebas pasan uno fijo */
    public DeportistaServiceImpl(IDeportistaDAO deportistas, IAlmacenamientoArchivos almacenamiento,
                                 FotosProperties fotos, Clock reloj) {
        this.deportistas = deportistas;
        this.almacenamiento = almacenamiento;
        this.fotos = fotos;
        this.reloj = reloj;
    }

    // --- Lectura ------------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PaginaDTO<DeportistaResponseDTO> listar(Integer page, Integer size, String sort, Boolean activo,
                                                   String categoria, boolean esAdministrador) {
        int anio = anioActual();
        ConsultaDeportistas consulta = consulta(page, size, sort, activo, categoria, anio, esAdministrador);
        List<Deportista> pagina = deportistas.listar(consulta, anio, esAdministrador);
        Map<String, String> urls = urlsDeFotos(pagina);
        List<DeportistaResponseDTO> contenido = pagina.stream()
                .map(d -> respuesta(d, esAdministrador, urls)).toList();
        return PaginaDTO.de(contenido, consulta.pagina(), consulta.tamano(), deportistas.contar(consulta));
    }

    /** Valida los parámetros de la lista; todos los errores juntos en un solo 400. */
    private static ConsultaDeportistas consulta(Integer page, Integer size, String sort, Boolean activo,
                                                String categoria, int anio, boolean esAdministrador) {
        Map<String, String> errores = new LinkedHashMap<>();
        int pagina = page == null ? 0 : page;
        if (pagina < 0) errores.put("page", "debe ser 0 o mayor (la primera página es 0)");
        int tamano = size == null ? TAMANO_POR_DEFECTO : size;
        if (tamano < 1 || tamano > TAMANO_MAXIMO) errores.put("size", "debe estar entre 1 y " + TAMANO_MAXIMO);

        OrdenDeportista orden = OrdenDeportista.POR_DEFECTO;
        boolean descendente = false;
        if (sort != null && !sort.isBlank()) {
            Set<OrdenDeportista> permitidos = esAdministrador ? EnumSet.allOf(OrdenDeportista.class) : ORDENES_ENTRENADOR;
            String[] partes = sort.split(",", -1);
            Optional<OrdenDeportista> pedido = OrdenDeportista.porCampo(partes[0].strip()).filter(permitidos::contains);
            String direccion = partes.length > 1 ? partes[1].strip().toLowerCase(Locale.ROOT) : "asc";
            if (pedido.isEmpty() || partes.length > 2 || !(direccion.equals("asc") || direccion.equals("desc"))) {
                errores.put("sort", "debe ser campo o campo,asc o campo,desc; campos: " + permitidos.stream()
                        .map(OrdenDeportista::campo).collect(Collectors.joining(", ")));
            } else {
                orden = pedido.get();
                descendente = direccion.equals("desc");
            }
        }

        LocalDate nacidoDesde = null;
        LocalDate nacidoHasta = null;
        if (categoria != null && !categoria.isBlank()) {
            Optional<ReglasDeportista.CategoriaClub> c = ReglasDeportista.categoriaPorNombre(categoria.strip());
            if (c.isEmpty()) {
                errores.put("categoria", "debe ser una de " + String.join(", ", ReglasEntrenador.CATEGORIAS));
            } else {
                // edad por año entre edad_minima y edad_maxima ⇔ nacido entre (anio - maxima) y (anio - minima)
                nacidoDesde = LocalDate.of(anio - c.get().edadMaxima(), 1, 1);
                nacidoHasta = LocalDate.of(anio - c.get().edadMinima(), 12, 31);
            }
        }
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        return new ConsultaDeportistas(activo, nacidoDesde, nacidoHasta, orden, descendente, pagina, tamano);
    }

    @Override
    @Transactional(readOnly = true)
    public DeportistaResponseDTO consultar(UUID id) {
        Deportista d = buscar(id);
        return respuesta(d, true, urlsDeFotos(List.of(d)));
    }

    // --- Alta y edición -----------------------------------------------------------------------------------------

    @Override
    @Transactional
    public DeportistaResponseDTO crear(DeportistaRequestDTO datos) {
        if (datos == null) throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        Deportista limpio = ReglasDeportista.limpiar(DeportistaMapper.toEntity(datos));
        Map<String, String> errores = ReglasDeportista.validar(limpio, LocalDate.now(reloj));
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        try {
            return consultar(deportistas.crear(limpio));
        } catch (DatoDuplicadoException e) {
            throw documentoRepetido();
        }
    }

    /**
     * Reemplaza los campos editables. La regla de 4 a 16 años solo se exige si cambia la fecha de nacimiento: a quien
     * ya se pasó de 16 se le puede editar o desactivar sin cambiar su fecha.
     */
    @Override
    @Transactional
    public DeportistaResponseDTO actualizar(UUID id, DeportistaRequestDTO datos) {
        if (datos == null) throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        Deportista guardado = buscar(id);
        Deportista limpio = ReglasDeportista.limpiar(DeportistaMapper.toEntity(datos));
        boolean cambiaFecha = !Objects.equals(limpio.getFechaNacimiento(), guardado.getFechaNacimiento());
        Map<String, String> errores = new LinkedHashMap<>(
                ReglasDeportista.validar(limpio, LocalDate.now(reloj), cambiaFecha));
        if (datos.activo() == null) errores.put("activo", "es obligatorio");
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        try {
            if (!deportistas.actualizar(id, limpio)) throw new RecursoNoEncontradoException(NO_EXISTE);
        } catch (DatoDuplicadoException e) {
            throw documentoRepetido();
        }
        return consultar(id);
    }

    /** No dice de quién es el documento: solo que ya está registrado. */
    private static ConflictoException documentoRepetido() {
        return new ConflictoException("documento: ya está registrado en otro deportista", List.of("documento"));
    }

    // --- Foto ---------------------------------------------------------------------------------------------------

    /**
     * Sube la foto nueva con una ruta propia ({@code {id}/{uuid}.jpg|png}, nunca el nombre del archivo del
     * cliente), actualiza {@code deportista.foto} y solo después borra la anterior. Sin {@code @Transactional}: el
     * cambio de ruta queda confirmado antes de borrar el archivo viejo. Si ese borrado falla, se registra el tipo
     * de error y la respuesta sigue siendo exitosa (queda un archivo huérfano, no una foto rota).
     */
    @Override
    public DeportistaResponseDTO subirFoto(UUID id, byte[] contenido) {
        Deportista d = buscar(id);
        Map<String, String> errores = ReglasFoto.validar(contenido);
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        String tipo = ReglasFoto.tipoPorContenido(contenido).orElseThrow();
        String ruta = id + "/" + UUID.randomUUID() + ReglasFoto.extension(tipo);

        almacenamiento.subir(fotos.bucket(), ruta, contenido, tipo, IAlmacenamientoArchivos.IMAGENES);
        boolean guardada;
        try {
            guardada = deportistas.actualizarFoto(id, ruta);
        } catch (RuntimeException e) {
            borrarSinFallar(ruta, "la foto recién subida");
            throw e;
        }
        if (!guardada) {
            borrarSinFallar(ruta, "la foto recién subida");
            throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        if (d.getFoto() != null) borrarSinFallar(d.getFoto(), "la foto anterior");
        return consultar(id);
    }

    /**
     * Borra el archivo y deja {@code foto} en NULL. Si Storage falla, no cambia nada (se puede reintentar); si el
     * archivo ya no estaba, igual deja la columna en NULL. Sin foto, no hace nada.
     */
    @Override
    public DeportistaResponseDTO eliminarFoto(UUID id) {
        Deportista d = buscar(id);
        if (d.getFoto() != null) {
            try {
                almacenamiento.eliminar(fotos.bucket(), d.getFoto());
            } catch (ArchivoNoEncontradoException e) {
                // Ya no estaba en el bucket: solo falta limpiar la columna.
            }
            if (!deportistas.actualizarFoto(id, null)) throw new RecursoNoEncontradoException(NO_EXISTE);
        }
        return consultar(id);
    }

    private void borrarSinFallar(String ruta, String cual) {
        try {
            almacenamiento.eliminar(fotos.bucket(), ruta);
        } catch (ArchivoNoEncontradoException e) {
            // Ya no estaba.
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar {} del bucket {}: {}", cual, fotos.bucket(), e.getClass().getSimpleName());
        }
    }

    /**
     * URL firmadas de las fotos de la página en una sola llamada a Storage. Si Storage falla, la página se responde
     * igual, con {@code foto} en null.
     */
    private Map<String, String> urlsDeFotos(List<Deportista> pagina) {
        List<String> rutas = pagina.stream().map(Deportista::getFoto).filter(Objects::nonNull).distinct().toList();
        if (rutas.isEmpty()) return Map.of();
        try {
            return almacenamiento.urlsFirmadas(fotos.bucket(), rutas, fotos.vigenciaUrlFirmada());
        } catch (RuntimeException e) {
            log.warn("No se pudieron firmar {} foto(s) del bucket {}: {}", rutas.size(), fotos.bucket(),
                    e.getClass().getSimpleName());
            return Map.of();
        }
    }

    // --- Ayudas -------------------------------------------------------------------------------------------------

    private Deportista buscar(UUID id) {
        return deportistas.buscarPorId(id, anioActual()).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
    }

    /** Año actual según el reloj de la aplicación (hora de Colombia): define la categoría por año de nacimiento. */
    private int anioActual() {
        return LocalDate.now(reloj).getYear();
    }

    /**
     * La categoría ya viene calculada por el DAO. El grupo guardado solo se devuelve si esa categoría lo admite: a
     * un deportista que pasó de Semillero a Sub-11 el 1 de enero se le descarta sin tocar la base. La foto sale como
     * URL firmada, nunca como ruta.
     */
    private DeportistaResponseDTO respuesta(Deportista d, boolean esAdministrador, Map<String, String> urls) {
        int edad = d.getFechaNacimiento().until(LocalDate.now(reloj)).getYears();
        boolean admiteGrupo = d.getCategoria() != null && ReglasDeportista.categoriaPorNombre(d.getCategoria())
                .map(ReglasDeportista.CategoriaClub::admiteGrupo).orElse(false);
        String foto = d.getFoto() == null ? null : urls.get(d.getFoto());
        return new DeportistaResponseDTO(d.getId(), d.getNombre(), d.getApellido(), d.getTipoDocumento(),
                esAdministrador ? d.getDocumento() : null, d.getFechaNacimiento(), edad,
                esAdministrador ? d.getTelefono() : null, esAdministrador ? d.getDireccion() : null,
                d.getPosicion(), foto, d.getCategoria(), d.getIdCategoria(), admiteGrupo ? d.getGrupo() : null,
                d.isActivo(), esAdministrador ? d.getEstadoInscripcion() : null);
    }
}
