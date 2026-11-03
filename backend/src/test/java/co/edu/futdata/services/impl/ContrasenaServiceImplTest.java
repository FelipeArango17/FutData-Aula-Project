package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.futdata.datasource.external.AuthNoDisponibleException;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.datasource.external.SesionRecuperacion;
import co.edu.futdata.datasource.external.TokenRecuperacionInvalidoException;
import co.edu.futdata.domain.rules.ReglasContrasena;
import co.edu.futdata.services.facade.CrearContrasenaNoDisponibleException;
import co.edu.futdata.services.facade.DemasiadasSolicitudesException;
import co.edu.futdata.services.facade.EnlaceInvalidoException;
import co.edu.futdata.services.facade.PoliticaContrasenaException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

/** Crear la contraseña con el enlace: orden, errores, límites y logs. Supabase Auth simulado; valores inventados. */
class ContrasenaServiceImplTest {

    private static final String TOKEN = "hash-inventado-del-enlace-7c1e";
    private static final String CONTRASENA = "Inventada2026";
    private static final String TOKEN_SESION = "jwt-inventado-de-la-sesion-2b9f";
    private static final String IP = "190.24.137.58";
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private ISupabaseAuthAdmin auth;
    private EntrenadorServiceImplCuentaTest.RelojMovible reloj;
    private ContrasenaServiceImpl servicio;
    private ListAppender<ILoggingEvent> registros;

    @BeforeEach
    void preparar() {
        auth = mock(ISupabaseAuthAdmin.class);
        when(auth.verificarTokenRecuperacion(anyString())).thenReturn(new SesionRecuperacion(ID, TOKEN_SESION));
        reloj = new EntrenadorServiceImplCuentaTest.RelojMovible(Instant.parse("2026-11-02T15:00:00Z"));
        servicio = new ContrasenaServiceImpl(auth, reloj);
        registros = new ListAppender<>();
        registros.start();
        raiz().addAppender(registros);
        ((Logger) LoggerFactory.getLogger("co.edu.futdata")).setLevel(Level.TRACE);
    }

    @AfterEach
    void sinSecretosEnLogs() {
        raiz().detachAppender(registros);
        assertThat(registros.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain(TOKEN).doesNotContain(CONTRASENA).doesNotContain(TOKEN_SESION).doesNotContain(IP)
                .doesNotContain("137.58"));
    }

    private static Logger raiz() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    @Test
    void verificaFijaPorIdYCierraLaSesionEnEseOrden() {
        servicio.crearContrasena(TOKEN, CONTRASENA, IP);

        InOrder orden = inOrder(auth);
        orden.verify(auth).verificarTokenRecuperacion(TOKEN);
        orden.verify(auth).fijarContrasena(ID, CONTRASENA);
        orden.verify(auth).cerrarSesion(TOKEN_SESION);
        orden.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void sinTokenEsElEnlaceInvalidoSinLlamarASupabase(String token) {
        assertThatThrownBy(() -> servicio.crearContrasena(token, CONTRASENA, IP))
                .isInstanceOf(EnlaceInvalidoException.class)
                .hasMessage("El enlace no es válido o venció; pide al administrador que te lo reenvíe");
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta1", "sinnumeros", "12345678"})
    void laPoliticaSeRevisaAntesDeVerificarParaNoGastarElEnlace(String contrasena) {
        PoliticaContrasenaException e = catchThrowableOfType(PoliticaContrasenaException.class,
                () -> servicio.crearContrasena(TOKEN, contrasena, IP));

        assertThat(e).hasMessage(ReglasContrasena.MENSAJE);
        assertThat(e.getCampos()).containsExactly("contrasena");
        verifyNoInteractions(auth);
    }

    @Test
    void sinContrasenaTambienEsLaPolitica() {
        assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, null, IP)).isInstanceOf(PoliticaContrasenaException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void tokenInvalidoVencidoOUsadoDanElMismoMensajeYNoFijanNada() {
        when(auth.verificarTokenRecuperacion(anyString())).thenThrow(new TokenRecuperacionInvalidoException(403));

        String inexistente = catchThrowableOfType(EnlaceInvalidoException.class,
                () -> servicio.crearContrasena("otro-token", CONTRASENA, IP)).getMessage();
        String usado = catchThrowableOfType(EnlaceInvalidoException.class,
                () -> servicio.crearContrasena(TOKEN, CONTRASENA, IP)).getMessage();

        assertThat(inexistente).isEqualTo(usado).isEqualTo(EnlaceInvalidoException.MENSAJE);
        verify(auth, never()).fijarContrasena(any(), anyString());
    }

    @Test
    void elMismoEnlaceDosVecesLaSegundaEsInvalido() {
        when(auth.verificarTokenRecuperacion(TOKEN))
                .thenReturn(new SesionRecuperacion(ID, TOKEN_SESION))
                .thenThrow(new TokenRecuperacionInvalidoException(403));

        servicio.crearContrasena(TOKEN, CONTRASENA, IP);
        assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, CONTRASENA, IP)).isInstanceOf(EnlaceInvalidoException.class);
        verify(auth, times(1)).fijarContrasena(ID, CONTRASENA);
    }

