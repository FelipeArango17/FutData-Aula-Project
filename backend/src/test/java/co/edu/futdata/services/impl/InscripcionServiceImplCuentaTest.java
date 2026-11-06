package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.datasource.external.AuthNoDisponibleException;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.datasource.external.NotificacionRechazadaException;
import co.edu.futdata.domain.entities.Acudiente;
import co.edu.futdata.domain.entities.DatosAcudiente;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IAcudienteDAO;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.InscripcionEntrada;
import co.edu.futdata.services.facade.InscripcionResultado;
import co.edu.futdata.services.facade.Solicitante;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/** Las ramas de la cuenta de acceso del acudiente en la inscripción única (paso 3 del 2B), con mocks. */
class InscripcionServiceImplCuentaTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final UUID DEPORTISTA = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID FICHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CUENTA_NUEVA = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CUENTA_EXISTENTE = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final String CORREO = "ana@correo.co";

    private IDeportistaDAO deportistas;
    private IAcudienteDAO acudientes;
    private IUsuarioDAO usuarios;
    private CuentasDeAcceso cuentas;
    private TransactionOperations transaccion;
    private InscripcionServiceImpl servicio;
    private ListAppender<ILoggingEvent> registros;
    private final Solicitante admin = new Solicitante(UUID.randomUUID(), Solicitante.ADMINISTRADOR);

    @BeforeEach
    void preparar() {
        deportistas = mock(IDeportistaDAO.class);
        acudientes = mock(IAcudienteDAO.class);
        usuarios = mock(IUsuarioDAO.class);
        cuentas = mock(CuentasDeAcceso.class);
        transaccion = mock(TransactionOperations.class);
        servicio = new InscripcionServiceImpl(deportistas, acudientes, usuarios, cuentas, transaccion, RELOJ);

        when(transaccion.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
        when(deportistas.crear(any())).thenReturn(DEPORTISTA);
        Deportista guardado = new Deportista();
        guardado.setId(DEPORTISTA);
        when(deportistas.buscarPorId(eq(DEPORTISTA), anyInt())).thenReturn(Optional.of(guardado));
        when(acudientes.insertar(any(), any(), any(), any(), any()))
                .thenAnswer(i -> new Acudiente(FICHA, i.getArgument(0), i.getArgument(1), i.getArgument(2),
                        i.getArgument(3), i.getArgument(4)));
        when(acudientes.vincular(any(), any(), any())).thenReturn(true);
        when(cuentas.crearCuentaAuth(anyString())).thenReturn(CUENTA_NUEVA);

        registros = new ListAppender<>();
        registros.start();
        ((Logger) LoggerFactory.getLogger(InscripcionServiceImpl.class)).addAppender(registros);
    }

    @AfterEach
    void quitarRegistros() {
        ((Logger) LoggerFactory.getLogger(InscripcionServiceImpl.class)).detachAppender(registros);
    }

    // --- 1. La ficha con ese correo ya tiene cuenta --------------------------------------------------------------

    @Test
    void siLaFichaYaTieneCuentaSeUsaEsaYNoSeCreaNiSeEnviaNada() {
        Acudiente ficha = ficha(CUENTA_EXISTENTE);
        when(acudientes.buscarPorCorreo(CORREO)).thenReturn(Optional.of(ficha));

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verifyNoInteractions(usuarios);
        verify(cuentas, never()).crearCuentaAuth(any());
        verify(cuentas, never()).registrarUsuario(any(), any(), any(), any(), any());
        verify(cuentas, never()).enviarEnlace(any(), any(), any());
        verify(acudientes, never()).enlazarUsuario(any(), any());
        verify(acudientes, never()).insertar(any(), any(), any(), any(), any());
        verify(acudientes).vincular(FICHA, DEPORTISTA, "Madre");
        assertThat(r.reutilizado()).isTrue();
        assertThat(r.cuentaCreada()).isFalse();
        assertThat(r.correoEnviado()).isNull();
        assertThat(r.acudiente().idUsuario()).isEqualTo(CUENTA_EXISTENTE);
    }

    // --- 2. Un usuario con ese correo ----------------------------------------------------------------------------

    @Test
    void unUsuarioAcudienteSinFichaReutilizaSuCuentaYCreaLaFichaEnlazada() {
        usuarioConRol(CUENTA_EXISTENTE, true);

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verify(cuentas, never()).crearCuentaAuth(any());
        verify(cuentas, never()).registrarUsuario(any(), any(), any(), any(), any());
        verify(cuentas, never()).enviarEnlace(any(), any(), any());
        verify(acudientes).insertar("Ana", "Ríos", CORREO, "3001234567", CUENTA_EXISTENTE);
        assertThat(r.reutilizado()).isFalse(); // la ficha es nueva
        assertThat(r.cuentaCreada()).isFalse();
        assertThat(r.correoEnviado()).isNull();
    }

    @Test
    void unUsuarioAcudienteCuyaFichaTieneOtroCorreoReutilizaEsaFicha() {
        usuarioConRol(CUENTA_EXISTENTE, true);
        Acudiente deLaCuenta = new Acudiente(FICHA, "Ana", "Ríos", "otro@correo.co", "3001234567", CUENTA_EXISTENTE);
        when(acudientes.buscarPorUsuario(CUENTA_EXISTENTE)).thenReturn(Optional.of(deLaCuenta));

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verify(acudientes, never()).insertar(any(), any(), any(), any(), any());
        verify(acudientes).vincular(FICHA, DEPORTISTA, "Madre");
        assertThat(r.reutilizado()).isTrue();
        assertThat(r.acudiente()).isEqualTo(deLaCuenta);
    }

    @Test
    void unUsuarioDeOtroRolEsUn409YNoSeCreaNada() {
        usuarioConRol(CUENTA_EXISTENTE, false);

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .hasMessage("acudiente.correo: ese correo ya pertenece a una cuenta con otro rol")
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("acudiente.correo"));

        verify(usuarios).tieneRol(CUENTA_EXISTENTE, "ACUDIENTE");
        verifyNoInteractions(transaccion);
        verify(cuentas, never()).crearCuentaAuth(any());
        verify(deportistas, never()).crear(any());
    }

    @Test
    void unaFichaSinCuentaYUnUsuarioAcudienteSeEnlazanSinCrearCuenta() {
        when(acudientes.buscarPorCorreo(CORREO)).thenReturn(Optional.of(ficha(null)));
        usuarioConRol(CUENTA_EXISTENTE, true);
        when(acudientes.enlazarUsuario(FICHA, CUENTA_EXISTENTE)).thenReturn(true);

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verify(acudientes).enlazarUsuario(FICHA, CUENTA_EXISTENTE);
        verify(cuentas, never()).crearCuentaAuth(any());
        assertThat(r.reutilizado()).isTrue();
        assertThat(r.cuentaCreada()).isFalse();
        assertThat(r.acudiente().idUsuario()).isEqualTo(CUENTA_EXISTENTE);
        assertThat(r.correoEnviado()).isNull();
    }

    // --- 3. Sin usuario: cuenta nueva en Auth --------------------------------------------------------------------

    @Test
    void sinUsuarioCreaLaCuentaRegistraUsuarioYRolYEnviaElEnlace() {
        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verify(cuentas).crearCuentaAuth(CORREO);
        verify(cuentas).registrarUsuario(CUENTA_NUEVA, "Ana", "Ríos", CORREO, "ACUDIENTE");
        verify(acudientes).insertar("Ana", "Ríos", CORREO, "3001234567", CUENTA_NUEVA);
        verify(cuentas).enviarEnlace(CORREO, "Ana", "BIENVENIDA_ACUDIENTE");
        verify(cuentas, never()).intentarEliminarCuentaAuth(any());
        assertThat(r.cuentaCreada()).isTrue();
        assertThat(r.reutilizado()).isFalse();
        assertThat(r.correoEnviado()).isTrue();
        assertThat(r.acudiente().idUsuario()).isEqualTo(CUENTA_NUEVA);
    }

    @Test
    void unaFichaSinCuentaYSinUsuarioRecibeUnaCuentaNuevaEnlazada() {
        when(acudientes.buscarPorCorreo(CORREO)).thenReturn(Optional.of(ficha(null)));
        when(acudientes.enlazarUsuario(FICHA, CUENTA_NUEVA)).thenReturn(true);

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        verify(cuentas).registrarUsuario(CUENTA_NUEVA, "Ana", "Ríos", CORREO, "ACUDIENTE");
        verify(acudientes).enlazarUsuario(FICHA, CUENTA_NUEVA);
        verify(acudientes, never()).insertar(any(), any(), any(), any(), any());
        assertThat(r.reutilizado()).isTrue();
        assertThat(r.cuentaCreada()).isTrue();
        assertThat(r.correoEnviado()).isTrue();
        assertThat(r.acudiente().idUsuario()).isEqualTo(CUENTA_NUEVA);
    }

    @Test
    void siAuthNoRespondeEsUn503YNoSeGuardaNada() {
        when(cuentas.crearCuentaAuth(anyString())).thenThrow(new AuthNoDisponibleException("caído", 500));

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(AuthNoDisponibleException.class);

        verifyNoInteractions(transaccion);
        verify(deportistas, never()).crear(any());
        verify(cuentas, never()).intentarEliminarCuentaAuth(any());
    }

    @Test
    void siAuthYaTieneElCorreoEsUn409DeReintento() {
        when(cuentas.crearCuentaAuth(anyString())).thenThrow(new ConflictoException("Ese correo ya pertenece a otra cuenta",
                List.of("correo")));

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("vuelve a intentarlo")
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("acudiente.correo"));
        verifyNoInteractions(transaccion);
    }

    // --- Carreras dentro de la transacción: 409 de reintento y compensación ------------------------------------

    @Test
    void siOtroUsuarioConEseCorreoAparecioEntretantoEsUn409YSeCompensa() {
        doThrow(new ConflictoException("Ese correo ya pertenece a otra cuenta", List.of("correo")))
                .when(cuentas).registrarUsuario(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("acudiente.correo"));
        verify(cuentas).intentarEliminarCuentaAuth(CUENTA_NUEVA);
        verify(cuentas, never()).enviarEnlace(any(), any(), any());
    }

    @Test
    void siElEnlaceDeLaFichaNoSePudoHacerEsUn409YSeCompensa() {
        when(acudientes.buscarPorCorreo(CORREO)).thenReturn(Optional.of(ficha(null)));
        when(acudientes.enlazarUsuario(FICHA, CUENTA_NUEVA)).thenReturn(false);

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("vuelve a intentarlo");
        verify(acudientes, never()).vincular(any(), any(), any());
        verify(cuentas).intentarEliminarCuentaAuth(CUENTA_NUEVA);
    }

    @Test
    void siLaCuentaYaEsDeOtraFichaAlEnlazarEsUn409() {
        when(acudientes.buscarPorCorreo(CORREO)).thenReturn(Optional.of(ficha(null)));
        usuarioConRol(CUENTA_EXISTENTE, true);
        when(acudientes.enlazarUsuario(FICHA, CUENTA_EXISTENTE)).thenThrow(new DatoDuplicadoException("cuenta", null));

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(ConflictoException.class)
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("acudiente.correo"));
        // la cuenta no la creó esta inscripción: no se borra
        verify(cuentas, never()).intentarEliminarCuentaAuth(any());
    }

    // --- Compensación ---------------------------------------------------------------------------------------------

    @Test
    void siLaTransaccionFallaConCuentaNuevaSeBorraLaCuentaYSaleLaExcepcionOriginal() {
        IllegalStateException original = new IllegalStateException("la base falló");
        doThrow(original).when(acudientes).vincular(any(), any(), any());

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isSameAs(original);
        verify(cuentas).intentarEliminarCuentaAuth(CUENTA_NUEVA);
        verify(cuentas, never()).enviarEnlace(any(), any(), any());
        assertThat(registros.list).isEmpty();
    }

    @Test
    void siElBorradoDeLaCuentaTambienFallaNoOcultaLaExcepcionOriginalYSoloRegistraElTipo() {
        IllegalStateException original = new IllegalStateException("la base falló");
        doThrow(original).when(acudientes).vincular(any(), any(), any());
        when(cuentas.intentarEliminarCuentaAuth(CUENTA_NUEVA)).thenReturn(Optional.of("AuthNoDisponibleException"));

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isSameAs(original);

        assertThat(registros.list).hasSize(1);
        String mensaje = registros.list.get(0).getFormattedMessage();
        assertThat(mensaje).contains("AuthNoDisponibleException").contains("borrarla a mano");
        sinDatosPersonales(mensaje);
    }

    @Test
    void conUnaCuentaReutilizadaNoSeCompensaNada() {
        usuarioConRol(CUENTA_EXISTENTE, true);
        doThrow(new IllegalStateException("la base falló")).when(acudientes).vincular(any(), any(), any());

        assertThatThrownBy(() -> servicio.inscribir(entrada(), admin)).isInstanceOf(IllegalStateException.class);
        verify(cuentas, never()).intentarEliminarCuentaAuth(any());
    }

    // --- Envío del enlace -----------------------------------------------------------------------------------------

    static List<Supplier<RuntimeException>> fallosDelEnvio() {
        return List.of(
                () -> new NotificacionRechazadaException("ENLACE_CONTRASENA", "plantilla debe ser una de: BIENVENIDA_ENTRENADOR"),
                () -> new NotificacionNoEnviadaException("n8n no responde", "ENLACE_CONTRASENA", null, null),
                () -> new AuthNoDisponibleException("no generó el enlace", 500),
                () -> new IllegalStateException("FRONTEND_URL no está configurada"));
    }

    @ParameterizedTest
    @MethodSource("fallosDelEnvio")
    void siElEnlaceFallaLaInscripcionQuedaYCorreoEnviadoEsFalso(Supplier<RuntimeException> falla) {
        RuntimeException e = falla.get();
        doThrow(e).when(cuentas).enviarEnlace(any(), any(), any());

        InscripcionResultado r = servicio.inscribir(entrada(), admin);

        assertThat(r.correoEnviado()).isFalse();
        assertThat(r.cuentaCreada()).isTrue();
        verify(acudientes).vincular(FICHA, DEPORTISTA, "Madre");
        verify(cuentas, never()).intentarEliminarCuentaAuth(any()); // nunca se deshace la inscripción
        assertThat(registros.list).hasSize(1);
        String mensaje = registros.list.get(0).getFormattedMessage();
        assertThat(mensaje).contains(e.getClass().getSimpleName());
        sinDatosPersonales(mensaje);
    }

    @Test
    void elEnlaceSeEnviaDespuesDelCommit() {
        java.util.concurrent.atomic.AtomicBoolean dentro = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean enviadoDentro = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.mockito.Mockito.doAnswer(i -> {
            dentro.set(true);
            try {
                return ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null);
            } finally {
                dentro.set(false);
            }
        }).when(transaccion).execute(any());
        org.mockito.Mockito.doAnswer(i -> {
            enviadoDentro.set(dentro.get());
            return null;
        }).when(cuentas).enviarEnlace(any(), any(), any());

        servicio.inscribir(entrada(), admin);

        verify(cuentas).enviarEnlace(any(), any(), any());
        assertThat(enviadoDentro).isFalse();
    }

    // --- Ayudas ---------------------------------------------------------------------------------------------------

    private void usuarioConRol(UUID id, boolean acudiente) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setCorreo(CORREO);
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(u));
        when(usuarios.tieneRol(id, "ACUDIENTE")).thenReturn(acudiente);
    }

    private static void sinDatosPersonales(String mensaje) {
        assertThat(mensaje).doesNotContain(CORREO).doesNotContain("Ana").doesNotContain("Ríos")
                .doesNotContain(CUENTA_NUEVA.toString()).doesNotContain("1234567890");
    }

    private static Acudiente ficha(UUID cuenta) {
        return new Acudiente(FICHA, "Ana", "Ríos", CORREO, "3001234567", cuenta);
    }

    private static InscripcionEntrada entrada() {
        return new InscripcionEntrada(new DeportistaRequestDTO("Mateo", "Ríos", "TI", "1234567890",
                LocalDate.of(2016, 3, 3), "3001234567", "Calle 1 # 2-3"),
                new DatosAcudiente("Ana", "Ríos", CORREO, "3001234567", "Madre"));
    }
}
