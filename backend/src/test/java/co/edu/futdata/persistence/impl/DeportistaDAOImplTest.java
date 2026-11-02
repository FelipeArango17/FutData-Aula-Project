package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.PaginaDTO;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.rules.ReglasDeportista;
import co.edu.futdata.persistence.dao.ConsultaDeportistas;
import co.edu.futdata.persistence.dao.OrdenDeportista;
import co.edu.futdata.services.impl.DeportistaServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * DeportistaDAOImpl contra un PostgreSQL real con todas las migraciones. La categoría se calcula en la consulta
 * con el año que recibe el DAO; la columna heredada {@code deportista.id_categoria} no se lee.
 */
@ExtendWith(DockerDisponible.class)
class DeportistaDAOImplTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ConsultaDeportistas TODOS = consulta(null, null, null, 0, 100);

    private NamedParameterJdbcTemplate jdbc;
    private DeportistaDAOImpl deportistas;
    /** Id de cada una de las 9 categorías, por nombre. */
    private final Map<String, UUID> categorias = new HashMap<>();
    private int documentos;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        for (ReglasDeportista.CategoriaClub c : ReglasDeportista.CATEGORIAS) {
            categorias.put(c.nombre(), jdbc.queryForObject("""
                    INSERT INTO categoria (nombre, edad_minima, edad_maxima) VALUES (:n, :min, :max)
                    RETURNING id_categoria""",
                    new MapSqlParameterSource("n", c.nombre()).addValue("min", c.edadMinima())
                            .addValue("max", c.edadMaxima()), UUID.class));
        }
        deportistas = new DeportistaDAOImpl(jdbc);
    }

    /** Las demás clases borran categorías sin borrar deportistas: no se dejan filas que lo impidan. */
    @AfterEach
    void limpiarTodo() {
        jdbc.getJdbcOperations().update("DELETE FROM documento");
        jdbc.getJdbcOperations().update("DELETE FROM deportista");
        PostgresDePrueba.limpiar();
    }

    /** Storage en memoria: guarda los archivos por ruta y firma con una URL de ejemplo. */
    private static final class StorageEnMemoria implements co.edu.futdata.datasource.external.IAlmacenamientoArchivos {
        final Map<String, byte[]> archivos = new HashMap<>();
        int llamadasDeFirma;

        @Override
        public String subir(String bucket, String ruta, byte[] contenido, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String subir(String bucket, String ruta, byte[] contenido, String contentType, List<String> tipos) {
            archivos.put(ruta, contenido);
            return ruta;
        }

        @Override
        public void eliminar(String bucket, String ruta) {
            archivos.remove(ruta);
        }

        @Override
        public String urlFirmada(String bucket, String ruta, java.time.Duration vigencia) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, String> urlsFirmadas(String bucket, List<String> rutas, java.time.Duration vigencia) {
            llamadasDeFirma++;
            Map<String, String> urls = new HashMap<>();
            rutas.stream().filter(archivos::containsKey).forEach(r -> urls.put(r, "https://firmada.example/" + r + "?token=t"));
            return urls;
        }
    }

    private final StorageEnMemoria storage = new StorageEnMemoria();

    private DeportistaServiceImpl servicio(Clock reloj) {
        return new DeportistaServiceImpl(deportistas, storage,
                new co.edu.futdata.config.FotosProperties("fotos", java.time.Duration.ofMinutes(60)), reloj);
    }

    private static ConsultaDeportistas consulta(Boolean activo, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        return new ConsultaDeportistas(activo, desde, hasta, OrdenDeportista.POR_DEFECTO, false, pagina, tamano);
    }

    private UUID crear(LocalDate nacimiento) {
        return crear(nacimiento, "Gómez " + String.format("%03d", documentos));
    }

    private UUID crear(LocalDate nacimiento, String apellido) {
        Deportista d = new Deportista();
        d.setNombre("Pedro"); d.setApellido(apellido); d.setTipoDocumento("TI");
        d.setDocumento(String.valueOf(1098765000 + ++documentos)); d.setFechaNacimiento(nacimiento);
        d.setTelefono("3001234567"); d.setDireccion("Calle 10 # 20-30");
        return deportistas.crear(d);
    }

    /** Un documento revisado (APROBADO o RECHAZADO) lleva revisado_en (documento_revision_ck). */
    private void documento(UUID deportista, String tipo, String estado) {
        jdbc.update("""
                INSERT INTO documento (id_deportista, tipo, ruta, nombre_original, tipo_contenido, tamano_bytes,
                                       estado, comentario, revisado_en)
                VALUES (:d, :tipo, :ruta, 'archivo.pdf', 'application/pdf', 1024, :estado, :comentario,
                        CASE WHEN :estado = 'PENDIENTE' THEN NULL ELSE now() END)""",
                new MapSqlParameterSource("d", deportista).addValue("tipo", tipo).addValue("estado", estado)
                        .addValue("ruta", deportista + "/" + UUID.randomUUID() + ".pdf")
                        .addValue("comentario", "RECHAZADO".equals(estado) ? "Ilegible" : null));
    }

    private void desactivar(UUID id) {
        jdbc.update("UPDATE deportista SET activo = false WHERE id_deportista = :id", Map.of("id", id));
    }

    // --- Categoría calculada --------------------------------------------------------------------------------

    @Test
    void laCategoriaSeCalculaConElAnioQueRecibe() {
        UUID id = crear(LocalDate.of(2016, 8, 20));

        Deportista en2026 = deportistas.buscarPorId(id, 2026).orElseThrow();
        Deportista en2027 = deportistas.buscarPorId(id, 2027).orElseThrow();

        assertThat(en2026.getCategoria()).isEqualTo("Semillero");
        assertThat(en2026.getIdCategoria()).isEqualTo(categorias.get("Semillero"));
        assertThat(en2027.getCategoria()).isEqualTo("Sub-11");
        assertThat(en2027.getIdCategoria()).isEqualTo(categorias.get("Sub-11"));
    }

    @Test
    void laColumnaHeredadaIdCategoriaNoSeLeeNiSeEscribe() {
        UUID id = crear(LocalDate.of(2016, 8, 20));
        assertThat(jdbc.queryForObject("SELECT id_categoria FROM deportista WHERE id_deportista = :id",
                Map.of("id", id), UUID.class)).isNull();

        // Aunque la columna apunte a otra categoría, la lectura usa la calculada
        jdbc.update("UPDATE deportista SET id_categoria = :c WHERE id_deportista = :id",
                Map.of("c", categorias.get("Sub-16"), "id", id));

        Deportista leido = deportistas.buscarPorId(id, 2026).orElseThrow();
        assertThat(leido.getCategoria()).isEqualTo("Semillero");
        assertThat(leido.getIdCategoria()).isEqualTo(categorias.get("Semillero"));
    }

    @Test
    void fueraDe4A16NoTieneCategoria() {
        UUID mayor = crear(LocalDate.of(2009, 12, 31));
        UUID menor = crear(LocalDate.of(2023, 1, 1));

        for (UUID id : List.of(mayor, menor)) {
            Deportista d = deportistas.buscarPorId(id, 2026).orElseThrow();
            assertThat(d.getCategoria()).isNull();
            assertThat(d.getIdCategoria()).isNull();
        }
    }

    @Test
    void listarDevuelveCadaDeportistaUnaVezConSuCategoria() {
        crear(LocalDate.of(2022, 12, 31)); // Párvulos
        crear(LocalDate.of(2016, 1, 1));   // Semillero
        crear(LocalDate.of(2010, 6, 6));   // Sub-16
        crear(LocalDate.of(2009, 6, 6));   // fuera de rango

        List<Deportista> lista = deportistas.listar(TODOS, 2026, true);

        assertThat(lista).hasSize(4);
        assertThat(lista).extracting(Deportista::getCategoria)
                .containsExactly("Párvulos", "Semillero", "Sub-16", null);
    }

    @Test
    void leeGrupoYActivoDeLaBase() {
        UUID id = crear(LocalDate.of(2017, 3, 3));
        assertThat(deportistas.buscarPorId(id, 2026).orElseThrow().isActivo()).isTrue();

        jdbc.update("UPDATE deportista SET grupo = 'mañana', activo = false WHERE id_deportista = :id", Map.of("id", id));

        Deportista leido = deportistas.buscarPorId(id, 2026).orElseThrow();
        assertThat(leido.getGrupo()).isEqualTo("mañana");
        assertThat(leido.isActivo()).isFalse();
    }

    @Test
    void buscarUnIdQueNoExisteDevuelveVacio() {
        assertThat(deportistas.buscarPorId(UUID.randomUUID(), 2026)).isEmpty();
    }

    // --- Paginación, orden y total --------------------------------------------------------------------------

    @Test
    void paginaConMasDeUnaPaginaSinRepetirNiPerder() {
        List<UUID> creados = new ArrayList<>();
        for (int i = 0; i < 25; i++) creados.add(crear(LocalDate.of(2014, 1, 1)));

        List<Deportista> p0 = deportistas.listar(consulta(null, null, null, 0, 10), 2026, true);
        List<Deportista> p1 = deportistas.listar(consulta(null, null, null, 1, 10), 2026, true);
        List<Deportista> p2 = deportistas.listar(consulta(null, null, null, 2, 10), 2026, true);
        List<Deportista> p3 = deportistas.listar(consulta(null, null, null, 3, 10), 2026, true);

        assertThat(p0).hasSize(10);
        assertThat(p1).hasSize(10);
        assertThat(p2).hasSize(5);
        assertThat(p3).isEmpty();
        List<UUID> todos = new ArrayList<>();
        for (List<Deportista> p : List.of(p0, p1, p2)) p.forEach(d -> todos.add(d.getId()));
        assertThat(todos).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(creados);
        assertThat(deportistas.contar(consulta(null, null, null, 2, 10))).isEqualTo(25);
    }

    @Test
    void conElMismoOrdenElDesempateEsPorId() {
        // Mismo apellido y nombre: el orden lo decide id_deportista y no cambia entre consultas
        for (int i = 0; i < 6; i++) crear(LocalDate.of(2014, 1, 1), "Igual");

        List<UUID> primera = deportistas.listar(TODOS, 2026, false).stream().map(Deportista::getId).toList();
        List<UUID> segunda = deportistas.listar(TODOS, 2026, false).stream().map(Deportista::getId).toList();

        assertThat(primera).isEqualTo(segunda);
        assertThat(primera).isSortedAccordingTo(java.util.Comparator.comparing(UUID::toString));
    }

    @Test
    void ordenaPorElCampoPedidoEnAmbasDirecciones() {
        crear(LocalDate.of(2012, 1, 1), "Beta");
        crear(LocalDate.of(2016, 1, 1), "Alfa");
        crear(LocalDate.of(2014, 1, 1), "Gama");

        var porFecha = new ConsultaDeportistas(null, null, null, OrdenDeportista.FECHA_NACIMIENTO, true, 0, 10);
        var porApellido = new ConsultaDeportistas(null, null, null, OrdenDeportista.APELLIDO, true, 0, 10);

        assertThat(deportistas.listar(porFecha, 2026, false)).extracting(Deportista::getApellido)
                .containsExactly("Alfa", "Gama", "Beta");
        assertThat(deportistas.listar(porApellido, 2026, false)).extracting(Deportista::getApellido)
                .containsExactly("Gama", "Beta", "Alfa");
    }

    @Test
    void elTotalRespetaLosFiltros() {
        UUID a = crear(LocalDate.of(2016, 3, 3)); // Semillero
        crear(LocalDate.of(2017, 3, 3));          // Semillero
        UUID c = crear(LocalDate.of(2015, 3, 3)); // Sub-11
        crear(LocalDate.of(2012, 3, 3));          // Sub-14
        desactivar(a);
        desactivar(c);
        LocalDate desde = LocalDate.of(2016, 1, 1);
        LocalDate hasta = LocalDate.of(2017, 12, 31);

        assertThat(deportistas.contar(TODOS)).isEqualTo(4);
        assertThat(deportistas.contar(consulta(true, null, null, 0, 10))).isEqualTo(2);
        assertThat(deportistas.contar(consulta(false, null, null, 0, 10))).isEqualTo(2);
        assertThat(deportistas.contar(consulta(null, desde, hasta, 0, 10))).isEqualTo(2);
        assertThat(deportistas.contar(consulta(true, desde, hasta, 0, 10))).isEqualTo(1);
        // La página puede tener menos filas que el total
        assertThat(deportistas.listar(consulta(null, null, null, 0, 3), 2026, false)).hasSize(3);
    }

    // --- Filtro por categoría con el reloj (servicio + DAO) -------------------------------------------------

    @Test
    void elFiltroPorCategoriaCambiaElPrimeroDeEneroEnHoraDeColombia() {
        crear(LocalDate.of(2016, 12, 31), "Dos mil dieciséis");
        crear(LocalDate.of(2018, 1, 1), "Dos mil dieciocho");
        // 31/12/2026 23:59:59 en Bogotá (en UTC ya es 2027) y 01/01/2027 00:00 en Bogotá
        var el31 = servicio(Clock.fixed(Instant.parse("2027-01-01T04:59:59Z"), BOGOTA));
        var el1 = servicio(Clock.fixed(Instant.parse("2027-01-01T05:00:00Z"), BOGOTA));

        // 2026: el de 2016 tiene 10 (Semillero) y el de 2018 tiene 8 (Cachorros)
        assertThat(apellidos(el31.listar(null, null, null, null, "Semillero", true))).containsExactly("Dos mil dieciséis");
        assertThat(apellidos(el31.listar(null, null, null, null, "Sub-11", true))).isEmpty();
        assertThat(apellidos(el31.listar(null, null, null, null, "Cachorros", true))).containsExactly("Dos mil dieciocho");
        // 2027: el de 2016 tiene 11 (Sub-11) y el de 2018 tiene 9 (Semillero)
        assertThat(apellidos(el1.listar(null, null, null, null, "Semillero", true))).containsExactly("Dos mil dieciocho");
        assertThat(apellidos(el1.listar(null, null, null, null, "Sub-11", true))).containsExactly("Dos mil dieciséis");
        assertThat(el1.listar(null, null, null, null, "Sub-11", true).totalElements()).isEqualTo(1);
        assertThat(apellidos(el1.listar(null, null, null, null, "Cachorros", true))).isEmpty();
    }

    private static List<String> apellidos(PaginaDTO<DeportistaResponseDTO> pagina) {
        return pagina.content().stream().map(DeportistaResponseDTO::apellido).toList();
    }

    // --- Crear y actualizar ---------------------------------------------------------------------------------

    @Test
    void creaConPosicionGrupoYActivo() {
        Deportista d = new Deportista();
        d.setNombre("Sara"); d.setApellido("Ríos"); d.setTipoDocumento("RC"); d.setDocumento("1098700001");
        d.setFechaNacimiento(LocalDate.of(2018, 2, 2)); d.setTelefono("3001234567"); d.setDireccion("Calle 1 # 2-3");
        d.setPosicion("Delantera"); d.setGrupo("tarde");

        Deportista leido = deportistas.buscarPorId(deportistas.crear(d), 2026).orElseThrow();

        assertThat(leido.getTipoDocumento()).isEqualTo("RC");
        assertThat(leido.getPosicion()).isEqualTo("Delantera");
        assertThat(leido.getGrupo()).isEqualTo("tarde");
        assertThat(leido.isActivo()).isTrue();
    }

    @Test
    void unDocumentoRepetidoAlCrearLanzaDatoDuplicado() {
        // Mismo número de documento, otro tipo y otro nombre: la unicidad es solo sobre el número
        Deportista otro = deportistas.buscarPorId(crear(LocalDate.of(2014, 1, 1)), 2026).orElseThrow();
        otro.setNombre("Otro"); otro.setTipoDocumento("RC");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deportistas.crear(otro))
                .isInstanceOf(co.edu.futdata.persistence.dao.DatoDuplicadoException.class);
    }

    @Test
    void actualizaLosCamposEditablesSinTocarFoto() {
        UUID id = crear(LocalDate.of(2017, 3, 3));
        jdbc.update("UPDATE deportista SET foto = 'fotos/x.jpg' WHERE id_deportista = :id", Map.of("id", id));
        Deportista cambios = deportistas.buscarPorId(id, 2026).orElseThrow();
        cambios.setNombre("Juana"); cambios.setTipoDocumento("RC"); cambios.setPosicion("Arquera");
        cambios.setGrupo("mañana"); cambios.setActivo(false); cambios.setFechaNacimiento(LocalDate.of(2016, 3, 3));

        assertThat(deportistas.actualizar(id, cambios)).isTrue();

        Deportista leido = deportistas.buscarPorId(id, 2026).orElseThrow();
        assertThat(leido.getNombre()).isEqualTo("Juana");
        assertThat(leido.getTipoDocumento()).isEqualTo("RC");
        assertThat(leido.getPosicion()).isEqualTo("Arquera");
        assertThat(leido.getGrupo()).isEqualTo("mañana");
        assertThat(leido.isActivo()).isFalse();
        assertThat(leido.getFechaNacimiento()).isEqualTo(LocalDate.of(2016, 3, 3));
        assertThat(jdbc.queryForObject("SELECT foto FROM deportista WHERE id_deportista = :id", Map.of("id", id),
                String.class)).isEqualTo("fotos/x.jpg");
    }

    @Test
    void unGrupoNuloSeGuardaNulo() {
        UUID id = crear(LocalDate.of(2017, 3, 3));
        jdbc.update("UPDATE deportista SET grupo = 'tarde' WHERE id_deportista = :id", Map.of("id", id));
        Deportista cambios = deportistas.buscarPorId(id, 2026).orElseThrow();
        cambios.setGrupo(null);

        deportistas.actualizar(id, cambios);

        assertThat(jdbc.queryForObject("SELECT grupo FROM deportista WHERE id_deportista = :id", Map.of("id", id),
                String.class)).isNull();
    }

    @Test
    void unDocumentoDeOtroAlActualizarLanzaDatoDuplicado() {
        UUID a = crear(LocalDate.of(2014, 1, 1));
        UUID b = crear(LocalDate.of(2014, 1, 1));
        Deportista cambios = deportistas.buscarPorId(b, 2026).orElseThrow();
        cambios.setDocumento(deportistas.buscarPorId(a, 2026).orElseThrow().getDocumento());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deportistas.actualizar(b, cambios))
                .isInstanceOf(co.edu.futdata.persistence.dao.DatoDuplicadoException.class);
    }

    @Test
    void actualizarUnIdQueNoExisteDevuelveFalse() {
        Deportista d = deportistas.buscarPorId(crear(LocalDate.of(2014, 1, 1)), 2026).orElseThrow();
        d.setDocumento("99999999");

        assertThat(deportistas.actualizar(UUID.randomUUID(), d)).isFalse();
    }

    /** Servicio + DAO: el nacido en 2009 ya no puede inscribirse, pero sí editarse y desactivarse. */
    @Test
    void elServicioDesactivaYEditaAUnNacidoEn2009() {
        UUID id = crear(LocalDate.of(2009, 5, 5)); // directo al DAO: el servicio no lo dejaría crear en 2026
        var servicio = servicio(Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), BOGOTA));

        var r = servicio.actualizar(id, new co.edu.futdata.api.dto.DeportistaRequestDTO("Pedro", "Gómez Ruiz", "TI",
                "1098700999", LocalDate.of(2009, 5, 5), "3007654321", "Carrera 5 # 6-7", null, null, false));

        assertThat(r.activo()).isFalse();
        assertThat(r.apellido()).isEqualTo("Gómez Ruiz");
        assertThat(r.categoria()).isNull();
    }

    // --- Foto (servicio + DAO + Storage en memoria) ---------------------------------------------------------

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 4, 5};

    private String fotoGuardada(UUID id) {
        return jdbc.queryForObject("SELECT foto FROM deportista WHERE id_deportista = :id", Map.of("id", id), String.class);
    }

    @Test
    void subirReemplazarYQuitarLaFotoEnLaBase() {
        UUID id = crear(LocalDate.of(2014, 1, 1));
        var servicio = servicio(Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), BOGOTA));

        DeportistaResponseDTO conJpg = servicio.subirFoto(id, JPG);
        String primera = fotoGuardada(id);
        assertThat(primera).matches(id + "/[0-9a-f-]{36}\\.jpg");
        assertThat(conJpg.foto()).isEqualTo("https://firmada.example/" + primera + "?token=t");

        DeportistaResponseDTO conPng = servicio.subirFoto(id, PNG);
        String segunda = fotoGuardada(id);
        assertThat(segunda).matches(id + "/[0-9a-f-]{36}\\.png").isNotEqualTo(primera);
        assertThat(storage.archivos).containsOnlyKeys(segunda);
        assertThat(conPng.foto()).contains(segunda);

        DeportistaResponseDTO sinFoto = servicio.eliminarFoto(id);
        assertThat(fotoGuardada(id)).isNull();
        assertThat(storage.archivos).isEmpty();
        assertThat(sinFoto.foto()).isNull();
    }

    @Test
    void laListaFirmaTodasLasFotosEnUnaSolaLlamada() {
        var servicio = servicio(Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), BOGOTA));
        for (int i = 0; i < 3; i++) servicio.subirFoto(crear(LocalDate.of(2014, 1, 1)), JPG);
        crear(LocalDate.of(2014, 1, 1)); // sin foto
        storage.llamadasDeFirma = 0;

        PaginaDTO<DeportistaResponseDTO> pagina = servicio.listar(null, null, null, null, null, false);

        assertThat(storage.llamadasDeFirma).isEqualTo(1);
        assertThat(pagina.content()).extracting(DeportistaResponseDTO::foto)
                .filteredOn(java.util.Objects::nonNull).hasSize(3).allMatch(u -> u.startsWith("https://firmada.example/"));
    }

    // --- estadoInscripcion ----------------------------------------------------------------------------------

    @Test
    void sinDocumentosLaInscripcionEstaPendiente() {
        UUID id = crear(LocalDate.of(2014, 1, 1));

        assertThat(deportistas.buscarPorId(id, 2026).orElseThrow().getEstadoInscripcion()).isEqualTo("PENDIENTE");
    }

    @Test
    void conLosCuatroObligatoriosAprobadosEstaCompleta() {
        UUID id = crear(LocalDate.of(2014, 1, 1));
        for (String tipo : ReglasDeportista.DOCUMENTOS_OBLIGATORIOS) documento(id, tipo, "APROBADO");

        assertThat(deportistas.buscarPorId(id, 2026).orElseThrow().getEstadoInscripcion()).isEqualTo("COMPLETA");
        assertThat(deportistas.listar(TODOS, 2026, true).getFirst().getEstadoInscripcion()).isEqualTo("COMPLETA");
    }

    @Test
    void unRechazadoYUnAprobadoDelMismoTipoCuentaComoAprobado() {
        UUID id = crear(LocalDate.of(2014, 1, 1));
        for (String tipo : ReglasDeportista.DOCUMENTOS_OBLIGATORIOS) {
            documento(id, tipo, "RECHAZADO");
            documento(id, tipo, "APROBADO");
        }

        assertThat(deportistas.listar(TODOS, 2026, true).getFirst().getEstadoInscripcion()).isEqualTo("COMPLETA");
    }

    @Test
    void conTresDeCuatroAprobadosSiguePendiente() {
        UUID id = crear(LocalDate.of(2014, 1, 1));
        documento(id, "DOCUMENTO_IDENTIDAD", "APROBADO");
        documento(id, "CERTIFICADO_ESTUDIO", "APROBADO");
        documento(id, "FORMULARIO_INSCRIPCION", "APROBADO");
        documento(id, "AUTORIZACION_HABEAS_DATA", "RECHAZADO");
        documento(id, "AUTORIZACION_HABEAS_DATA", "PENDIENTE");
        // Un documento opcional aprobado no reemplaza a un obligatorio
        documento(id, "PAZ_Y_SALVO", "APROBADO");

        assertThat(deportistas.listar(TODOS, 2026, true).getFirst().getEstadoInscripcion()).isEqualTo("PENDIENTE");
    }

    @Test
    void cadaDeportistaTieneSuPropioEstado() {
        UUID completo = crear(LocalDate.of(2014, 1, 1), "Completo");
        crear(LocalDate.of(2014, 1, 1), "Pendiente");
        for (String tipo : ReglasDeportista.DOCUMENTOS_OBLIGATORIOS) documento(completo, tipo, "APROBADO");

        assertThat(deportistas.listar(TODOS, 2026, true)).extracting(Deportista::getApellido, Deportista::getEstadoInscripcion)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Completo", "COMPLETA"),
                        org.assertj.core.groups.Tuple.tuple("Pendiente", "PENDIENTE"));
    }

    @Test
    void sinEstadoInscripcionNoLoCalcula() {
        UUID id = crear(LocalDate.of(2014, 1, 1));
        for (String tipo : ReglasDeportista.DOCUMENTOS_OBLIGATORIOS) documento(id, tipo, "APROBADO");

        assertThat(deportistas.listar(TODOS, 2026, false).getFirst().getEstadoInscripcion()).isNull();
    }
}
