package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.EntrenadorResponseDTO;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.AuthNoDisponibleException;
import co.edu.futdata.datasource.external.AuthPeticionInvalidaException;
import co.edu.futdata.datasource.external.CorreoYaRegistradoException;
import co.edu.futdata.datasource.external.CuentaAuthNoEncontradaException;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DemasiadasSolicitudesException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.FormatoColombiano;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Alta con cuenta de acceso, reenvío del enlace y desactivación con baneo (regla 9). Supabase Auth, n8n y la tabla
 * usuario son simulados; los entrenadores y categorías, los dobles en memoria de {@link EntrenadorServiceImplTest}.
 */
class EntrenadorServiceImplCuentaTest {

    private static final String TOKEN = "token-inventado-f00d";
    private static final String FRONTEND = "http://localhost:5173";

    private EntrenadorServiceImplTest.CategoriasFalsas categorias;
    private EntrenadorServiceImplTest.EntrenadoresFalsos entrenadores;
    private ISupabaseAuthAdmin auth;
    private IUsuarioDAO usuarios;
    private ISesionVerificadaDAO sesiones;
    private INotificador notificador;
    private RelojMovible reloj;
    private TransaccionAnotada transaccion;
    private final List<String> eventos = new ArrayList<>();
    private EntrenadorServiceImpl servicio;
    private ListAppender<ILoggingEvent> registros;

    @BeforeEach
    void preparar() {
        categorias = new EntrenadorServiceImplTest.CategoriasFalsas();
        entrenadores = new EntrenadorServiceImplTest.EntrenadoresFalsos(categorias);
        auth = mock(ISupabaseAuthAdmin.class);
        usuarios = mock(IUsuarioDAO.class);
        sesiones = mock(ISesionVerificadaDAO.class);
        notificador = mock(INotificador.class);
        reloj = new RelojMovible(ZonedDateTime.of(2026, 10, 29, 10, 0, 0, 0, FormatoColombiano.ZONA).toInstant());
        transaccion = new TransaccionAnotada(eventos);

        when(auth.crearUsuario(anyString())).thenAnswer(i -> {
            eventos.add("auth.crearUsuario");
            return UUID.randomUUID();
        });
        when(auth.generarTokenRecuperacion(anyString())).thenAnswer(i -> {
            eventos.add("auth.generarToken");
            return TOKEN;
        });
        doAnswer(i -> eventos.add("n8n")).when(notificador).notificar(anyString(), anyMap());
        doAnswer(i -> {
            eventos.add("usuario.crear");
            return i.<Usuario>getArgument(0).getId();
        }).when(usuarios).crear(any());

        servicio = servicio(new FrontendProperties(FRONTEND));

        registros = new ListAppender<>();
        registros.start();
        raiz().addAppender(registros);
        ((Logger) LoggerFactory.getLogger("co.edu.futdata")).setLevel(Level.TRACE);
    }

