package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.ResultadoCiclosDTO;
import co.edu.futdata.config.CobrosCicloProperties;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.config.MensualidadProperties;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.domain.entities.Pago;
import co.edu.futdata.domain.entities.TipoPago;
import co.edu.futdata.persistence.dao.AcudienteConCorreo;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.DeportistaDeCiclo;
import co.edu.futdata.persistence.dao.IAuditoriaDAO;
import co.edu.futdata.persistence.dao.IPagoDAO;
import co.edu.futdata.services.facade.ConflictoException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/** Reglas del servicio con DAO y notificador simulados (las consultas SQL se prueban en CobrosCicloConPostgresTest). */
class CobrosCicloServiceImplTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID OTRO = UUID.fromString("00000000-0000-0000-0000-0000000000d2");

    private IPagoDAO pagos;
    private INotificador notificador;
    private IAuditoriaDAO auditoria;
    private CobrosCicloServiceImpl servicio;

    @BeforeEach
    void preparar() {
        pagos = mock(IPagoDAO.class);
        notificador = mock(INotificador.class);
        auditoria = mock(IAuditoriaDAO.class);
        servicio = armar(new MensualidadProperties(90_000, 5, false));
        when(pagos.acudientesConCorreo(any())).thenReturn(List.of(new AcudienteConCorreo("María Torres", "maria@correo.co")));
    }

    private CobrosCicloServiceImpl armar(MensualidadProperties mensualidad) {
        return new CobrosCicloServiceImpl(pagos, auditoria, notificador, mensualidad, new CobrosCicloProperties(150_000, 90_000, 5, 7),
                new FrontendProperties("https://club.test"), mock(PlatformTransactionManager.class));
    }

    private void deportistas(DeportistaDeCiclo... lista) {
        when(pagos.deportistasParaCiclo()).thenReturn(List.of(lista));
    }

    private static DeportistaDeCiclo registrado(UUID id, String fecha) {
        return new DeportistaDeCiclo(id, "Pedro Gómez", LocalDate.parse(fecha));
    }

    private List<Pago> creados() {
        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagos, org.mockito.Mockito.atLeast(0)).crear(captor.capture());
        return captor.getAllValues();
    }

    // --- Mensualidad del ciclo ---

    @Test
    void unRegistradoHaceDosAnosSoloRecibeElCicloVigente() {
        deportistas(registrado(ID, "2024-11-20"));
        // hoy 10 nov, límite 15 nov: el 20 de noviembre aún no entra, así que el vigente es el del 20 de octubre; nunca los 23 anteriores
        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r.creadas()).isEqualTo(1);
        List<Pago> lista = creados();
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).tipo()).isEqualTo(TipoPago.MENSUALIDAD);
        assertThat(lista.get(0).periodo()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(lista.get(0).fechaVencimiento()).isEqualTo(HOY); // la fecha (20 oct) ya pasó: vence hoy
        assertThat(lista.get(0).monto()).isEqualTo(90_000);
        assertThat(lista.get(0).concepto()).isEqualTo("Mensualidad octubre 2026");
        verify(notificador, never()).notificar(any(), anyMap()); // no es anticipado: no se avisa
    }

    @Test
    void unCobroAnticipadoVenceEnLaFechaDeCobroYAvisaAlAcudiente() {
        deportistas(registrado(ID, "2025-11-14")); // 14 nov <= 10 nov + 5
        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r).isEqualTo(new ResultadoCiclosDTO(1, 0, 1, 0, 0));
        assertThat(creados().get(0).fechaVencimiento()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(creados().get(0).periodo()).isEqualTo(LocalDate.of(2026, 11, 1));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificador).notificar(eq("RECORDATORIO_PAGO"), datos.capture());
        assertThat(datos.getValue()).containsOnly(
                Map.entry("correo", "maria@correo.co"), Map.entry("nombreAcudiente", "María Torres"),
                Map.entry("nombreDeportista", "Pedro Gómez"), Map.entry("concepto", "Mensualidad noviembre 2026"),
                Map.entry("monto", 90_000L), Map.entry("fechaVencimiento", "2026-11-14"), Map.entry("diasRestantes", 4),
                Map.entry("enlacePagos", "https://club.test/acudiente/pagos"));
    }

    @Test
    void esIdempotenteSiLaMensualidadYaExisteEnCualquierEstado() {
        deportistas(registrado(ID, "2025-11-14"));
        when(pagos.existeMensualidad(ID, LocalDate.of(2026, 11, 1))).thenReturn(true); // incluye RECHAZADO a propósito

        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r).isEqualTo(new ResultadoCiclosDTO(0, 1, 0, 0, 0));
        verify(pagos, never()).crear(any());
        verify(notificador, never()).notificar(any(), anyMap());
    }

    @Test
    void siSeCreaLaMensualidadEnLaMismaCarreraCuentaComoYaExistente() {
        deportistas(registrado(ID, "2025-11-14"), registrado(OTRO, "2025-11-14"));
        doThrow(new DatoDuplicadoException("mensualidad", null)).when(pagos).crear(any());

        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r).isEqualTo(new ResultadoCiclosDTO(0, 2, 0, 0, 0));
        verify(notificador, never()).notificar(any(), anyMap());
    }

    @Test
    void unRegistroAFuturoPasadoElLimiteNoGeneraNada() {
        deportistas(registrado(ID, "2026-11-16"));
        assertThat(servicio.generarCobrosDeCiclo(HOY, null)).isEqualTo(new ResultadoCiclosDTO(0, 0, 0, 0, 0));
        verify(pagos, never()).crear(any());
    }

    // --- Ingreso ---

    @Test
    void unIngresoRecienteRecibeInscripcionYUniformeConVencimientoHoySinAvisarlos() {
        deportistas(registrado(ID, "2026-11-05")); // hace 5 días
        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r.creadas()).isEqualTo(3);
        assertThat(creados()).extracting(Pago::tipo).containsExactly(TipoPago.MENSUALIDAD, TipoPago.INSCRIPCION, TipoPago.UNIFORME);
        Pago inscripcion = creados().get(1);
        Pago uniforme = creados().get(2);
        assertThat(inscripcion.monto()).isEqualTo(150_000);
        assertThat(inscripcion.fechaVencimiento()).isEqualTo(HOY);
        assertThat(inscripcion.periodo()).isNull();
        assertThat(uniforme.monto()).isEqualTo(90_000);
        assertThat(uniforme.fechaVencimiento()).isEqualTo(HOY);
        // La mensualidad es del 5 de noviembre (ya pasó): no es anticipada, y los de ingreso nunca se avisan
        verify(notificador, never()).notificar(any(), anyMap());
    }

    @Test
    void laVentanaDeIngresoLlegaHastaElSeptimoDia() {
        deportistas(registrado(ID, "2026-11-03"), registrado(OTRO, "2026-11-02")); // hoy-7 sí; hoy-8 no
        servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(creados()).extracting(Pago::idDeportista, Pago::tipo).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ID, TipoPago.MENSUALIDAD), org.assertj.core.groups.Tuple.tuple(ID, TipoPago.INSCRIPCION),
                org.assertj.core.groups.Tuple.tuple(ID, TipoPago.UNIFORME), org.assertj.core.groups.Tuple.tuple(OTRO, TipoPago.MENSUALIDAD));
    }

    @Test
    void noRepiteInscripcionNiUniformeSiYaTieneUnCobroDeEseTipo() {
        deportistas(registrado(ID, "2026-11-05"));
        when(pagos.existeCobroDeTipo(ID, TipoPago.INSCRIPCION)).thenReturn(true);

        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(creados()).extracting(Pago::tipo).containsExactly(TipoPago.MENSUALIDAD, TipoPago.UNIFORME);
        assertThat(r.yaExistentes()).isEqualTo(1);
    }

    // --- Fallos ---

    @Test
    void unFalloDeN8nNoBloqueaElCobroYSeCuentaComoAvisoFallido() {
        deportistas(registrado(ID, "2025-11-14"));
        doThrow(new NotificacionNoEnviadaException("caído", "RECORDATORIO_PAGO", 502, null)).when(notificador).notificar(any(), anyMap());

        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r).isEqualTo(new ResultadoCiclosDTO(1, 0, 0, 1, 0));
        verify(pagos, times(1)).crear(any());
    }

    @Test
    void unAcudienteQueFallaNoImpideAvisarAlOtro() {
        deportistas(registrado(ID, "2025-11-14"));
        when(pagos.acudientesConCorreo(ID)).thenReturn(List.of(new AcudienteConCorreo("Ana", "ana@correo.co"), new AcudienteConCorreo("Luis", "luis@correo.co")));
        doThrow(new NotificacionNoEnviadaException("caído", "RECORDATORIO_PAGO", null, null)).doNothing().when(notificador).notificar(any(), anyMap());

        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r.avisosEnviados()).isEqualTo(1);
        assertThat(r.avisosFallidos()).isEqualTo(1);
    }

    @Test
    void sinFrontendUrlElAvisoFallaPeroElCobroSeCrea() {
        deportistas(registrado(ID, "2025-11-14"));
        var sinUrl = new CobrosCicloServiceImpl(pagos, auditoria, notificador, new MensualidadProperties(90_000, 5, false),
                new CobrosCicloProperties(150_000, 90_000, 5, 7), new FrontendProperties(""), mock(PlatformTransactionManager.class));

        ResultadoCiclosDTO r = sinUrl.generarCobrosDeCiclo(HOY, null);

        assertThat(r).isEqualTo(new ResultadoCiclosDTO(1, 0, 0, 1, 0));
        verify(notificador, never()).notificar(any(), anyMap());
    }

    @Test
    void unDeportistaConErrorNoAbortaALosDemas() {
        deportistas(registrado(ID, "2025-11-14"), registrado(OTRO, "2025-11-14"));
        when(pagos.existeMensualidad(eq(ID), any())).thenThrow(new IllegalStateException("base caída"));

        ResultadoCiclosDTO r = servicio.generarCobrosDeCiclo(HOY, null);

        assertThat(r.deportistasConError()).isEqualTo(1);
        assertThat(r.creadas()).isEqualTo(1);
        assertThat(creados()).extracting(Pago::idDeportista).containsExactly(OTRO);
    }

    @Test
    void sinAcudientesConCorreoNoHayAvisos() {
        deportistas(registrado(ID, "2025-11-14"));
        when(pagos.acudientesConCorreo(ID)).thenReturn(List.of());
        assertThat(servicio.generarCobrosDeCiclo(HOY, null)).isEqualTo(new ResultadoCiclosDTO(1, 0, 0, 0, 0));
    }

    @Test
    void sinMontoDeMensualidadEsConflicto() {
        deportistas(registrado(ID, "2025-11-14"));
        assertThatThrownBy(() -> armar(new MensualidadProperties(0, 5, false)).generarCobrosDeCiclo(HOY, null))
                .isInstanceOf(ConflictoException.class);
        verify(pagos, never()).crear(any());
    }

    @Test
    void registraLaAuditoriaConElUsuarioQuePidio() {
        deportistas(registrado(ID, "2025-11-14"));
        UUID admin = UUID.randomUUID();
        servicio.generarCobrosDeCiclo(HOY, admin);
        verify(auditoria).registrar(eq(admin), eq("COBROS_CICLO_GENERADOS"), anyMap());
    }

    @Test
    void elCorreoSeEnmascaraEnLosLogs() {
        assertThat(CobrosCicloServiceImpl.enmascarar("maria@gmail.com")).isEqualTo("m***@gmail.com");
        assertThat(CobrosCicloServiceImpl.enmascarar("raro")).isEqualTo("***");
    }
}
