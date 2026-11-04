package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.api.dto.EntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.rules.ReglasEntrenador;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.FormatoColombiano;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** EntrenadorServiceImpl con dobles en memoria de los DAO: sin base de datos ni Spring. */
class EntrenadorServiceImplTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);
    private static final Clock RELOJ = Clock.fixed(
            ZonedDateTime.of(HOY.atTime(10, 0), FormatoColombiano.ZONA).toInstant(), FormatoColombiano.ZONA);

    private CategoriasFalsas categorias;
    private EntrenadoresFalsos entrenadores;
    private EntrenadorServiceImpl servicio;

    private UUID mora;
    private UUID diaz;

    @BeforeEach
    void prepararDatos() {
        categorias = new CategoriasFalsas();
        entrenadores = new EntrenadoresFalsos(categorias);
        servicio = new EntrenadorServiceImpl(entrenadores, categorias, cuentasQueFuncionan(), TransactionOperations.withoutTransaction(), RELOJ);
        // Los mismos datos de ejemplo del frontend (entrenadoresService.js)
        mora = entrenadores.agregar("Andrés", "Mora", "CC", "1017234567", "andres.mora@pradofc.co");
        diaz = entrenadores.agregar("Carlos", "Díaz", "CC", "71654321", "carlos.diaz@pradofc.co");
        // Las 9 del club, en el orden oficial; las que no reparte cada prueba quedan sin entrenador
        categorias.agregar("Párvulos", null);
        categorias.agregar("Cachorros", diaz);
        categorias.agregar("Semillero", diaz);
        categorias.agregar("Sub-11", null);
        categorias.agregar("Sub-12", mora);
        categorias.agregar("Sub-13", null);
        categorias.agregar("Sub-14", diaz);
        categorias.agregar("Sub-15", null);
        categorias.agregar("Sub-16", diaz);
    }

    /**
     * Cuentas de acceso con Supabase Auth, la tabla usuario y n8n simulados, todos respondiendo bien. Las pruebas de
     * la cuenta, el enlace y sus fallas están en {@link EntrenadorServiceImplCuentaTest}.
     */
    static CuentasDeAcceso cuentasQueFuncionan() {
        ISupabaseAuthAdmin auth = mock(ISupabaseAuthAdmin.class);
        when(auth.crearUsuario(anyString())).thenAnswer(i -> UUID.randomUUID());
        when(auth.generarTokenRecuperacion(anyString())).thenReturn("token-inventado");
        return new CuentasDeAcceso(auth, mock(IUsuarioDAO.class), mock(ISesionVerificadaDAO.class),
                mock(INotificador.class), new FrontendProperties("http://localhost:5173"));
    }

    static EntrenadorRequestDTO datos() {
        return new EntrenadorRequestDTO("Luis", "Pérez", "CC", "1098765432", LocalDate.of(1990, 5, 14),
                "3001112233", "luis.perez@pradofc.co", "Porteros", null, null);
    }

    static EntrenadorRequestDTO conDocumento(String tipo, String documento) {
        EntrenadorRequestDTO d = datos();
        return new EntrenadorRequestDTO(d.nombre(), d.apellido(), tipo, documento, d.fechaNacimiento(), d.telefono(),
                d.correo(), d.especialidad(), d.horarioTrabajo(), d.direccion());
    }

    static EntrenadorRequestDTO conCorreo(String correo) {
        EntrenadorRequestDTO d = datos();
        return new EntrenadorRequestDTO(d.nombre(), d.apellido(), d.tipoDocumento(), d.documento(), d.fechaNacimiento(),
                d.telefono(), correo, d.especialidad(), d.horarioTrabajo(), d.direccion());
    }

    static EntrenadorRequestDTO conFecha(LocalDate fecha) {
        EntrenadorRequestDTO d = datos();
        return new EntrenadorRequestDTO(d.nombre(), d.apellido(), d.tipoDocumento(), d.documento(), fecha,
                d.telefono(), d.correo(), d.especialidad(), d.horarioTrabajo(), d.direccion());
    }

    private static AsignarCategoriasRequestDTO pedir(Boolean reasignar, String... nombres) {
        return new AsignarCategoriasRequestDTO(Arrays.asList(nombres), reasignar);
    }

    @Nested
    class Consultas {

        @Test
        void listaLoQueDevuelveElDaoConSusCategorias() {
            List<EntrenadorResponseDTO> lista = servicio.listar();

            assertThat(lista).extracting(EntrenadorResponseDTO::apellido).containsExactly("Díaz", "Mora");
            assertThat(lista.get(0).categorias()).containsExactly("Cachorros", "Semillero", "Sub-14", "Sub-16");
            assertThat(lista.get(1).categorias()).containsExactly("Sub-12");
        }

        @Test
        void consultaUnoPorId() {
            EntrenadorResponseDTO andres = servicio.consultar(mora);

            assertThat(andres.id()).isEqualTo(mora);
            assertThat(andres.nombre()).isEqualTo("Andrés");
            assertThat(andres.tipoDocumento()).isEqualTo("CC");
            assertThat(andres.categorias()).containsExactly("Sub-12");
        }

        @Test
        void consultarUnIdQueNoExisteEs404() {
            assertThatThrownBy(() -> servicio.consultar(UUID.randomUUID()))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("No existe un entrenador con ese id.");
        }

        @Test
        void listaLasCategoriasConElResumenDeSuEntrenador() {
            List<CategoriaResponseDTO> lista = servicio.listarCategorias();

            assertThat(lista).extracting(CategoriaResponseDTO::nombre).containsExactlyElementsOf(ReglasEntrenador.CATEGORIAS);
            CategoriaResponseDTO sub12 = lista.get(ReglasEntrenador.CATEGORIAS.indexOf("Sub-12"));
            assertThat(sub12.edadMinima()).isEqualTo(12);
            assertThat(sub12.edadMaxima()).isEqualTo(12);
            assertThat(sub12.entrenador().id()).isEqualTo(mora);
            assertThat(sub12.entrenador().nombre()).isEqualTo("Andrés");
            assertThat(sub12.entrenador().apellido()).isEqualTo("Mora");
        }
    }

    @Nested
    class Crear {

        @Test
        void guardaLosDatosLimpiosYDevuelveElEntrenadorSinCategorias() {
            EntrenadorResponseDTO creado = servicio.crear(new EntrenadorRequestDTO("  Luis ", " Pérez  ", " cc ",
                    " 1098765432 ", LocalDate.of(1990, 5, 14), " 3001112233 ", " Luis.Perez@PradoFC.co ", " Porteros ",
                    "  ", " Calle 40 Sur  75b-81 "));

            assertThat(creado.id()).isNotNull();
            assertThat(creado).isEqualTo(new EntrenadorResponseDTO(creado.id(), "Luis", "Pérez", "CC", "1098765432",
                    LocalDate.of(1990, 5, 14), "3001112233", "luis.perez@pradofc.co", "Porteros", List.of(), null,
                    "Calle 40 Sur 75b-81", true, true));
            assertThat(entrenadores.filas).hasSize(3);
        }

        @Test
        void conDatosInvalidosResponde400ConTodosLosCamposYNoGuarda() {
            DatosInvalidosException e = catchThrowableOfType(DatosInvalidosException.class, () -> servicio.crear(
                    new EntrenadorRequestDTO(" ", "Pérez", "Cédula de ciudadanía", "12.345", null, "300", "luis@",
                            "", null, null)));

            assertThat(e.getCampos()).containsExactly("correo", "documento", "especialidad", "fechaNacimiento",
                    "nombre", "telefono", "tipoDocumento");
            assertThat(e.getMessage()).startsWith("correo: no es válido; documento: solo puede tener números")
                    .contains("fechaNacimiento: es obligatoria")
                    .doesNotContain("12.345", "luis@", "Cédula");
            assertThat(entrenadores.filas).hasSize(2);
        }

        @Test
        void unMenorDe18NoSePuedeRegistrar() {
            DatosInvalidosException e = catchThrowableOfType(DatosInvalidosException.class,
                    () -> servicio.crear(conFecha(HOY.minusYears(18).plusDays(1))));

            assertThat(e).hasMessage("fechaNacimiento: el entrenador debe tener al menos 18 años");
            assertThat(servicio.crear(conFecha(HOY.minusYears(18))).fechaNacimiento()).isEqualTo(HOY.minusYears(18));
        }

        @Test
        void unaFechaDeNacimientoFuturaNoSeAcepta() {
            assertThatThrownBy(() -> servicio.crear(conFecha(HOY.plusDays(1))))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("fechaNacimiento: no puede ser una fecha futura");
        }

        @Test
        void elMismoTipoYNumeroDeDocumentoDeOtroEs409PorDocumento() {
            ConflictoException e = catchThrowableOfType(ConflictoException.class,
                    () -> servicio.crear(conDocumento("CC", "1017234567")));

            assertThat(e.getCampos()).containsExactly("documento");
            assertThat(e).hasMessage("documento: ya está registrado en otro entrenador");
            assertThat(entrenadores.filas).hasSize(2);
        }

        @Test
        void elMismoNumeroConOtroTipoNoEsDuplicado() {
            assertThat(servicio.crear(conDocumento("PASAPORTE", "1017234567")).tipoDocumento()).isEqualTo("PASAPORTE");
        }

        @Test
        void unPasaporteSeGuardaEnMayusculasYEnMinusculasEsElMismoDocumento() {
            assertThat(servicio.crear(conDocumento("PASAPORTE", " ab123456 ")).documento()).isEqualTo("AB123456");

            EntrenadorRequestDTO otro = conDocumento("pasaporte", "Ab123456");
            EntrenadorRequestDTO conOtroCorreo = new EntrenadorRequestDTO(otro.nombre(), otro.apellido(),
                    otro.tipoDocumento(), otro.documento(), otro.fechaNacimiento(), otro.telefono(), "otro@pradofc.co",
                    otro.especialidad(), null, null);
            assertThat(catchThrowableOfType(ConflictoException.class, () -> servicio.crear(conOtroCorreo)).getCampos())
                    .containsExactly("documento");
        }

        @Test
        void unaTarjetaDeIdentidadNoSePuedeRegistrar() {
            assertThatThrownBy(() -> servicio.crear(conDocumento("TI", "1098765432")))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("tipoDocumento: TI no se acepta: la tarjeta de identidad es de menores de edad");
            assertThat(entrenadores.filas).hasSize(2);
        }

        @Test
        void elCorreoDeOtroSinDistinguirMayusculasEs409PorCorreo() {
            ConflictoException e = catchThrowableOfType(ConflictoException.class,
                    () -> servicio.crear(conCorreo("  CARLOS.DIAZ@PRADOFC.CO ")));

            assertThat(e.getCampos()).containsExactly("correo");
            assertThat(e).hasMessage("correo: ya está registrado en otro entrenador");
        }

        @Test
        void siSeRepitenDocumentoYCorreoInformaAmbosEmpezandoPorElDocumento() {
            EntrenadorRequestDTO d = conDocumento("CC", "71654321");
            EntrenadorRequestDTO ambos = new EntrenadorRequestDTO(d.nombre(), d.apellido(), d.tipoDocumento(),
                    d.documento(), d.fechaNacimiento(), d.telefono(), "andres.mora@pradofc.co", d.especialidad(), null, null);

            ConflictoException e = catchThrowableOfType(ConflictoException.class, () -> servicio.crear(ambos));

            assertThat(e.getCampos()).containsExactly("documento", "correo");
            assertThat(e).hasMessage("documento: ya está registrado en otro entrenador; "
                    + "correo: ya está registrado en otro entrenador");
        }

        @Test
        void elConflictoNoMuestraDatosDelOtroEntrenador() {
            ConflictoException e = catchThrowableOfType(ConflictoException.class,
                    () -> servicio.crear(conDocumento("CC", "71654321")));

            assertThat(e.getMessage()).doesNotContain("Carlos", "Díaz", "71654321", "carlos.diaz", diaz.toString());
        }

        @Test
        void siLaBaseRechazaPorUnicidadEnUnaCarreraEsElMismo409() {
            entrenadores.duplicadoAlEscribir = "correo";

            ConflictoException e = catchThrowableOfType(ConflictoException.class, () -> servicio.crear(datos()));

            assertThat(e.getCampos()).containsExactly("correo");
            assertThat(e).hasMessage("correo: ya está registrado en otro entrenador");
        }
    }

    @Nested
    class Editar {

        @Test
        void actualizaLosDatosPersonalesYConservaIdYCategorias() {
            EntrenadorResponseDTO editado = servicio.editar(mora, conCorreo("andres.felipe@pradofc.co"));

            assertThat(editado.id()).isEqualTo(mora);
            assertThat(editado.nombre()).isEqualTo("Luis");
            assertThat(editado.correo()).isEqualTo("andres.felipe@pradofc.co");
            assertThat(editado.categorias()).containsExactly("Sub-12");
            assertThat(entrenadores.filas).hasSize(2);
        }

        @Test
        void guardarSinCambiarNadaNoChocaConsigoMismo() {
            EntrenadorRequestDTO propios = new EntrenadorRequestDTO("Andrés", "Mora", "CC", "1017234567",
                    LocalDate.of(1990, 5, 14), "3105558821", "Andres.Mora@pradofc.co", "Formación técnica", null, null);

            assertThat(servicio.editar(mora, propios).documento()).isEqualTo("1017234567");
        }

        @Test
        void usarElDocumentoOElCorreoDeOtroEs409() {
            assertThat(catchThrowableOfType(ConflictoException.class,
                    () -> servicio.editar(mora, conDocumento("CC", "71654321"))).getCampos()).containsExactly("documento");
            assertThat(catchThrowableOfType(ConflictoException.class,
                    () -> servicio.editar(mora, conCorreo("Carlos.Diaz@pradofc.co"))).getCampos()).containsExactly("correo");
            assertThat(entrenadores.filas.get(mora).getDocumento()).isEqualTo("1017234567");
        }

        @Test
        void unIdQueNoExisteEs404AunqueLosDatosSeanInvalidos() {
            assertThatThrownBy(() -> servicio.editar(UUID.randomUUID(), conFecha(null)))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void validaIgualQueAlCrear() {
            assertThatThrownBy(() -> servicio.editar(mora, conDocumento("CC", "12345")))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("documento: debe tener entre 6 y 12 dígitos");
        }

        @Test
        void siLaBaseRechazaPorUnicidadEnUnaCarreraEsElMismo409() {
            entrenadores.duplicadoAlEscribir = "documento";

            assertThat(catchThrowableOfType(ConflictoException.class, () -> servicio.editar(mora, datos())).getCampos())
                    .containsExactly("documento");
        }
    }

    @Nested
    class AsignarCategorias {

        @Test
        void pedirLasMismasNoCambiaNada() {
            EntrenadorResponseDTO r = servicio.asignarCategorias(mora, pedir(null, "Sub-12"));

            assertThat(r.categorias()).containsExactly("Sub-12");
            assertThat(categorias.asignaciones).isEmpty();
            assertThat(categorias.bloqueadas).isTrue();
        }

        @Test
        void unaCategoriaDeOtroSinReasignarEs409YNoCambiaNada() {
            ConflictoException e = catchThrowableOfType(ConflictoException.class,
                    () -> servicio.asignarCategorias(mora, pedir(false, "Sub-14", "Sub-12", "Cachorros")));

            assertThat(e).hasMessage("Cachorros y Sub-14 ya tienen otro entrenador. "
                    + "Confirma la reasignación para pasarlas a este entrenador.");
            assertThat(e.getCampos()).containsExactly("categorias");
            assertThat(e.getMessage()).doesNotContain("Carlos", "Díaz");
            assertThat(categorias.asignaciones).isEmpty();
        }

        @Test
        void unaSolaCategoriaDeOtroUsaElSingular() {
            assertThatThrownBy(() -> servicio.asignarCategorias(mora, pedir(null, "Sub-12", "Semillero")))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessage("Semillero ya tiene otro entrenador. Confirma la reasignación para pasarla a este entrenador.");
        }

        @Test
        void conReasignarLasPasaDeSuEntrenadorAnteriorAEste() {
            EntrenadorResponseDTO r = servicio.asignarCategorias(mora, pedir(true, "Sub-16", "Sub-12", "Cachorros"));

            assertThat(r.categorias()).containsExactly("Cachorros", "Sub-12", "Sub-16");
            assertThat(servicio.consultar(diaz).categorias()).containsExactly("Semillero", "Sub-14");
            assertThat(categorias.asignaciones).hasSize(1);
        }

        @Test
        void quitarUnaCategoriaLaDejaSinEntrenador() {
            EntrenadorResponseDTO r = servicio.asignarCategorias(diaz, pedir(null, "Cachorros", "Sub-16"));

            assertThat(r.categorias()).containsExactly("Cachorros", "Sub-16");
            assertThat(categorias.listar()).filteredOn(c -> List.of("Semillero", "Sub-14").contains(c.getNombre()))
                    .extracting(Categoria::getIdEntrenador).containsOnlyNulls();
        }

        @Test
        void unaListaVaciaLeQuitaTodasSusCategorias() {
            assertThat(servicio.asignarCategorias(mora, pedir(null)).categorias()).isEmpty();
            assertThat(servicio.listarCategorias().get(ReglasEntrenador.CATEGORIAS.indexOf("Sub-12")).entrenador()).isNull();
        }

        @Test
        void unaListaVaciaParaQuienNoTieneCategoriasEsValida() {
            UUID nuevo = servicio.crear(datos()).id();

            assertThat(servicio.asignarCategorias(nuevo, pedir(null)).categorias()).isEmpty();
        }

        @Test
        void unaCategoriaSinEntrenadorSeAsignaSinPedirConfirmacion() {
            servicio.asignarCategorias(diaz, pedir(null, "Cachorros", "Semillero", "Sub-14"));

            EntrenadorResponseDTO r = servicio.asignarCategorias(mora, pedir(null, "Sub-12", "Sub-16"));

            assertThat(r.categorias()).containsExactly("Sub-12", "Sub-16");
        }

        @Test
        void siQuitaYTomaDeOtroAlMismoTiempoHaceLasDosCosas() {
            EntrenadorResponseDTO r = servicio.asignarCategorias(mora, pedir(true, "Cachorros"));

            assertThat(r.categorias()).containsExactly("Cachorros");
            assertThat(servicio.listarCategorias().get(ReglasEntrenador.CATEGORIAS.indexOf("Sub-12")).entrenador()).isNull();
            assertThat(servicio.consultar(diaz).categorias()).containsExactly("Semillero", "Sub-14", "Sub-16");
        }

        @Test
        void siLaReasignacionNoEstaConfirmadaTampocoQuitaNada() {
            assertThatThrownBy(() -> servicio.asignarCategorias(mora, pedir(null, "Cachorros")))
                    .isInstanceOf(ConflictoException.class);

            assertThat(servicio.consultar(mora).categorias()).containsExactly("Sub-12");
        }

        @Test
        void unEntrenadorInactivoNoRecibeCategorias() {
            servicio.cambiarEstado(mora, new CambiarEstadoEntrenadorRequestDTO(false));

            ConflictoException e = catchThrowableOfType(ConflictoException.class,
                    () -> servicio.asignarCategorias(mora, pedir(true, "Sub-12")));

            assertThat(e).hasMessage("El entrenador está inactivo. Actívalo antes de asignarle categorías.");
            assertThat(e.getCampos()).containsExactly("activo");
            assertThat(servicio.asignarCategorias(mora, pedir(null)).categorias()).isEmpty();
        }

        @Test
        void ignoraRepetidosYEspaciosEnLosNombres() {
            EntrenadorResponseDTO r = servicio.asignarCategorias(mora, pedir(true, " Sub-12 ", "Semillero", "Semillero"));

            assertThat(r.categorias()).containsExactly("Semillero", "Sub-12");
        }

        @Test
        void unNombreQueNoEsCategoriaDelClubEs400() {
            DatosInvalidosException e = catchThrowableOfType(DatosInvalidosException.class,
                    () -> servicio.asignarCategorias(mora, pedir(true, "Sub-12", "Sub-99", "sub-12")));

            assertThat(e).hasMessage("categorias: Sub-99 y sub-12 no son categorías del club "
                    + "(Párvulos, Cachorros, Semillero, Sub-11, Sub-12, Sub-13, Sub-14, Sub-15 o Sub-16)");
            assertThat(e.getCampos()).containsExactly("categorias");
        }

        @Test
        void unaCategoriaOficialQueNoEstaEnLaTablaEs400() {
            categorias.filas.removeIf(c -> c.getNombre().equals("Sub-16"));

            assertThatThrownBy(() -> servicio.asignarCategorias(mora, pedir(true, "Sub-12", "Sub-16")))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("categorias: Sub-16 todavía no está creada en la base de datos");
        }

        @Test
        void sinListaONombresVaciosEs400() {
            assertThatThrownBy(() -> servicio.asignarCategorias(mora, new AsignarCategoriasRequestDTO(null, true)))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("categorias: es obligatoria (puede ser una lista vacía)");
            assertThatThrownBy(() -> servicio.asignarCategorias(mora, pedir(true, "Sub-12", " ")))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("categorias: no puede tener nombres vacíos");
            assertThatThrownBy(() -> servicio.asignarCategorias(mora, pedir(true, "Sub-12", null)))
                    .isInstanceOf(DatosInvalidosException.class);
        }

        @Test
        void unEntrenadorQueNoExisteEs404() {
            assertThatThrownBy(() -> servicio.asignarCategorias(UUID.randomUUID(), pedir(true, "Sub-12")))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(categorias.asignaciones).isEmpty();
        }
    }

    @Nested
    class CambiarEstado {

        @Test
        void desactivarConservaSusDatosYDejaSinEntrenadorSusCategorias() {
            EntrenadorResponseDTO r = servicio.cambiarEstado(diaz, new CambiarEstadoEntrenadorRequestDTO(false));

            assertThat(r.activo()).isFalse();
            assertThat(r.categorias()).isEmpty();
            assertThat(r.apellido()).isEqualTo("Díaz");
            assertThat(categorias.bloqueadas).isTrue();
            assertThat(servicio.listarCategorias()).filteredOn(c -> c.entrenador() == null)
                    .extracting(CategoriaResponseDTO::nombre).containsExactly("Párvulos", "Cachorros", "Semillero",
                            "Sub-11", "Sub-13", "Sub-14", "Sub-15", "Sub-16");
            assertThat(servicio.consultar(mora).categorias()).containsExactly("Sub-12");
        }

        @Test
        void reactivarLoDejaActivoSinCategorias() {
            servicio.cambiarEstado(diaz, new CambiarEstadoEntrenadorRequestDTO(false));

            EntrenadorResponseDTO r = servicio.cambiarEstado(diaz, new CambiarEstadoEntrenadorRequestDTO(true));

            assertThat(r.activo()).isTrue();
            assertThat(r.categorias()).isEmpty();
        }

        @Test
        void pedirElEstadoQueYaTieneNoCambiaNada() {
            EntrenadorResponseDTO r = servicio.cambiarEstado(mora, new CambiarEstadoEntrenadorRequestDTO(true));

            assertThat(r.activo()).isTrue();
            assertThat(r.categorias()).containsExactly("Sub-12");
            assertThat(categorias.asignaciones).isEmpty();
        }

        @Test
        void sinActivoEs400() {
            assertThatThrownBy(() -> servicio.cambiarEstado(mora, new CambiarEstadoEntrenadorRequestDTO(null)))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("activo: es obligatorio (true o false)");
            assertThatThrownBy(() -> servicio.cambiarEstado(mora, null)).isInstanceOf(DatosInvalidosException.class);
        }

        @Test
        void unEntrenadorQueNoExisteEs404() {
            assertThatThrownBy(() -> servicio.cambiarEstado(UUID.randomUUID(), new CambiarEstadoEntrenadorRequestDTO(false)))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    /** Tabla categoria en memoria. Registra si se bloqueó y cada asignación. */
    static class CategoriasFalsas implements ICategoriaDAO {

        final List<Categoria> filas = new ArrayList<>();
        final List<List<UUID>> asignaciones = new ArrayList<>();
        boolean bloqueadas;

        void agregar(String nombre, UUID idEntrenador) {
            int minima = switch (nombre) {
                case "Párvulos" -> 4;
                case "Cachorros" -> 7;
                case "Semillero" -> 9;
                default -> Integer.parseInt(nombre.substring("Sub-".length()));
            };
            int maxima = switch (nombre) {
                case "Párvulos" -> 6;
                case "Cachorros" -> 8;
                case "Semillero" -> 10;
                default -> minima;
            };
            filas.add(new Categoria(UUID.randomUUID(), nombre, minima, maxima, idEntrenador));
        }

        @Override
        public List<Categoria> listarPorEntrenador(UUID idEntrenador) {
            return listar().stream().filter(c -> idEntrenador.equals(c.getIdEntrenador())).toList();
        }

        List<String> de(UUID idEntrenador) {
            return listar().stream().filter(c -> idEntrenador.equals(c.getIdEntrenador())).map(Categoria::getNombre).toList();
        }

        @Override
        public List<Categoria> listar() {
            return filas.stream().sorted(Comparator.comparingInt(Categoria::getEdadMinima)).map(c -> new Categoria(
                    c.getId(), c.getNombre(), c.getEdadMinima(), c.getEdadMaxima(), c.getIdEntrenador())).toList();
        }

        @Override
        public List<Categoria> listarParaActualizar() {
            bloqueadas = true;
            return listar();
        }

        @Override
        public void asignarEntrenador(Collection<UUID> ids, UUID idEntrenador) {
            if (ids.isEmpty()) {
                return;
            }
            asignaciones.add(List.copyOf(ids));
            filas.stream().filter(c -> ids.contains(c.getId())).forEach(c -> c.setIdEntrenador(idEntrenador));
        }
    }

    /** Tabla entrenador en memoria; las categorías salen de {@link CategoriasFalsas}. */
    static class EntrenadoresFalsos implements IEntrenadorDAO {

        final Map<UUID, Entrenador> filas = new LinkedHashMap<>();
        private final CategoriasFalsas categorias;
        /** Si no es null, la siguiente escritura falla como si la base rechazara ese campo por unicidad. */
        String duplicadoAlEscribir;

        EntrenadoresFalsos(CategoriasFalsas categorias) {
            this.categorias = categorias;
        }

        UUID agregar(String nombre, String apellido, String tipo, String documento, String correo) {
            Entrenador e = new Entrenador();
            e.setNombre(nombre);
            e.setApellido(apellido);
            e.setTipoDocumento(tipo);
            e.setDocumento(documento);
            e.setCorreo(correo);
            e.setFechaNacimiento(LocalDate.of(1985, 3, 2));
            e.setTelefono("3105558821");
            e.setEspecialidad("Formación técnica");
            return crear(e);
        }

        @Override
        public List<Entrenador> listar() {
            return filas.values().stream()
                    .sorted(Comparator.comparing(Entrenador::getApellido).thenComparing(Entrenador::getNombre))
                    .map(this::copia).toList();
        }

        @Override
        public Optional<Entrenador> buscarPorId(UUID id) {
            return Optional.ofNullable(filas.get(id)).map(this::copia);
        }

        @Override
        public Optional<Entrenador> buscarPorIdUsuario(UUID idUsuario) {
            return filas.values().stream().filter(e -> idUsuario.equals(e.getIdUsuario())).findFirst().map(this::copia);
        }

        @Override
        public boolean existeDocumento(String tipo, String documento, UUID excluirId) {
            return filas.values().stream().anyMatch(e -> !e.getId().equals(excluirId)
                    && e.getTipoDocumento().equals(tipo) && e.getDocumento().equals(documento));
        }

        @Override
        public boolean existeCorreo(String correo, UUID excluirId) {
            return filas.values().stream().anyMatch(e -> !e.getId().equals(excluirId) && e.getCorreo().equalsIgnoreCase(correo));
        }

        @Override
        public UUID crear(Entrenador e) {
            fallarSiHayCarrera();
            Entrenador fila = copia(e);
            fila.setId(UUID.randomUUID());
            filas.put(fila.getId(), fila);
            return fila.getId();
        }

        @Override
        public boolean actualizar(Entrenador e) {
            fallarSiHayCarrera();
            if (!filas.containsKey(e.getId())) {
                return false;
            }
            Entrenador nueva = copia(e);
            nueva.setIdUsuario(filas.get(e.getId()).getIdUsuario()); // como el DAO real: no toca id_usuario
            filas.put(e.getId(), nueva);
            return true;
        }

        @Override
        public boolean vincularUsuario(UUID id, UUID idUsuario) {
            Entrenador fila = filas.get(id);
            if (fila == null || fila.getIdUsuario() != null) {
                return false;
            }
            fila.setIdUsuario(idUsuario);
            return true;
        }

        @Override
        public boolean cambiarActivo(UUID id, boolean activo) {
            Entrenador fila = filas.get(id);
            if (fila == null) {
                return false;
            }
            fila.setActivo(activo);
            return true;
        }

        private void fallarSiHayCarrera() {
            if (duplicadoAlEscribir != null) {
                throw new DatoDuplicadoException(duplicadoAlEscribir, new RuntimeException("unique"));
            }
        }

        private Entrenador copia(Entrenador e) {
            Entrenador c = ReglasEntrenador.limpiar(e);
            c.setCorreo(e.getCorreo());
            c.setIdUsuario(e.getIdUsuario());
            c.setCategorias(e.getId() == null ? List.of() : categorias.de(e.getId()));
            return c;
        }
    }
}