    @AfterEach
    void sinSecretosEnLogs() {
        raiz().detachAppender(registros);
        assertThat(registros.list).allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                .doesNotContain(TOKEN).doesNotContain("crear-contrasena?token"));
    }

    private EntrenadorServiceImpl servicio(FrontendProperties frontend) {
        CuentasDeAcceso cuentas = new CuentasDeAcceso(auth, usuarios, sesiones, notificador, frontend);
        return new EntrenadorServiceImpl(entrenadores, categorias, cuentas, transaccion, reloj);
    }

    private static Logger raiz() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private UUID entrenadorSinCuenta() {
        return entrenadores.agregar("Andrés", "Mora", "CC", "1017234567", "andres.mora@pradofc.co");
    }

    private UUID entrenadorConCuenta(UUID idUsuario, String correoDeLaCuenta) {
        UUID id = entrenadorSinCuenta();
        entrenadores.vincularUsuario(id, idUsuario);
        Usuario u = new Usuario();
        u.setId(idUsuario);
        u.setCorreo(correoDeLaCuenta);
        when(usuarios.buscarPorId(idUsuario)).thenReturn(Optional.of(u));
        return id;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> datosEnviados() {
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificador).notificar(eq(INotificador.ENLACE_CONTRASENA), datos.capture());
        return datos.getValue();
    }

    @Nested
    class Alta {

        @Test
        void creaLaCuentaAntesYEnviaElEnlaceDespuesDelCommit() {
            EntrenadorResponseDTO creado = servicio.crear(EntrenadorServiceImplTest.datos());

            assertThat(creado.correoEnviado()).isTrue();
            assertThat(eventos).containsExactly("auth.crearUsuario", "inicio", "usuario.crear", "commit",
                    "auth.generarToken", "n8n");
            Entrenador guardado = entrenadores.filas.get(creado.id());
            assertThat(guardado.getIdUsuario()).isNotNull();
            verify(auth).crearUsuario("luis.perez@pradofc.co");
            verify(usuarios).asignarRol(guardado.getIdUsuario(), "ENTRENADOR");
            assertThat(datosEnviados()).containsOnly(
                    Map.entry("plantilla", "BIENVENIDA_ENTRENADOR"),
                    Map.entry("correo", "luis.perez@pradofc.co"),
                    Map.entry("nombre", "Luis"),
                    Map.entry("enlace", FRONTEND + "/crear-contrasena?token=" + TOKEN),
                    Map.entry("vigenciaHoras", 1));
        }

        @Test
        void laRespuestaNoTraeElEnlace() {
            EntrenadorResponseDTO creado = servicio.crear(EntrenadorServiceImplTest.datos());
            assertThat(creado.toString()).doesNotContain(TOKEN);
        }

        @Test
        void conElCorreoDeOtraCuentaResponde409SinTocarAuth() {
            when(usuarios.existeCorreo("luis.perez@pradofc.co")).thenReturn(true);

            ConflictoException e = catchThrowableOfType(ConflictoException.class, () -> servicio.crear(EntrenadorServiceImplTest.datos()));

            assertThat(e).hasMessage("Ese correo ya pertenece a otra cuenta");
            assertThat(e.getCampos()).containsExactly("correo");
            verifyNoInteractions(auth, notificador);
            assertThat(entrenadores.filas).isEmpty();
        }

        @Test
        void siAuthYaTieneElCorreoResponde409YNoGuarda() {
            when(auth.crearUsuario(anyString())).thenThrow(new CorreoYaRegistradoException(422));

            assertThatThrownBy(() -> servicio.crear(EntrenadorServiceImplTest.datos()))
                    .isInstanceOf(ConflictoException.class).hasMessage("Ese correo ya pertenece a otra cuenta");
            assertThat(entrenadores.filas).isEmpty();
            verify(auth, never()).eliminar(any());
        }

        @Test
        void siAuthNoRespondeNoGuardaNada() {
            when(auth.crearUsuario(anyString())).thenThrow(new AuthNoDisponibleException("caído", 503));

            assertThatThrownBy(() -> servicio.crear(EntrenadorServiceImplTest.datos())).isInstanceOf(AuthNoDisponibleException.class);
            assertThat(entrenadores.filas).isEmpty();
            verifyNoInteractions(notificador);
        }

        @Test
        void siLaBaseFallaTrasCrearLaCuentaLaBorraDeAuthYNoEnvia() {
            DataAccessResourceFailureException caida = new DataAccessResourceFailureException("sin conexión");
            doThrow(caida).when(usuarios).asignarRol(any(), anyString());

            assertThatThrownBy(() -> servicio.crear(EntrenadorServiceImplTest.datos())).isSameAs(caida);

            ArgumentCaptor<UUID> creada = ArgumentCaptor.forClass(UUID.class);
            verify(usuarios).crear(any());
            verify(auth).eliminar(creada.capture());
            assertThat(eventos).containsSubsequence("auth.crearUsuario", "inicio", "rollback");
            verify(auth, never()).generarTokenRecuperacion(anyString());
            verifyNoInteractions(notificador);
        }

        @Test
        void unCorreoRepetidoEnUsuarioPorCarreraEs409YTambienBorraLaCuenta() {
            doThrow(new DuplicateKeyException("usuario_correo_key")).when(usuarios).crear(any());

            assertThatThrownBy(() -> servicio.crear(EntrenadorServiceImplTest.datos()))
                    .isInstanceOf(ConflictoException.class).hasMessage("Ese correo ya pertenece a otra cuenta");
            verify(auth).eliminar(any());
        }

        @Test
        void siBorrarLaCuentaTambienFallaSeRespondeLaFallaOriginal() {
            DataAccessResourceFailureException caida = new DataAccessResourceFailureException("sin conexión");
            doThrow(caida).when(usuarios).asignarRol(any(), anyString());
            doThrow(new AuthNoDisponibleException("caído", 503)).when(auth).eliminar(any());

            assertThatThrownBy(() -> servicio.crear(EntrenadorServiceImplTest.datos())).isSameAs(caida);
            assertThat(registros.list).anyMatch(e -> e.getLevel() == Level.ERROR
                    && e.getFormattedMessage().contains("hay que borrarla a mano"));
        }

        @Test
        void siN8nFallaElAltaQuedaConCorreoEnviadoFalseYElReenvioEsperaElMinuto() {
            doThrow(new NotificacionNoEnviadaException("n8n respondió 502", INotificador.ENLACE_CONTRASENA, 502, null))
                    .when(notificador).notificar(anyString(), anyMap());

            EntrenadorResponseDTO creado = servicio.crear(EntrenadorServiceImplTest.datos());

            assertThat(creado.correoEnviado()).isFalse();
            assertThat(entrenadores.filas.get(creado.id()).getIdUsuario()).isNotNull();
            verify(auth, never()).eliminar(any());
            // El enlace se generó (y pudo llegar): el alta cuenta como un envío
            assertThatThrownBy(() -> servicio.reenviarEnlace(creado.id())).isInstanceOf(DemasiadasSolicitudesException.class);
        }

        @Test
        void siFallaGenerarElEnlaceElAltaQuedaConCorreoEnviadoFalseYSePuedeReenviarEnseguida() {
            when(auth.generarTokenRecuperacion(anyString()))
                    .thenThrow(new AuthNoDisponibleException("caído", 503))
                    .thenReturn(TOKEN);

            EntrenadorResponseDTO creado = servicio.crear(EntrenadorServiceImplTest.datos());

            assertThat(creado.correoEnviado()).isFalse();
            assertThat(entrenadores.filas).containsKey(creado.id());
            verifyNoInteractions(notificador);
            verify(auth, never()).eliminar(any());

            Usuario cuenta = new Usuario();
            cuenta.setCorreo("luis.perez@pradofc.co");
            when(usuarios.buscarPorId(entrenadores.filas.get(creado.id()).getIdUsuario())).thenReturn(Optional.of(cuenta));
            servicio.reenviarEnlace(creado.id());
            verify(notificador).notificar(eq(INotificador.ENLACE_CONTRASENA), anyMap());
        }

        @Test
        void sinFrontendUrlNoGeneraEnlaceYQuedaConCorreoEnviadoFalse() {
            EntrenadorResponseDTO creado = servicio(new FrontendProperties(null)).crear(EntrenadorServiceImplTest.datos());

            assertThat(creado.correoEnviado()).isFalse();
            verify(auth, never()).generarTokenRecuperacion(anyString());
            verifyNoInteractions(notificador);
        }

        @Test
        void lasOtrasRespuestasNoTraenCorreoEnviado() {
            EntrenadorResponseDTO creado = servicio.crear(EntrenadorServiceImplTest.datos());
            assertThat(servicio.consultar(creado.id()).correoEnviado()).isNull();
        }
    }

    @Nested
    class Reenvio {

        @Test
        void sinCuentaLaCreaLaVinculaYEnvia() {
            UUID id = entrenadorSinCuenta();
            eventos.clear();

            servicio.reenviarEnlace(id);

            assertThat(eventos).containsExactly("auth.crearUsuario", "inicio", "usuario.crear", "commit",
                    "auth.generarToken", "n8n");
            assertThat(entrenadores.filas.get(id).getIdUsuario()).isNotNull();
            verify(usuarios).asignarRol(entrenadores.filas.get(id).getIdUsuario(), "ENTRENADOR");
            assertThat(datosEnviados()).containsEntry("correo", "andres.mora@pradofc.co").containsEntry("nombre", "Andrés");
        }

        @Test
        void conCuentaGeneraUnEnlaceNuevoParaElCorreoDeLaCuenta() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "cuenta.mora@pradofc.co");

            servicio.reenviarEnlace(id);

            verify(auth, never()).crearUsuario(anyString());
            verify(auth).generarTokenRecuperacion("cuenta.mora@pradofc.co");
            assertThat(datosEnviados()).containsEntry("correo", "cuenta.mora@pradofc.co");
        }

        @Test
        void siNoExisteResponde404() {
            assertThatThrownBy(() -> servicio.reenviarEnlace(UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void unEntrenadorInactivoResponde409() {
            UUID id = entrenadorSinCuenta();
            entrenadores.cambiarActivo(id, false);

            ConflictoException e = catchThrowableOfType(ConflictoException.class, () -> servicio.reenviarEnlace(id));

            assertThat(e).hasMessage(EntrenadorServiceImpl.INACTIVO_REENVIO);
            assertThat(e.getCampos()).containsExactly("activo");
            verifyNoInteractions(auth, notificador);
        }

        @Test
        void siElCorreoEsDeOtraCuentaResponde409SinVincularNiCrearCuenta() {
            UUID id = entrenadorSinCuenta();
            when(usuarios.existeCorreo("andres.mora@pradofc.co")).thenReturn(true);

            assertThatThrownBy(() -> servicio.reenviarEnlace(id))
                    .isInstanceOf(ConflictoException.class).hasMessage("Ese correo ya pertenece a otra cuenta");
            assertThat(entrenadores.filas.get(id).getIdUsuario()).isNull();
            verifyNoInteractions(auth, notificador);
        }

        @Test
        void siAuthYaTieneElCorreoResponde409SinVincular() {
            UUID id = entrenadorSinCuenta();
            when(auth.crearUsuario(anyString())).thenThrow(new CorreoYaRegistradoException(422));

            assertThatThrownBy(() -> servicio.reenviarEnlace(id))
                    .isInstanceOf(ConflictoException.class).hasMessage("Ese correo ya pertenece a otra cuenta");
            assertThat(entrenadores.filas.get(id).getIdUsuario()).isNull();
            verifyNoInteractions(notificador);
        }

        @Test
        void unoPorMinutoCon429YRetryAfter() {
            UUID id = entrenadorConCuenta(UUID.randomUUID(), "andres.mora@pradofc.co");
            servicio.reenviarEnlace(id);

            reloj.avanzar(Duration.ofSeconds(20));
            DemasiadasSolicitudesException e = catchThrowableOfType(DemasiadasSolicitudesException.class,
                    () -> servicio.reenviarEnlace(id));
            assertThat(e.getEspera()).isEqualTo(Duration.ofSeconds(40));
            assertThat(e.segundosParaReintentar()).isEqualTo(40);

            reloj.avanzar(Duration.ofSeconds(40));
            servicio.reenviarEnlace(id);
            verify(auth, org.mockito.Mockito.times(2)).generarTokenRecuperacion(anyString());
        }

        @Test
        void elLimiteEsPorEntrenador() {
            UUID uno = entrenadorConCuenta(UUID.randomUUID(), "uno@pradofc.co");
            UUID otro = entrenadores.agregar("Carlos", "Díaz", "CC", "71654321", "carlos.diaz@pradofc.co");
            servicio.reenviarEnlace(uno);
            servicio.reenviarEnlace(otro);
        }

        @Test
        void siN8nFallaRespondeElErrorYElMinutoQuedaGastado() {
            UUID id = entrenadorConCuenta(UUID.randomUUID(), "andres.mora@pradofc.co");
            doThrow(new NotificacionNoEnviadaException("tiempo agotado", INotificador.ENLACE_CONTRASENA, null, null))
                    .when(notificador).notificar(anyString(), anyMap());

            assertThatThrownBy(() -> servicio.reenviarEnlace(id)).isInstanceOf(NotificacionNoEnviadaException.class);
            assertThatThrownBy(() -> servicio.reenviarEnlace(id)).isInstanceOf(DemasiadasSolicitudesException.class);
        }

        @Test
        void siFallaAntesDeGenerarElEnlaceSePuedeReintentarEnseguida() {
            UUID id = entrenadorConCuenta(UUID.randomUUID(), "andres.mora@pradofc.co");
            when(auth.generarTokenRecuperacion(anyString()))
                    .thenThrow(new AuthNoDisponibleException("caído", 503))
                    .thenReturn(TOKEN);

            assertThatThrownBy(() -> servicio.reenviarEnlace(id)).isInstanceOf(AuthNoDisponibleException.class);
            servicio.reenviarEnlace(id);
            verify(notificador).notificar(eq(INotificador.ENLACE_CONTRASENA), anyMap());
        }

        @Test
        void siLaBaseFallaTrasCrearLaCuentaLaBorraYNoVincula() {
            UUID id = entrenadorSinCuenta();
            DataAccessResourceFailureException caida = new DataAccessResourceFailureException("sin conexión");
            doThrow(caida).when(usuarios).asignarRol(any(), anyString());

            assertThatThrownBy(() -> servicio.reenviarEnlace(id)).isSameAs(caida);

            verify(auth).eliminar(any());
            assertThat(entrenadores.filas.get(id).getIdUsuario()).isNull();
            verifyNoInteractions(notificador);
            // No se generó ningún enlace: se puede reintentar enseguida (sin 429)
            org.mockito.Mockito.reset(usuarios);
            servicio.reenviarEnlace(id);
        }

        @Test
        void sinCorreoNiCuentaResponde409() {
            UUID id = entrenadorSinCuenta();
            entrenadores.filas.get(id).setCorreo(null);

            assertThatThrownBy(() -> servicio.reenviarEnlace(id))
                    .isInstanceOf(ConflictoException.class).hasMessage(EntrenadorServiceImpl.SIN_CORREO);
            verifyNoInteractions(auth);
        }
    }

    @Nested
    class EdicionDelCorreo {

        private co.edu.futdata.api.dto.EntrenadorRequestDTO conCorreo(UUID id, String correo) {
            Entrenador e = entrenadores.filas.get(id);
            return new co.edu.futdata.api.dto.EntrenadorRequestDTO(e.getNombre(), e.getApellido(), e.getTipoDocumento(),
                    e.getDocumento(), e.getFechaNacimiento(), e.getTelefono(), correo, e.getEspecialidad(),
                    e.getHorarioTrabajo(), e.getDireccion());
        }

        @Test
        void conCuentaNoSePuedeCambiarElCorreo() {
            UUID id = entrenadorConCuenta(UUID.randomUUID(), "andres.mora@pradofc.co");

            ConflictoException e = catchThrowableOfType(ConflictoException.class,
                    () -> servicio.editar(id, conCorreo(id, "otro.correo@pradofc.co")));

            assertThat(e).hasMessage(EntrenadorServiceImpl.CORREO_CON_CUENTA);
            assertThat(e.getCampos()).containsExactly("correo");
            assertThat(entrenadores.filas.get(id).getCorreo()).isEqualTo("andres.mora@pradofc.co");
        }

        @Test
        void conCuentaPasaSiElCorreoEsElMismoConOtrasMayusculasOEspacios() {
            UUID id = entrenadorConCuenta(UUID.randomUUID(), "andres.mora@pradofc.co");

            EntrenadorResponseDTO r = servicio.editar(id, conCorreo(id, "  Andres.Mora@PradoFC.co "));

            assertThat(r.correo()).isEqualTo("andres.mora@pradofc.co");
            assertThat(entrenadores.filas.get(id).getIdUsuario()).isNotNull();
        }

        @Test
        void sinCuentaSiSePuedeCambiar() {
            UUID id = entrenadorSinCuenta();

            assertThat(servicio.editar(id, conCorreo(id, "nuevo.mora@pradofc.co")).correo()).isEqualTo("nuevo.mora@pradofc.co");
        }
    }

    @Nested
    class Estado {

        @Test
        void desactivarConCuentaBaneaPrimeroYDespuesCambiaLaBaseYBorraSesiones() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");

            EntrenadorResponseDTO r = servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(false));

            assertThat(r.activo()).isFalse();
            InOrder orden = inOrder(auth, usuarios, sesiones);
            orden.verify(auth).cambiarAcceso(idUsuario, false);
            orden.verify(usuarios).actualizarActivo(idUsuario, false);
            orden.verify(sesiones).eliminarDeUsuario(idUsuario);
        }

        @Test
        void reactivarConCuentaRestauraElAcceso() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");
            entrenadores.cambiarActivo(id, false);

            assertThat(servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(true)).activo()).isTrue();

            InOrder orden = inOrder(auth, usuarios);
            orden.verify(auth).cambiarAcceso(idUsuario, true);
            orden.verify(usuarios).actualizarActivo(idUsuario, true);
            verify(sesiones, never()).eliminarDeUsuario(any());
        }

        @Test
        void sinCuentaSoloCambiaLaFila() {
            UUID id = entrenadorSinCuenta();

            assertThat(servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(false)).activo()).isFalse();
            verifyNoInteractions(auth, usuarios, sesiones);
        }

        @Test
        void siAuthFallaNoCambiaNada() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");
            categorias.agregar("Sub-12", id);
            doThrow(new IllegalStateException("Supabase Auth no está configurado.")).when(auth).cambiarAcceso(any(), anyBoolean());

            assertThatThrownBy(() -> servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(false)))
                    .isInstanceOf(AuthNoDisponibleException.class);

            assertThat(entrenadores.filas.get(id).isActivo()).isTrue();
            assertThat(categorias.de(id)).containsExactly("Sub-12");
            verify(usuarios, never()).actualizarActivo(any(), anyBoolean());
            assertThat(eventos).doesNotContain("inicio");
        }

        @Test
        void desactivarConLaCuentaInexistenteEnAuthSigueConLaBaseYRegistraLaPosibleMezclaDeProyectos() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");
            doThrow(new CuentaAuthNoEncontradaException(404)).when(auth).cambiarAcceso(idUsuario, false);

            EntrenadorResponseDTO r = servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(false));

            assertThat(r.activo()).isFalse();
            verify(usuarios).actualizarActivo(idUsuario, false);
            verify(sesiones).eliminarDeUsuario(idUsuario);
            verify(auth, never()).cambiarAcceso(idUsuario, true);
            assertThat(registros.list).anyMatch(e -> e.getLevel() == Level.ERROR
                    && e.getFormattedMessage().contains("SUPABASE_URL") && e.getFormattedMessage().contains("DB_URL"));
        }

        @Test
        void desactivarConLaCuentaInexistenteYLaBaseCaidaNoRevierteAuth() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");
            doThrow(new CuentaAuthNoEncontradaException(404)).when(auth).cambiarAcceso(idUsuario, false);
            DataAccessResourceFailureException caida = new DataAccessResourceFailureException("sin conexión");
            doThrow(caida).when(usuarios).actualizarActivo(idUsuario, false);

            assertThatThrownBy(() -> servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(false))).isSameAs(caida);
            verify(auth, never()).cambiarAcceso(idUsuario, true);
        }

        @Test
        void reactivarConLaCuentaInexistenteEnAuthResponde409SinCambiarNada() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");
            entrenadores.cambiarActivo(id, false);
            doThrow(new CuentaAuthNoEncontradaException(404)).when(auth).cambiarAcceso(idUsuario, true);

            ConflictoException e = catchThrowableOfType(ConflictoException.class,
                    () -> servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(true)));

            assertThat(e).hasMessage("La cuenta de acceso de este entrenador no existe en Supabase Auth; revisa la configuración");
            assertThat(entrenadores.filas.get(id).isActivo()).isFalse();
            verify(usuarios, never()).actualizarActivo(any(), anyBoolean());
            assertThat(eventos).doesNotContain("inicio");
            assertThat(registros.list).anyMatch(ev -> ev.getLevel() == Level.ERROR && ev.getFormattedMessage().contains("SUPABASE_URL"));
        }

        @Test
        void unErrorDeContratoDeAuthNoSeDisfrazaDe503() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");
            doThrow(new AuthPeticionInvalidaException("bloquear el acceso", 422)).when(auth).cambiarAcceso(idUsuario, false);

            assertThatThrownBy(() -> servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(false)))
                    .isInstanceOf(AuthPeticionInvalidaException.class);
            assertThat(entrenadores.filas.get(id).isActivo()).isTrue();
        }

        @Test
        void siLaBaseFallaRevierteAuth() {
            UUID idUsuario = UUID.randomUUID();
            UUID id = entrenadorConCuenta(idUsuario, "andres.mora@pradofc.co");
            DataAccessResourceFailureException caida = new DataAccessResourceFailureException("sin conexión");
            doThrow(caida).when(usuarios).actualizarActivo(idUsuario, false);

            assertThatThrownBy(() -> servicio.cambiarEstado(id, new CambiarEstadoEntrenadorRequestDTO(false))).isSameAs(caida);

            InOrder orden = inOrder(auth);
            orden.verify(auth).cambiarAcceso(idUsuario, false);
            orden.verify(auth).cambiarAcceso(idUsuario, true);
            assertThat(eventos).contains("rollback");
        }
    }

    /** Anota el inicio, el commit y el rollback para comprobar el orden respecto de Auth y n8n. Sin base de datos. */
    static final class TransaccionAnotada implements TransactionOperations {
        private final List<String> eventos;

        TransaccionAnotada(List<String> eventos) {
            this.eventos = eventos;
        }

        @Override
        public <T> T execute(TransactionCallback<T> accion) {
            eventos.add("inicio");
            try {
                T resultado = accion.doInTransaction(mock(TransactionStatus.class));
                eventos.add("commit");
                return resultado;
            } catch (RuntimeException e) {
                eventos.add("rollback");
                throw e;
            }
        }
    }

    /** Reloj que avanza a mano, para el límite de un envío por minuto. */
    static final class RelojMovible extends Clock {
        private Instant ahora;

        RelojMovible(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneId getZone() {
            return FormatoColombiano.ZONA;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