    @Test
    void siSupabaseFallaAlVerificarEs503() {
        when(auth.verificarTokenRecuperacion(anyString())).thenThrow(new AuthNoDisponibleException("Supabase Auth respondió 500", 500));

        assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, CONTRASENA, IP))
                .isInstanceOf(CrearContrasenaNoDisponibleException.class)
                .hasMessage("Inténtalo de nuevo; si persiste, pide que te reenvíen el enlace.");
        verify(auth, never()).fijarContrasena(any(), anyString());
    }

    @Test
    void siFallaFijarLaContrasenaEs503YIgualCierraLaSesion() {
        doThrow(new AuthNoDisponibleException("Supabase Auth respondió 422", 422)).when(auth).fijarContrasena(any(), anyString());

        assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, CONTRASENA, IP))
                .isInstanceOf(CrearContrasenaNoDisponibleException.class);
        verify(auth).cerrarSesion(TOKEN_SESION);
    }

    @Test
    void siCerrarLaSesionFallaLaContrasenaIgualQuedaCreada() {
        doThrow(new AuthNoDisponibleException("caído", 502)).when(auth).cerrarSesion(anyString());

        assertThatCode(() -> servicio.crearContrasena(TOKEN, CONTRASENA, IP)).doesNotThrowAnyException();
        verify(auth).fijarContrasena(ID, CONTRASENA);
    }

    @Test
    void diezIntentosPorMinutoPorIpCon429() {
        when(auth.verificarTokenRecuperacion(anyString())).thenThrow(new TokenRecuperacionInvalidoException(403));
        for (int i = 0; i < 10; i++) {
            String token = "token-" + i;
            assertThatThrownBy(() -> servicio.crearContrasena(token, CONTRASENA, IP)).isInstanceOf(EnlaceInvalidoException.class);
        }

        DemasiadasSolicitudesException e = catchThrowableOfType(DemasiadasSolicitudesException.class,
                () -> servicio.crearContrasena("token-11", CONTRASENA, IP));
        assertThat(e.getEspera()).isEqualTo(Duration.ofMinutes(1));
        assertThat(e.segundosParaReintentar()).isEqualTo(60);
        // Otra IP sí puede
        assertThatThrownBy(() -> servicio.crearContrasena("token-12", CONTRASENA, "181.50.1.2"))
                .isInstanceOf(EnlaceInvalidoException.class);
        // Al minuto, la primera vuelve a poder
        reloj.avanzar(Duration.ofMinutes(1));
        assertThatThrownBy(() -> servicio.crearContrasena("token-13", CONTRASENA, IP)).isInstanceOf(EnlaceInvalidoException.class);
    }

    @Test
    void losIntentosQueFallanPorPoliticaNoCuentan() {
        for (int i = 0; i < 30; i++) {
            assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, "corta", IP)).isInstanceOf(PoliticaContrasenaException.class);
        }
        assertThatCode(() -> servicio.crearContrasena(TOKEN, CONTRASENA, IP)).doesNotThrowAnyException();
    }

    @Test
    void cincoIntentosPorMinutoPorTokenAunqueCambieLaIp() {
        when(auth.verificarTokenRecuperacion(anyString())).thenThrow(new TokenRecuperacionInvalidoException(403));
        for (int i = 0; i < 5; i++) {
            String ip = "10.0.0." + i;
            assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, CONTRASENA, ip)).isInstanceOf(EnlaceInvalidoException.class);
        }
        assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, CONTRASENA, "10.0.0.99"))
                .isInstanceOf(DemasiadasSolicitudesException.class);
        verify(auth, times(5)).verificarTokenRecuperacion(TOKEN);
    }

    @Test
    void unTopeGlobalDe60PorMinuto() {
        when(auth.verificarTokenRecuperacion(anyString())).thenThrow(new TokenRecuperacionInvalidoException(403));
        for (int i = 0; i < 60; i++) {
            String token = "token-" + i;
            String ip = "10.0." + (i / 200) + "." + i; // una IP distinta por intento
            assertThatThrownBy(() -> servicio.crearContrasena(token, CONTRASENA, ip)).isInstanceOf(EnlaceInvalidoException.class);
        }
        assertThatThrownBy(() -> servicio.crearContrasena("token-61", CONTRASENA, "10.9.9.9"))
                .isInstanceOf(DemasiadasSolicitudesException.class);
    }

    @Test
    void unRechazoDeUnLimiteNoGastaLosDemas() {
        when(auth.verificarTokenRecuperacion(anyString())).thenThrow(new TokenRecuperacionInvalidoException(403));
        // Gasta el cupo del token desde 5 IPs distintas
        for (int i = 0; i < 5; i++) {
            String ip = "10.1.1." + i;
            assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, CONTRASENA, ip)).isInstanceOf(EnlaceInvalidoException.class);
        }
        // Desde IP fija: 9 rechazos por el límite del token no deben gastar los 10 de la IP
        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> servicio.crearContrasena(TOKEN, CONTRASENA, IP)).isInstanceOf(DemasiadasSolicitudesException.class);
        }
        for (String token : List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10")) {
            assertThatThrownBy(() -> servicio.crearContrasena(token, CONTRASENA, IP)).isInstanceOf(EnlaceInvalidoException.class);
        }
    }
}
