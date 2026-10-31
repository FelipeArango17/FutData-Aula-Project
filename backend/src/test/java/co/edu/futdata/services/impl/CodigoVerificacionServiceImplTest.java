package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.domain.entities.CodigoVerificacion;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.domain.rules.GeneradorCodigo;
import co.edu.futdata.persistence.dao.ICodigoVerificacionDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.CodigoVerificacionException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodigoVerificacionServiceImplTest {
    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private static final Instant AHORA = Instant.parse("2026-10-30T04:00:00Z");
    private static final UUID SESION = UUID.fromString("5e55a000-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private IUsuarioDAO usuarios;
    private ICodigoVerificacionDAO codigos;
    private ISesionVerificadaDAO sesiones;
    private INotificador notificador;
    private GeneradorCodigo generador;
    private CodigoVerificacionServiceImpl servicio;

    @BeforeEach
    void preparar() {
        usuarios = mock(IUsuarioDAO.class);
        codigos = mock(ICodigoVerificacionDAO.class);
        sesiones = mock(ISesionVerificadaDAO.class);
        notificador = mock(INotificador.class);
        generador = mock(GeneradorCodigo.class);
        servicio = new CodigoVerificacionServiceImpl(usuarios, codigos, sesiones, notificador, generador,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(usuarios.buscarPorId(ID)).thenReturn(Optional.of(usuario()));
    }

    @Test
    void generaHashNoGuardaCodigoPlanoYNotifica() {
        when(codigos.buscarUltimo(ID)).thenReturn(Optional.empty());
        when(generador.generar()).thenReturn("012345");
        when(generador.hash("012345")).thenReturn("sal$hash");
        when(codigos.guardar(any())).thenReturn(UUID.randomUUID());

        var respuesta = servicio.solicitar(ID);

        assertThat(respuesta.correoEnmascarado()).isEqualTo("a***@pradofc.co");
        verify(codigos).guardar(argThat(c -> c.getCodigoHash().equals("sal$hash")
                && c.getExpiraEn().equals(AHORA.plusSeconds(600))));
        verify(notificador).notificar(eq(INotificador.CODIGO_2FA), argThat(datos ->
                "012345".equals(datos.get("codigo")) && datos.get("vigenciaMinutos").equals(10L)));
    }

    @Test
    void limitaReenvioDuranteUnMinuto() {
        CodigoVerificacion codigo = codigo(AHORA.minusSeconds(30), AHORA.plusSeconds(570), 0);
        when(codigos.buscarUltimo(ID)).thenReturn(Optional.of(codigo));

        assertThatThrownBy(() -> servicio.reenviar(ID))
                .isInstanceOf(CodigoVerificacionException.class)
                .hasMessageContaining("esperar");
        verifyNoInteractions(generador, notificador);
    }

    @Test
    void quintoIntentoBloqueaPorQuinceMinutos() {
        CodigoVerificacion codigo = codigo(AHORA.minusSeconds(30), AHORA.plusSeconds(570), 4);
        when(codigos.buscarUltimo(ID)).thenReturn(Optional.of(codigo));
        when(generador.coincide("111111", "hash")).thenReturn(false);
        when(codigos.incrementarIntentos(codigo.getId())).thenReturn(5);

        assertThatThrownBy(() -> servicio.verificar(ID, SESION, "111111"))
                .isInstanceOf(CodigoVerificacionException.class)
                .hasMessageContaining("máximo");
        verify(codigos).bloquear(eq(codigo.getId()), eq(AHORA.plusSeconds(900)));
        verifyNoInteractions(sesiones);
    }

    @Test
    void codigoCorrectoMarcaSolicitudComoUsada() {
        CodigoVerificacion codigo = codigo(AHORA.minusSeconds(30), AHORA.plusSeconds(570), 2);
        when(codigos.buscarUltimo(ID)).thenReturn(Optional.of(codigo));
        when(generador.coincide("012345", "hash")).thenReturn(true);

        assertThat(servicio.verificar(ID, SESION, "012345").id()).isEqualTo(ID);
        verify(codigos).marcarUsado(codigo.getId());
        verify(codigos, never()).incrementarIntentos(any());
    }

    @Test
    void codigoCorrectoMarcaSoloLaSesionDelTokenConLaVigenciaDeLasReglas() {
        CodigoVerificacion codigo = codigo(AHORA.minusSeconds(30), AHORA.plusSeconds(570), 0);
        when(codigos.buscarUltimo(ID)).thenReturn(Optional.of(codigo));
        when(generador.coincide("012345", "hash")).thenReturn(true);

        servicio.verificar(ID, SESION, "012345");

        verify(sesiones).marcarVerificada(SESION, ID, AHORA,
                AHORA.plus(co.edu.futdata.domain.rules.ReglasVerificacion.VIGENCIA_SESION));
        verify(sesiones, never()).eliminarDeUsuario(any());
    }

    @Test
    void sinSesionNoSeVerifica() {
        assertThatThrownBy(() -> servicio.verificar(ID, null, "012345")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(codigos, sesiones);
    }

    @Test
    void cerrarSesionBorraSoloLaSesionDelToken() {
        servicio.cerrarSesion(ID, SESION);

        verify(sesiones).eliminar(SESION, ID);
        verify(sesiones, never()).eliminarDeUsuario(any());
    }

    @Test
    void unaCuentaDeAuthSinPerfilEnFutDataEs404YNoEnviaNada() {
        UUID otro = UUID.randomUUID();
        when(usuarios.buscarPorId(otro)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.solicitar(otro))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("La cuenta no está registrada en FutData.");
        verifyNoInteractions(notificador);
    }

    @Test
    void unaCuentaInactivaNoRecibeCodigo() {
        Usuario inactivo = usuario();
        inactivo.setActivo(false);
        when(usuarios.buscarPorId(ID)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> servicio.solicitar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(notificador);
    }

    private static Usuario usuario() {
        Usuario u = new Usuario();
        u.setId(ID); u.setNombre("Ana"); u.setApellido("Pérez"); u.setCorreo("ana@pradofc.co");
        u.setRol("ACUDIENTE"); u.setActivo(true);
        return u;
    }

    private static CodigoVerificacion codigo(Instant envio, Instant expira, int intentos) {
        CodigoVerificacion c = new CodigoVerificacion();
        c.setId(UUID.randomUUID()); c.setUsuarioId(ID); c.setCodigoHash("hash");
        c.setUltimoEnvio(envio); c.setExpiraEn(expira); c.setIntentos(intentos);
        return c;
    }
}
