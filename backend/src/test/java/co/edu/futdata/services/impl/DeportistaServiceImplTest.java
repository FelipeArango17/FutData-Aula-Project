package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.api.dto.DeportistaResponseDTO;
import co.edu.futdata.api.dto.PaginaDTO;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.persistence.dao.ConsultaDeportistas;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.OrdenDeportista;
import org.mockito.ArgumentCaptor;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.config.FotosProperties;
import co.edu.futdata.datasource.external.AlmacenamientoNoDisponibleException;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeportistaServiceImplTest {

    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final UUID SEMILLERO = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000009");
    private static final UUID SUB_11 = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000011");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Duration VIGENCIA = Duration.ofMinutes(60);
    private static final FotosProperties FOTOS = new FotosProperties("fotos", VIGENCIA);
    private IDeportistaDAO dao;
    private IAlmacenamientoArchivos almacenamiento;
    private DeportistaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        dao = mock(IDeportistaDAO.class);
        almacenamiento = mock(IAlmacenamientoArchivos.class);
        servicio = new DeportistaServiceImpl(dao, almacenamiento, FOTOS, Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), BOGOTA));
    }

    private DeportistaRequestDTO datos() {
        return new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678",
                LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30");
    }

    private static Deportista leido(LocalDate nacimiento, String categoria, UUID idCategoria, String grupo) {
        Deportista d = new Deportista();
        d.setId(ID); d.setNombre("Ana"); d.setApellido("Pérez"); d.setTipoDocumento("TI");
        d.setDocumento("12345678"); d.setFechaNacimiento(nacimiento);
        d.setTelefono("3001234567"); d.setDireccion("Calle 10 # 20-30");
        d.setCategoria(categoria); d.setIdCategoria(idCategoria); d.setGrupo(grupo);
        return d;
    }

    @Test
    void creaYCalculaEdad() {
        when(dao.crear(any())).thenReturn(ID);
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null)));

        assertThat(servicio.crear(datos()).edad()).isEqualTo(14);
        verify(dao).crear(any(Deportista.class));
    }

    @Test
    void unNacidoEn2009NoSeCreaEn2026() {
        DeportistaRequestDTO fueraDeRango = new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678",
                LocalDate.of(2009, 5, 5), "3001234567", "Calle 10 # 20-30");

        assertThatThrownBy(() -> servicio.crear(fueraDeRango))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("fechaNacimiento")
                .hasMessageContaining("el club solo inscribe de 4 a 16 años");
        verify(dao, never()).crear(any());
    }

    /** Sin consulta previa: el 409 sale de la violación de unicidad que captura el DAO. */
    @Test
    void documentoDuplicadoDevuelve409ConCampo() {
        when(dao.crear(any())).thenThrow(new DatoDuplicadoException("documento", null));

        assertThatThrownBy(() -> servicio.crear(datos()))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("documento: ya está registrado en otro deportista")
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("documento"));
    }

    @Test
    void creaConRegistroCivilPosicionYGrupo() {
        when(dao.crear(any())).thenReturn(ID);
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(LocalDate.of(2018, 4, 10), "Cachorros", null, "tarde")));

        servicio.crear(new DeportistaRequestDTO("Ana", "Pérez", "RC", "1098765432", LocalDate.of(2018, 4, 10),
                "3001234567", "Calle 10 # 20-30", " Delantera ", "tarde", false));

        ArgumentCaptor<Deportista> guardado = ArgumentCaptor.forClass(Deportista.class);
        verify(dao).crear(guardado.capture());
        assertThat(guardado.getValue().getTipoDocumento()).isEqualTo("RC");
        assertThat(guardado.getValue().getPosicion()).isEqualTo("Delantera");
        assertThat(guardado.getValue().getGrupo()).isEqualTo("tarde");
    }

    @Test
    void crearConCedulaDa400() {
        assertThatThrownBy(() -> servicio.crear(new DeportistaRequestDTO("Ana", "Pérez", "CC", "1098765432",
                LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("tipoDocumento: debe ser TI, RC, CE o PASAPORTE");
        verify(dao, never()).crear(any());
    }

    @Test
    void unaPosicionDeMasDe50CaracteresDa400() {
        assertThatThrownBy(() -> servicio.crear(new DeportistaRequestDTO("Ana", "Pérez", "TI", "1098765432",
                LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30", "x".repeat(51), null, null)))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("posicion");
    }

    // --- PUT ------------------------------------------------------------------------------------------------

    private static DeportistaRequestDTO edicion(LocalDate nacimiento, String grupo, Boolean activo) {
        return new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678", nacimiento, "3001234567",
                "Calle 10 # 20-30", "Arquera", grupo, activo);
    }

    @Test
    void desactivaAUnNacidoEn2009SinCambiarSuFecha() {
        LocalDate nacimiento = LocalDate.of(2009, 5, 5);
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(nacimiento, null, null, null)));
        when(dao.actualizar(eq(ID), any())).thenReturn(true);

        servicio.actualizar(ID, edicion(nacimiento, null, false));

        ArgumentCaptor<Deportista> guardado = ArgumentCaptor.forClass(Deportista.class);
        verify(dao).actualizar(eq(ID), guardado.capture());
        assertThat(guardado.getValue().isActivo()).isFalse();
        assertThat(guardado.getValue().getPosicion()).isEqualTo("Arquera");
    }

    @Test
    void cambiarLaFechaAUnaFueraDeRangoDa400() {
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null)));

        assertThatThrownBy(() -> servicio.actualizar(ID, edicion(LocalDate.of(2009, 5, 5), null, true)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("fechaNacimiento: por año de nacimiento tiene 17 años en 2026");
        verify(dao, never()).actualizar(any(), any());
    }

    @Test
    void unGrupoEnSub11Da400() {
        LocalDate nacimiento = LocalDate.of(2015, 4, 10);
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(nacimiento, "Sub-11", SUB_11, null)));

        assertThatThrownBy(() -> servicio.actualizar(ID, edicion(nacimiento, "mañana", true)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("grupo: solo se asigna en Cachorros y Semillero; por año de nacimiento está en Sub-11");
        verify(dao, never()).actualizar(any(), any());
    }

    @Test
    void unGrupoAUnNacidoEn2009SinCambiarLaFechaDa400() {
        LocalDate nacimiento = LocalDate.of(2009, 5, 5);
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(nacimiento, null, null, null)));

        assertThatThrownBy(() -> servicio.actualizar(ID, edicion(nacimiento, "tarde", true)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("grupo: solo se asigna en Cachorros y Semillero; por año de nacimiento no tiene categoría")
                .hasMessageNotContaining("fechaNacimiento");
    }

    @Test
    void unGrupoNuloSeGuardaNulo() {
        LocalDate nacimiento = LocalDate.of(2017, 4, 10);
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(nacimiento, "Semillero", SEMILLERO, "tarde")));
        when(dao.actualizar(eq(ID), any())).thenReturn(true);

        servicio.actualizar(ID, edicion(nacimiento, null, true));

        ArgumentCaptor<Deportista> guardado = ArgumentCaptor.forClass(Deportista.class);
        verify(dao).actualizar(eq(ID), guardado.capture());
        assertThat(guardado.getValue().getGrupo()).isNull();
    }

    @Test
    void unDocumentoRepetidoAlEditarDa409() {
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null)));
        when(dao.actualizar(eq(ID), any())).thenThrow(new DatoDuplicadoException("documento", null));

        assertThatThrownBy(() -> servicio.actualizar(ID, edicion(LocalDate.of(2012, 4, 10), null, true)))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("documento: ya está registrado en otro deportista");
    }

    @Test
    void editarUnIdQueNoExisteDa404() {
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, edicion(LocalDate.of(2012, 4, 10), null, true)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(dao, never()).actualizar(any(), any());
    }

    @Test
    void elPutExigeActivo() {
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null)));

        assertThatThrownBy(() -> servicio.actualizar(ID, edicion(LocalDate.of(2012, 4, 10), null, null)))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("activo: es obligatorio");
    }

    // --- Lectura con categoría calculada --------------------------------------------------------------------

    @Test
    void consultarPideAlDaoElAnioDelReloj() {
        when(dao.buscarPorId(ID, 2026)).thenReturn(
                Optional.of(leido(LocalDate.of(2016, 8, 20), "Semillero", SEMILLERO, "tarde")));

        DeportistaResponseDTO r = servicio.consultar(ID);

        assertThat(r.categoria()).isEqualTo("Semillero");
        assertThat(r.idCategoria()).isEqualTo(SEMILLERO);
        assertThat(r.grupo()).isEqualTo("tarde");
        assertThat(r.activo()).isTrue();
    }

    @Test
    void elAnioSeTomaEnHoraDeColombia() {
        // 1 de enero de 2027 a las 03:00 UTC: en Bogotá todavía es 31 de diciembre de 2026
        servicio = new DeportistaServiceImpl(dao, almacenamiento, FOTOS, Clock.fixed(Instant.parse("2027-01-01T03:00:00Z"), BOGOTA));
        when(dao.buscarPorId(ID, 2026)).thenReturn(
                Optional.of(leido(LocalDate.of(2016, 8, 20), "Semillero", SEMILLERO, "tarde")));

        assertThat(servicio.consultar(ID).categoria()).isEqualTo("Semillero");
        verify(dao).buscarPorId(ID, 2026);
    }

    @Test
    void elGrupoSeDescartaSiLaCategoriaYaNoLoAdmite() {
        // Nacido en 2016 con grupo "tarde" guardado: en 2027 está en Sub-11 y el grupo no se devuelve
        servicio = new DeportistaServiceImpl(dao, almacenamiento, FOTOS, Clock.fixed(Instant.parse("2027-03-15T15:00:00Z"), BOGOTA));
        when(dao.buscarPorId(ID, 2027)).thenReturn(
                Optional.of(leido(LocalDate.of(2016, 8, 20), "Sub-11", SUB_11, "tarde")));

        DeportistaResponseDTO r = servicio.consultar(ID);

        assertThat(r.categoria()).isEqualTo("Sub-11");
        assertThat(r.idCategoria()).isEqualTo(SUB_11);
        assertThat(r.grupo()).isNull();
    }

    @Test
    void fueraDeRangoNoTieneCategoriaNiGrupoYNoEsError() {
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(LocalDate.of(2009, 5, 5), null, null, "tarde")));

        DeportistaResponseDTO r = servicio.consultar(ID);

        assertThat(r.categoria()).isNull();
        assertThat(r.idCategoria()).isNull();
        assertThat(r.grupo()).isNull();
    }

    @Test
    void elEntrenadorVeCategoriaYGrupoPeroNoDatosPrivadosNiEstadoInscripcion() {
        Deportista d = leido(LocalDate.of(2017, 1, 1), "Semillero", SEMILLERO, "mañana");
        d.setEstadoInscripcion("COMPLETA"); // aunque el DAO lo trajera, no se devuelve
        when(dao.listar(any(), eq(2026), eq(false))).thenReturn(List.of(d));

        DeportistaResponseDTO r = servicio.listar(null, null, null, null, null, false).content().getFirst();

        assertThat(r.documento()).isNull();
        assertThat(r.telefono()).isNull();
        assertThat(r.direccion()).isNull();
        assertThat(r.estadoInscripcion()).isNull();
        assertThat(r.tipoDocumento()).isEqualTo("TI");
        assertThat(r.categoria()).isEqualTo("Semillero");
        assertThat(r.grupo()).isEqualTo("mañana");
        verify(dao).listar(any(), eq(2026), eq(false));
    }

    @Test
    void elAdministradorPideYRecibeElEstadoInscripcion() {
        Deportista d = leido(LocalDate.of(2017, 1, 1), "Semillero", SEMILLERO, null);
        d.setEstadoInscripcion("PENDIENTE");
        when(dao.listar(any(), eq(2026), eq(true))).thenReturn(List.of(d));

        assertThat(servicio.listar(null, null, null, null, null, true).content().getFirst().estadoInscripcion())
                .isEqualTo("PENDIENTE");
    }

    // --- Parámetros de la lista -----------------------------------------------------------------------------

    @Test
    void sinParametrosUsaPagina0Tamano10YOrdenPorApellido() {
        when(dao.contar(any())).thenReturn(23L);

        PaginaDTO<DeportistaResponseDTO> pagina = servicio.listar(null, null, null, null, null, true);

        ArgumentCaptor<ConsultaDeportistas> consulta = ArgumentCaptor.forClass(ConsultaDeportistas.class);
        verify(dao).listar(consulta.capture(), eq(2026), eq(true));
        assertThat(consulta.getValue()).isEqualTo(
                new ConsultaDeportistas(null, null, null, OrdenDeportista.APELLIDO, false, 0, 10));
        assertThat(pagina.page()).isZero();
        assertThat(pagina.size()).isEqualTo(10);
        assertThat(pagina.totalElements()).isEqualTo(23);
        assertThat(pagina.totalPages()).isEqualTo(3);
    }

    @Test
    void pasaFiltrosOrdenYPaginaAlDao() {
        servicio.listar(2, 100, "fechaNacimiento,DESC", false, " Semillero ", true);

        // 29/10/2026: Semillero (9 y 10 años por año) = nacidos de 2016 a 2017
        verify(dao).listar(new ConsultaDeportistas(false, LocalDate.of(2016, 1, 1), LocalDate.of(2017, 12, 31),
                OrdenDeportista.FECHA_NACIMIENTO, true, 2, 100), 2026, true);
    }

    @Test
    void sinResultadosHayCeroPaginas() {
        PaginaDTO<DeportistaResponseDTO> pagina = servicio.listar(0, 10, null, null, null, true);

        assertThat(pagina.content()).isEmpty();
        assertThat(pagina.totalPages()).isZero();
    }

    @Test
    void parametrosInvalidosDan400ConTodosLosCampos() {
        assertThatThrownBy(() -> servicio.listar(-1, 101, "apellido; drop table deportista", null, "Sub-9", true))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("page: debe ser 0 o mayor")
                .hasMessageContaining("size: debe estar entre 1 y 100")
                .hasMessageContaining("sort: debe ser campo o campo,asc o campo,desc; campos: nombre, apellido, "
                        + "documento, fechaNacimiento, fechaRegistro")
                .hasMessageContaining("categoria: debe ser una de Párvulos, Cachorros, Semillero, Sub-11")
                .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos())
                        .containsExactly("categoria", "page", "size", "sort"));
        verifyNoInteractions(dao);
    }

    @Test
    void tamanoCeroYDireccionDesconocidaDan400() {
        assertThatThrownBy(() -> servicio.listar(0, 0, null, null, null, true))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("size");
        assertThatThrownBy(() -> servicio.listar(0, 10, "apellido,arriba", null, null, true))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("sort");
        assertThatThrownBy(() -> servicio.listar(0, 10, "apellido,asc,desc", null, null, true))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("sort");
        assertThatThrownBy(() -> servicio.listar(0, 10, null, null, "semillero", true))
                .as("el nombre de la categoría es exacto").isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("categoria");
        verifyNoInteractions(dao);
    }

    // --- El entrenador no ve el número de documento ---------------------------------------------------------

    @Test
    void elEntrenadorNoPuedeOrdenarPorDocumentoNiPorFechaDeRegistro() {
        for (String sort : List.of("documento", "documento,desc", "fechaRegistro")) {
            assertThatThrownBy(() -> servicio.listar(0, 10, sort, null, null, false)).as(sort)
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("sort: debe ser campo o campo,asc o campo,desc; campos: nombre, apellido, fechaNacimiento");
        }
        verifyNoInteractions(dao);
    }

    @Test
    void elEntrenadorOrdenaPorNombreApellidoOFechaDeNacimiento() {
        servicio.listar(0, 10, "nombre", null, null, false);
        servicio.listar(0, 10, "apellido,desc", null, null, false);
        servicio.listar(0, 10, "fechaNacimiento,asc", null, null, false);

        verify(dao, times(3)).listar(any(), eq(2026), eq(false));
    }

    @Test
    void elAdministradorSiOrdenaPorDocumentoYLoRecibe() {
        when(dao.listar(any(), eq(2026), eq(true))).thenReturn(List.of(leido(LocalDate.of(2012, 1, 1), "Sub-14", null, null)));

        assertThat(servicio.listar(0, 10, "documento", null, null, true).content().getFirst().documento())
                .isEqualTo("12345678");
    }

    // --- Foto -----------------------------------------------------------------------------------------------

    /** Firma JPG (FF D8 FF E0) seguida de relleno hasta {@code tamano} bytes. */
    private static byte[] jpg(int tamano) {
        byte[] b = new byte[tamano];
        b[0] = (byte) 0xFF; b[1] = (byte) 0xD8; b[2] = (byte) 0xFF; b[3] = (byte) 0xE0;
        return b;
    }

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] PDF_COMO_JPG = "%PDF-1.4 esto no es una imagen".getBytes();

    private Deportista conFoto(String ruta) {
        Deportista d = leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null);
        d.setFoto(ruta);
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(d));
        return d;
    }

    private String rutaSubida(String tipo) {
        ArgumentCaptor<String> ruta = ArgumentCaptor.forClass(String.class);
        verify(almacenamiento).subir(eq("fotos"), ruta.capture(), any(), eq(tipo), eq(IAlmacenamientoArchivos.IMAGENES));
        return ruta.getValue();
    }

    @Test
    void subeUnJpgConRutaPropiaYDevuelveLaUrlFirmada() {
        conFoto(null);
        when(dao.actualizarFoto(eq(ID), any())).thenReturn(true);
        when(almacenamiento.urlsFirmadas(eq("fotos"), any(), eq(VIGENCIA)))
                .thenAnswer(i -> Map.of(((List<?>) i.getArgument(1)).getFirst(), "https://firmada.example/x?token=t"));
        // consultar() después de guardar: el DAO ya devuelve la ruta nueva
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null)))
                .thenAnswer(i -> {
                    Deportista d = leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null);
                    d.setFoto(ID + "/nueva.jpg");
                    return Optional.of(d);
                });

        DeportistaResponseDTO r = servicio.subirFoto(ID, jpg(1024));

        String ruta = rutaSubida("image/jpeg");
        assertThat(ruta).matches(ID + "/[0-9a-f-]{36}\\.jpg");
        verify(dao).actualizarFoto(ID, ruta);
        assertThat(r.foto()).isEqualTo("https://firmada.example/x?token=t");
        verify(almacenamiento, never()).eliminar(any(), any());
    }

    @Test
    void subeUnPngComoImagePng() {
        conFoto(null);
        when(dao.actualizarFoto(eq(ID), any())).thenReturn(true);

        servicio.subirFoto(ID, PNG);

        assertThat(rutaSubida("image/png")).endsWith(".png");
    }

    @Test
    void unPdfConExtensionJpgDa400SinSubirNada() {
        conFoto(null);

        assertThatThrownBy(() -> servicio.subirFoto(ID, PDF_COMO_JPG))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("archivo: debe ser una imagen JPG o PNG (se revisa el contenido del archivo, no su extensión)");
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void exactamente5MbSeAcepta() {
        conFoto(null);
        when(dao.actualizarFoto(eq(ID), any())).thenReturn(true);

        servicio.subirFoto(ID, jpg(5 * 1024 * 1024));

        verify(almacenamiento).subir(eq("fotos"), any(), any(), eq("image/jpeg"), any());
    }

    @Test
    void cincoMbYUnByteDa400() {
        conFoto(null);

        assertThatThrownBy(() -> servicio.subirFoto(ID, jpg(5 * 1024 * 1024 + 1)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("archivo: pesa 5242881 bytes; el máximo es 5 MB (5242880 bytes)");
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void unArchivoVacioDa400() {
        conFoto(null);

        assertThatThrownBy(() -> servicio.subirFoto(ID, new byte[0]))
                .isInstanceOf(DatosInvalidosException.class).hasMessage("archivo: está vacío");
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void reemplazarSubeLaNuevaActualizaYDespuesBorraLaAnterior() {
        conFoto(ID + "/anterior.jpg");
        when(dao.actualizarFoto(eq(ID), any())).thenReturn(true);

        servicio.subirFoto(ID, jpg(100));

        var orden = inOrder(almacenamiento, dao);
        orden.verify(almacenamiento).subir(eq("fotos"), any(), any(), any(), any());
        orden.verify(dao).actualizarFoto(eq(ID), argThat(r -> r.endsWith(".jpg") && !r.equals(ID + "/anterior.jpg")));
        orden.verify(almacenamiento).eliminar("fotos", ID + "/anterior.jpg");
    }

    @Test
    void siFallaElBorradoDeLaAnteriorIgualRespondeBien() {
        conFoto(ID + "/anterior.jpg");
        when(dao.actualizarFoto(eq(ID), any())).thenReturn(true);
        doThrow(new AlmacenamientoNoDisponibleException("Storage caído", 503, null))
                .when(almacenamiento).eliminar("fotos", ID + "/anterior.jpg");

        DeportistaResponseDTO r = servicio.subirFoto(ID, jpg(100));

        assertThat(r.id()).isEqualTo(ID);
        verify(dao).actualizarFoto(eq(ID), argThat(ruta -> ruta.endsWith(".jpg")));
    }

    @Test
    void siNoSePuedeGuardarLaRutaSeBorraLaFotoRecienSubida() {
        conFoto(null);
        when(dao.actualizarFoto(eq(ID), any())).thenThrow(new org.springframework.dao.QueryTimeoutException("lenta"));

        assertThatThrownBy(() -> servicio.subirFoto(ID, jpg(100)))
                .isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
        String subida = rutaSubida("image/jpeg");
        verify(almacenamiento).eliminar("fotos", subida);
    }

    @Test
    void fotoDeUnDeportistaQueNoExisteDa404SinSubirNada() {
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.subirFoto(ID, jpg(100))).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.eliminarFoto(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void quitarLaFotoBorraElArchivoYDejaNull() {
        conFoto(ID + "/actual.png");
        when(dao.actualizarFoto(ID, null)).thenReturn(true);

        servicio.eliminarFoto(ID);

        var orden = inOrder(almacenamiento, dao);
        orden.verify(almacenamiento).eliminar("fotos", ID + "/actual.png");
        orden.verify(dao).actualizarFoto(ID, null);
    }

    @Test
    void siStorageFallaAlQuitarLaFotoNoCambiaLaBase() {
        conFoto(ID + "/actual.png");
        doThrow(new AlmacenamientoNoDisponibleException("Storage caído", 503, null))
                .when(almacenamiento).eliminar(any(), any());

        assertThatThrownBy(() -> servicio.eliminarFoto(ID)).isInstanceOf(AlmacenamientoNoDisponibleException.class);
        verify(dao, never()).actualizarFoto(any(), any());
    }

    @Test
    void conStorageCaidoLaListaRespondeConFotoNull() {
        Deportista d = leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null);
        d.setFoto(ID + "/actual.jpg");
        when(dao.listar(any(), eq(2026), eq(true))).thenReturn(List.of(d));
        when(almacenamiento.urlsFirmadas(any(), any(), any()))
                .thenThrow(new AlmacenamientoNoDisponibleException("Storage caído", 503, null));

        PaginaDTO<DeportistaResponseDTO> pagina = servicio.listar(null, null, null, null, null, true);

        assertThat(pagina.content()).hasSize(1);
        assertThat(pagina.content().getFirst().foto()).isNull();
    }

    @Test
    void laListaFirmaTodaLaPaginaEnUnaSolaLlamadaYElEntrenadorVeLaUrl() {
        Deportista a = leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null);
        a.setFoto("a/1.jpg");
        Deportista b = leido(LocalDate.of(2013, 4, 10), "Sub-13", null, null);
        b.setFoto("b/2.png");
        Deportista sinFoto = leido(LocalDate.of(2014, 4, 10), "Sub-12", null, null);
        when(dao.listar(any(), eq(2026), eq(false))).thenReturn(List.of(a, b, sinFoto));
        when(almacenamiento.urlsFirmadas("fotos", List.of("a/1.jpg", "b/2.png"), VIGENCIA))
                .thenReturn(Map.of("a/1.jpg", "https://firmada/a", "b/2.png", "https://firmada/b"));

        List<DeportistaResponseDTO> lista = servicio.listar(null, null, null, null, null, false).content();

        assertThat(lista).extracting(DeportistaResponseDTO::foto).containsExactly("https://firmada/a", "https://firmada/b", null);
        verify(almacenamiento, times(1)).urlsFirmadas(any(), any(), any());
        verify(almacenamiento, never()).urlFirmada(any(), any(), any());
    }

    /** El bucket y la vigencia salen de FotosProperties, no de constantes del servicio. */
    @Test
    void elBucketYLaVigenciaSalenDeLaConfiguracion() {
        var otraConfig = new FotosProperties("fotos-prueba", Duration.ofMinutes(15));
        servicio = new DeportistaServiceImpl(dao, almacenamiento, otraConfig, Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), BOGOTA));
        Deportista d = leido(LocalDate.of(2012, 4, 10), "Sub-14", null, null);
        d.setFoto(ID + "/anterior.jpg");
        when(dao.buscarPorId(ID, 2026)).thenReturn(Optional.of(d));
        when(dao.actualizarFoto(eq(ID), any())).thenReturn(true);

        servicio.subirFoto(ID, jpg(100));

        verify(almacenamiento).subir(eq("fotos-prueba"), any(), any(), eq("image/jpeg"), any());
        verify(almacenamiento).eliminar("fotos-prueba", ID + "/anterior.jpg");
        verify(almacenamiento).urlsFirmadas(eq("fotos-prueba"), any(), eq(Duration.ofMinutes(15)));
        verify(almacenamiento, never()).subir(eq("fotos"), any(), any(), any(), any());
    }

    @Test
    void laRespuestaNuncaTraeLaRutaDelBucket() {
        conFoto(ID + "/actual.jpg");
        when(almacenamiento.urlsFirmadas(any(), any(), any())).thenReturn(Map.of());

        assertThat(servicio.consultar(ID).foto()).isNull();
    }
}
