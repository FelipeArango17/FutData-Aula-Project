package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.domain.entities.Acudiente;
import co.edu.futdata.domain.entities.DatosAcudiente;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IAcudienteDAO;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.InscripcionEntrada;
import co.edu.futdata.services.facade.InscripcionResultado;
import co.edu.futdata.services.facade.SinPermisoException;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

class InscripcionServiceImplTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID DEPORTISTA = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID ACUDIENTE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private IDeportistaDAO deportistas;
    private IAcudienteDAO acudientes;
    private IUsuarioDAO usuarios;
    private CuentasDeAcceso cuentas;
    private TransactionOperations transaccion;
    private AtomicBoolean dentro;
    private InscripcionServiceImpl servicio;
    private final Solicitante admin = new Solicitante(ADMIN, Solicitante.ADMINISTRADOR);

    /** La cuenta de Auth que crea la inscripción cuando el correo no es de nadie (las ramas están en ...CuentaTest). */
    private static final UUID CUENTA = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @BeforeEach
    void preparar() {
        deportistas = mock(IDeportistaDAO.class);
        acudientes = mock(IAcudienteDAO.class);
        usuarios = mock(IUsuarioDAO.class);
        cuentas = mock(CuentasDeAcceso.class);
        transaccion = mock(TransactionOperations.class);
        dentro = new AtomicBoolean(false);
        servicio = new InscripcionServiceImpl(deportistas, acudientes, usuarios, cuentas, transaccion, RELOJ);
        when(cuentas.crearCuentaAuth(anyString())).thenReturn(CUENTA);

        when(transaccion.execute(any())).thenAnswer(i -> {
            dentro.set(true);
            try {
                return ((org.springframework.transaction.support.TransactionCallback<?>) i.getArgument(0)).doInTransaction(null);
            } finally {
                dentro.set(false);
            }
        });
        when(deportistas.crear(any())).thenReturn(DEPORTISTA);
        Deportista guardado = new Deportista();
        guardado.setId(DEPORTISTA);
        guardado.setCategoria("Semillero");
        guardado.setEstadoInscripcion("PENDIENTE");
        when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenReturn(Optional.of(guardado));
        when(acudientes.insertar(any(), any(), any(), any(), any())).thenReturn(nuevo());
    }

    // --- Autorización ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"ENTRENADOR", "TESORERO", "ACUDIENTE", "administrador", " ", "OTRO"})
    @NullAndEmptySource
    void soloElAdministradorPuedeInscribir(String rol) {
        assertThatThrownBy(() -> servicio.inscribir(entrada(), new Solicitante(ADMIN, rol)))
                .isInstanceOf(SinPermisoException.class);
        sinTocarNada();
    }

    @Test
    void unSolicitanteNuloEsUn403() {
        assertThatThrownBy(() -> servicio.inscribir(entrada(), null)).isInstanceOf(SinPermisoException.class);
        sinTocarNada();
    }

    @Test
    void laAutorizacionVaAntesQueLaValidacion() {
        assertThatThrownBy(() -> servicio.inscribir(null, new Solicitante(ADMIN, "ACUDIENTE")))
                .isInstanceOf(SinPermisoException.class);
    }

    // --- Validación ------------------------------------------------------------------------------------------

    @Test
    void sinCuerpoEsUn400() {
        assertThatThrownBy(() -> servicio.inscribir(null, admin)).isInstanceOf(DatosInvalidosException.class)
                .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos()).containsExactly("cuerpo"));
        sinTocarNada();
    }

    @Test
    void sinDeportistaNiAcudienteSalenTodosLosCamposObligatoriosJuntos() {
        DatosInvalidosException e = invalido(new InscripcionEntrada(null, null));

        assertThat(e.getCampos()).containsExactlyInAnyOrder(
                "acudiente.apellido", "acudiente.correo", "acudiente.nombre", "acudiente.parentesco",
                "acudiente.telefono", "deportista.apellido", "deportista.direccion", "deportista.documento",
                "deportista.fechaNacimiento", "deportista.nombre", "deportista.telefono", "deportista.tipoDocumento");
        sinTocarNada();
    }

    @Test
    void cadaErrorDelDeportistaLlevaElPrefijoDeportista() {
        DeportistaRequestDTO malo = new DeportistaRequestDTO("Ana", "Ríos", "CC", "12", LocalDate.of(2005, 1, 1),
                "123", "x", "Delantero", "tarde", true);

        DatosInvalidosException e = invalido(new InscripcionEntrada(malo, acudiente()));

        assertThat(e.getCampos()).containsExactlyInAnyOrder("deportista.tipoDocumento", "deportista.documento",
                "deportista.fechaNacimiento", "deportista.telefono", "deportista.direccion");
        assertThat(e.getMessage()).contains("deportista.fechaNacimiento: por año de nacimiento tiene 21 años");
    }

    @Test
    void elGrupoSoloValeEnCachorrosYSemillero() {
        DeportistaRequestDTO sub11 = new DeportistaRequestDTO("Ana", "Ríos", "TI", "1234567890", LocalDate.of(2015, 2, 2),
                "3001234567", "Calle 1 # 2-3", null, "tarde", true);

        assertThat(invalido(new InscripcionEntrada(sub11, acudiente())).getCampos()).containsExactly("deportista.grupo");
    }

    @Test
    void elNombreDelDeportistaNoPuedeSuperarLaColumna() {
        DeportistaRequestDTO largo = deportista("1234567890", "N".repeat(101));

        assertThat(invalido(new InscripcionEntrada(largo, acudiente())).getCampos()).containsExactly("deportista.nombre");
    }

    @Test
    void cadaErrorDelAcudienteLlevaElPrefijoAcudiente() {
        DatosAcudiente malo = new DatosAcudiente(" ", "A".repeat(101), "sin-arroba", "12345", "Primo");

        DatosInvalidosException e = invalido(new InscripcionEntrada(deportista("1234567890"), malo));

        assertThat(e.getCampos()).containsExactly("acudiente.apellido", "acudiente.correo", "acudiente.nombre",
                "acudiente.parentesco", "acudiente.telefono");
        assertThat(e.getMessage()).contains("acudiente.nombre: es obligatorio")
                .contains("acudiente.apellido: debe tener como máximo 100 caracteres")
                .contains("acudiente.correo: no es válido")
                .contains("acudiente.telefono: debe tener exactamente 10 dígitos")
                .contains("acudiente.parentesco: debe ser Madre, Padre, Abuelo, Abuela, Tia, Tio u Otro");
    }

    @Test
    void elCorreoNoPasaDe254Caracteres() {
        String correo = "a".repeat(250) + "@b.co"; // 255
        DatosAcudiente largo = new DatosAcudiente("Ana", "Ríos", correo, "3001234567", "Madre");

        DatosInvalidosException e = invalido(new InscripcionEntrada(deportista("1234567890"), largo));

        assertThat(e.getCampos()).containsExactly("acudiente.correo");
        assertThat(e.getMessage()).contains("máximo 254");
    }

    @Test
    void unCorreoDeExactamente254CaracteresSePermite() {
        String correo = "a".repeat(249) + "@b.co"; // 254
        when(acudientes.buscarPorCorreo(anyString())).thenReturn(Optional.empty());

        servicio.inscribir(new InscripcionEntrada(deportista("1234567890"),
                new DatosAcudiente("Ana", "Ríos", correo, "3001234567", "Madre")), admin);

        verify(acudientes).insertar("Ana", "Ríos", correo, "3001234567", CUENTA);
    }

    @Test
    void erroresDeDeportistaYAcudienteSalenJuntosYNoSeInsertaNada() {
        DeportistaRequestDTO malo = deportista("12");
        DatosAcudiente mal = new DatosAcudiente("Ana", "Ríos", "x", "3001234567", "Madre");

        DatosInvalidosException e = invalido(new InscripcionEntrada(malo, mal));

        assertThat(e.getCampos()).containsExactly("acudiente.correo", "deportista.documento");
        sinTocarNada();
    }

    @ParameterizedTest
    @CsvSource({"Tía,Tia", "Tia,Tia", "Tío,Tio", "Tio,Tio", "Madre,Madre", "Padre,Padre", "Abuelo,Abuelo",
            "Abuela,Abuela", "Otro,Otro"})
    void aceptaLosParentescosYNormalizaLasTildes(String parentesco, String esperado) {
        when(acudientes.buscarPorCorreo(anyString())).thenReturn(Optional.empty());

        InscripcionResultado r = servicio.inscribir(new InscripcionEntrada(deportista("1234567890"),
                new DatosAcudiente("Ana", "Ríos", "ana@correo.co", "3001234567", parentesco)), admin);

        assertThat(r.parentesco()).isEqualTo(esperado);
        verify(acudientes).vincular(any(), eq(DEPORTISTA), eq(esperado));
    }

    @Test
    void elParentescoEnMinusculasSeRechaza() {
        DatosAcudiente a = new DatosAcudiente("Ana", "Ríos", "ana@correo.co", "3001234567", "madre");

        assertThat(invalido(new InscripcionEntrada(deportista("1234567890"), a)).getCampos())
                .containsExactly("acudiente.parentesco");
    }

    // --- Duplicados ------------------------------------------------------------------------------------------

    @Test
    void unDocumentoRepetidoEsUn409AntesDeAbrirLaTransaccion() {
        when(deportistas.existeDocumento("1234567890")).thenReturn(true);

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .hasMessage("deportista.documento: ya está registrado en otro deportista")
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("deportista.documento"));
        verifyNoInteractions(transaccion, acudientes, usuarios, cuentas);
        verify(deportistas, never()).crear(any());
    }

    @Test
    void laCarreraDeDocumentoLaCubreElInsertYCompensaLaCuenta() {
        when(deportistas.crear(any())).thenThrow(new DatoDuplicadoException("documento", null));

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("deportista.documento"));
        verify(acudientes, never()).insertar(any(), any(), any(), any(), any());
        verify(acudientes, never()).vincular(any(), any(), any());
        verify(cuentas).intentarEliminarCuentaAuth(CUENTA);
        verify(cuentas, never()).enviarEnlace(any(), any(), any());
    }

    @Test
    void laCarreraDeCorreoEsUn409ConMensajeDeReintento() {
        when(acudientes.buscarPorCorreo(anyString())).thenReturn(Optional.empty());
        when(acudientes.insertar(any(), any(), any(), any(), any())).thenThrow(new DatoDuplicadoException("correo", null));

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("acudiente.correo").hasMessageContaining("vuelve a intentarlo")
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("acudiente.correo"));
        verify(acudientes, never()).vincular(any(), any(), any());
        verify(cuentas).intentarEliminarCuentaAuth(CUENTA);
    }

    // --- Acudiente nuevo y reutilizado -----------------------------------------------------------------------

    @Test
    void conUnAcudienteNuevoLoCreaConSuCuentaNuevaYLoVincula() {
        when(acudientes.buscarPorCorreo("ana@correo.co")).thenReturn(Optional.empty());

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verify(acudientes).insertar("Ana", "Ríos", "ana@correo.co", "3001234567", CUENTA);
        verify(acudientes).vincular(ACUDIENTE, DEPORTISTA, "Madre");
        assertThat(r.reutilizado()).isFalse();
        assertThat(r.cuentaCreada()).isTrue();
        assertThat(r.correoEnviado()).isTrue();
        assertThat(r.parentesco()).isEqualTo("Madre");
        assertThat(r.acudiente().id()).isEqualTo(ACUDIENTE);
        assertThat(r.deportista().getId()).isEqualTo(DEPORTISTA);
        assertThat(r.deportista().getCategoria()).isEqualTo("Semillero");
        assertThat(r.deportista().getEstadoInscripcion()).isEqualTo("PENDIENTE");
    }

    @Test
    void conUnAcudienteQueYaExisteLoReutilizaSinCambiarSusDatos() {
        Acudiente existente = new Acudiente(ACUDIENTE, "Nombre Viejo", "Apellido Viejo", "ana@correo.co", "3110000000",
                UUID.fromString("00000000-0000-0000-0000-0000000000c9"));
        when(acudientes.buscarPorCorreo("ana@correo.co")).thenReturn(Optional.of(existente));

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verify(acudientes, never()).insertar(any(), any(), any(), any(), any());
        verify(acudientes, never()).enlazarUsuario(any(), any());
        verify(acudientes).vincular(ACUDIENTE, DEPORTISTA, "Madre");
        assertThat(r.reutilizado()).isTrue();
        assertThat(r.acudiente()).isEqualTo(existente);
    }

    @Test
    void buscaAlAcudientePorElCorreoYaLimpio() {
        when(acudientes.buscarPorCorreo(anyString())).thenReturn(Optional.empty());

        servicio.inscribir(new InscripcionEntrada(deportista("1234567890"),
                new DatosAcudiente("  Ana ", " Ríos ", "  Ana@Correo.CO ", " 3001234567 ", " Madre ")), admin);

        verify(acudientes, org.mockito.Mockito.times(2)).buscarPorCorreo("ana@correo.co"); // antes y dentro de la transacción
        verify(usuarios).buscarPorCorreo("ana@correo.co");
        verify(cuentas).crearCuentaAuth("ana@correo.co");
        verify(acudientes).insertar("Ana", "Ríos", "ana@correo.co", "3001234567", CUENTA);
    }

    // --- Orden y todo o nada ---------------------------------------------------------------------------------

    @Test
    void deportistaAcudienteYVinculoVanEnEseOrdenDentroDeLaTransaccion() {
        when(acudientes.buscarPorCorreo(anyString())).thenReturn(Optional.empty());
        AtomicBoolean todoDentro = new AtomicBoolean(true);
        when(deportistas.crear(any())).thenAnswer(i -> {
            todoDentro.compareAndSet(true, dentro.get());
            return DEPORTISTA;
        });
        when(acudientes.insertar(any(), any(), any(), any(), any())).thenAnswer(i -> {
            todoDentro.compareAndSet(true, dentro.get());
            return nuevo();
        });
        when(acudientes.vincular(any(), any(), any())).thenAnswer(i -> {
            todoDentro.compareAndSet(true, dentro.get());
            return true;
        });

        servicio.inscribir(entrada(), admin);

        InOrder orden = inOrder(deportistas, acudientes, cuentas);
        orden.verify(deportistas).existeDocumento("1234567890");
        orden.verify(cuentas).crearCuentaAuth("ana@correo.co");
        orden.verify(deportistas).crear(any());
        orden.verify(cuentas).registrarUsuario(CUENTA, "Ana", "Ríos", "ana@correo.co", "ACUDIENTE");
        orden.verify(acudientes).insertar(any(), any(), any(), any(), any());
        orden.verify(acudientes).vincular(any(), any(), any());
        orden.verify(cuentas).enviarEnlace("ana@correo.co", "Ana", CuentasDeAcceso.PLANTILLA_BIENVENIDA_ACUDIENTE);
        assertThat(todoDentro).isTrue();
    }

    @Test
    void laRevisionDelDocumentoEsAntesDeLaTransaccion() {
        AtomicBoolean fueraDeLaTransaccion = new AtomicBoolean(false);
        when(deportistas.existeDocumento(anyString())).thenAnswer(i -> {
            fueraDeLaTransaccion.set(!dentro.get());
            return false;
        });

        servicio.inscribir(entrada(), admin);

        assertThat(fueraDeLaTransaccion).isTrue();
    }

    @Test
    void siFallaElVinculoLaExcepcionSaleYLaTransaccionLaDeshace() {
        when(acudientes.buscarPorCorreo(anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("falla")).when(acudientes).vincular(any(), any(), any());

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elDeportistaSeCreaConLosDatosLimpios() {
        servicio.inscribir(new InscripcionEntrada(
                new DeportistaRequestDTO("  Mateo ", " Ríos ", " ti ", " 1234567890 ", LocalDate.of(2016, 3, 3),
                        " 3001234567 ", " Calle 1 # 2-3 ", " ", " ", false),
                acudiente()), admin);

        org.mockito.ArgumentCaptor<Deportista> captor = org.mockito.ArgumentCaptor.forClass(Deportista.class);
        verify(deportistas).crear(captor.capture());
        Deportista d = captor.getValue();
        assertThat(d.getNombre()).isEqualTo("Mateo");
        assertThat(d.getTipoDocumento()).isEqualTo("TI");
        assertThat(d.getDocumento()).isEqualTo("1234567890");
        assertThat(d.getPosicion()).isNull();
        assertThat(d.getGrupo()).isNull();
        assertThat(d.isActivo()).isTrue(); // el activo del cuerpo se ignora
    }

    // --- Ayudas ----------------------------------------------------------------------------------------------

    private void sinTocarNada() {
        verifyNoInteractions(transaccion, acudientes, usuarios, cuentas);
        verify(deportistas, never()).crear(any());
        verify(deportistas, never()).existeDocumento(any());
    }

    private DatosInvalidosException invalido(InscripcionEntrada entrada) {
        try {
            servicio.inscribir(entrada, admin);
        } catch (DatosInvalidosException e) {
            return e;
        }
        throw new AssertionError("Debió lanzar DatosInvalidosException");
    }

    private static InscripcionEntrada entrada() {
        return new InscripcionEntrada(deportista("1234567890"), acudiente());
    }

    private static DeportistaRequestDTO deportista(String documento) {
        return deportista(documento, "Mateo");
    }

    private static DeportistaRequestDTO deportista(String documento, String nombre) {
        return new DeportistaRequestDTO(nombre, "Ríos", "TI", documento, LocalDate.of(2016, 3, 3), "3001234567",
                "Calle 1 # 2-3");
    }

    private static DatosAcudiente acudiente() {
        return new DatosAcudiente("Ana", "Ríos", "ana@correo.co", "3001234567", "Madre");
    }

    private static Acudiente nuevo() {
        return new Acudiente(ACUDIENTE, "Ana", "Ríos", "ana@correo.co", "3001234567", null);
    }

}
