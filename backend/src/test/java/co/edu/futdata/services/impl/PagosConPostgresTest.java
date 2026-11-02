package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.api.dto.AprobarPagoRequestDTO;
import co.edu.futdata.api.dto.CrearPagoRequestDTO;
import co.edu.futdata.api.dto.IniciarWompiResponseDTO;
import co.edu.futdata.api.dto.MiPagoResponseDTO;
import co.edu.futdata.api.dto.PaginaResponseDTO;
import co.edu.futdata.api.dto.PagoResponseDTO;
import co.edu.futdata.api.dto.RechazarPagoRequestDTO;
import co.edu.futdata.api.dto.ResultadoGeneracionDTO;
import co.edu.futdata.config.ClubProperties;
import co.edu.futdata.config.MensualidadProperties;
import co.edu.futdata.config.WompiProperties;
import co.edu.futdata.datasource.external.IWompiClient;
import co.edu.futdata.datasource.external.TransaccionWompi;
import co.edu.futdata.datasource.external.WompiNoDisponibleException;
import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.TipoPago;
import co.edu.futdata.persistence.dao.FiltroPagos;
import co.edu.futdata.persistence.impl.AuditoriaDAOImpl;
import co.edu.futdata.persistence.impl.DeportistaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.NotificacionPagoDAOImpl;
import co.edu.futdata.persistence.impl.PagoDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.FirmaWebhookInvalidaException;
import co.edu.futdata.services.facade.PagosNoConfiguradosException;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import co.edu.futdata.util.reports.ComprobantePdfGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cobros, pagos en línea y webhook de Wompi con los DAO y servicios reales sobre PostgreSQL (Testcontainers o base local).
 * El cliente HTTP de Wompi es una lista simulada: nunca se llama a Wompi. Los checksums de los eventos se calculan aquí con
 * SHA-256 propio (no con {@code WompiFirmas}) para no repetir la implementación.
 */
@ExtendWith(DockerDisponible.class)
class PagosConPostgresTest {

    private static final String SECRETO_EVENTOS = "test_events_secreto";
    private static final String SECRETO_INTEGRIDAD = "test_integrity_secreto";
    private static final WompiProperties WOMPI = new WompiProperties("pub_test_publica", "prv_test_privada", SECRETO_EVENTOS,
            SECRETO_INTEGRIDAD, WompiProperties.SANDBOX, "https://club.test/pagos/resultado");
    private static final WompiProperties SIN_LLAVES = new WompiProperties("", "", "", "", WompiProperties.SANDBOX, "");
    /** 10 de noviembre de 2026, 10:00 en Colombia. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), java.time.ZoneId.of("America/Bogota"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);
    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private NamedParameterJdbcTemplate jdbc;
    private PagoServiceImpl servicio;
    private PagoWompiServiceImpl webhook;
    private TransactionTemplate transaccion;
    private final List<TransaccionWompi> transaccionesDeWompi = new ArrayList<>();
    private boolean wompiCaido;
    private UUID admin;
    private UUID deportista;
    private UUID acudiente;     // usuario acudiente de `deportista`
    private UUID otroAcudiente; // usuario acudiente de otro deportista
    private int contador;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiar();
        transaccionesDeWompi.clear();
        wompiCaido = false;
        servicio = armarServicio(WOMPI, new MensualidadProperties(90_000, 5, false));
        webhook = armarWebhook(WOMPI);
        admin = usuario("admin");
        deportista = deportista("Pedro");
        acudiente = acudienteDe(deportista);
        otroAcudiente = acudienteDe(deportista("Luis"));
    }

    private PagoServiceImpl armarServicio(WompiProperties wompi, MensualidadProperties mensualidad) {
        var txm = new DataSourceTransactionManager(PostgresDePrueba.dataSource());
        transaccion = new TransactionTemplate(txm);
        var pagos = new PagoDAOImpl(jdbc);
        var auditoria = new AuditoriaDAOImpl(jdbc, JSON);
        var aplicador = new AplicadorTransaccionWompi(pagos, auditoria, new NotificacionPagoDAOImpl(jdbc), RELOJ);
        IWompiClient cliente = referencia -> {
            if (wompiCaido) {
                throw new WompiNoDisponibleException("caído", null);
            }
            return List.copyOf(transaccionesDeWompi);
        };
        var club = new ClubProperties("Club de prueba", "811032309-1", "Calle 1", "3137317366", "club@prueba.co");
        return new PagoServiceImpl(pagos, new DeportistaDAOImpl(jdbc), auditoria, cliente, aplicador,
                new ComprobantePdfGenerator(club, RELOJ), wompi, mensualidad, RELOJ, txm);
    }

    private PagoWompiServiceImpl armarWebhook(WompiProperties wompi) {
        var pagos = new PagoDAOImpl(jdbc);
        var aplicador = new AplicadorTransaccionWompi(pagos, new AuditoriaDAOImpl(jdbc, JSON), new NotificacionPagoDAOImpl(jdbc), RELOJ);
        return new PagoWompiServiceImpl(wompi, pagos, aplicador, JSON, new DataSourceTransactionManager(PostgresDePrueba.dataSource()));
    }

    private void limpiar() {
        for (String tabla : List.of("auditoria", "notificacion_pago", "pago", "acudiente_deportista", "acudiente", "documento", "deportista")) {
            jdbc.getJdbcOperations().update("DELETE FROM " + tabla);
        }
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'pagos-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
    }

    // --- Datos ---------------------------------------------------------------------------------------------

    private UUID usuario(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, :n, 'Prueba', :c)",
                Map.of("id", id, "n", nombre, "c", "pagos-" + id + "@pradofc.co"));
        return id;
    }

    private UUID deportista(String nombre) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento)
                VALUES (:n, 'Gómez', 'TI', :doc, '2016-03-03') RETURNING id_deportista""",
                new MapSqlParameterSource("n", nombre).addValue("doc", "10" + (++contador) + System.nanoTime() % 100000), UUID.class);
    }

    private UUID acudienteDe(UUID idDeportista) {
        UUID idUsuario = usuario("acudiente");
        UUID idAcudiente = jdbc.queryForObject("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, id_usuario)
                VALUES ('María', 'Torres', 'CC', :doc, '1985-05-05', :usuario) RETURNING id_acudiente""",
                new MapSqlParameterSource("doc", "9" + (++contador) + System.nanoTime() % 100000).addValue("usuario", idUsuario), UUID.class);
        jdbc.update("INSERT INTO acudiente_deportista (id_acudiente, id_deportista) VALUES (:a, :d)", Map.of("a", idAcudiente, "d", idDeportista));
        return idUsuario;
    }

    private PagoResponseDTO mensualidad(UUID idDeportista, LocalDate periodo, long monto) {
        return servicio.crear(admin, new CrearPagoRequestDTO(idDeportista, TipoPago.MENSUALIDAD, null, monto, periodo.withDayOfMonth(5), periodo));
    }

    private PagoResponseDTO cobro() {
        return mensualidad(deportista, NOVIEMBRE, 90_000);
    }

    private PagoResponseDTO leer(UUID idPago) {
        return servicio.listar(new FiltroPagos(null, null, null, null), "creadoEn,asc", 0, 100).content().stream()
                .filter(p -> p.id().equals(idPago)).findFirst().orElseThrow();
    }

    private List<String> eventosDeAuditoria() {
        return jdbc.getJdbcOperations().queryForList("SELECT evento FROM auditoria ORDER BY id_auditoria", String.class);
    }

    private List<String> avisos() {
        return jdbc.getJdbcOperations().queryForList("SELECT mensaje FROM notificacion_pago ORDER BY fecha_envio, mensaje", String.class);
    }

    private int cantidadDePagos() {
        return jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM pago", Integer.class);
    }

    // --- Eventos del webhook -------------------------------------------------------------------------------

    private record Evento(String cuerpo, String checksum) {
    }

    private static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private static Evento evento(String estado, String idTransaccion, long centavos, String referencia, String medio) throws Exception {
        long timestamp = 1_793_000_000L;
        String checksum = sha256(idTransaccion + estado + centavos + timestamp + SECRETO_EVENTOS);
        String cuerpo = """
                {"event":"transaction.updated","data":{"transaction":{"id":"%s","status":"%s","amount_in_cents":%d,"reference":"%s",
                 "payment_method_type":"%s","currency":"COP"}},"environment":"test",
                 "signature":{"properties":["transaction.id","transaction.status","transaction.amount_in_cents"],"checksum":"%s"},
                 "timestamp":%d,"sent_at":"2026-11-10T15:00:00.000Z"}""".formatted(idTransaccion, estado, centavos, referencia, medio, checksum, timestamp);
        return new Evento(cuerpo, checksum);
    }

    private void recibir(Evento e) {
        transaccion.executeWithoutResult(s -> webhook.procesarEvento(e.cuerpo(), e.checksum()));
    }

    /** El cobro ya iniciado en Wompi (con su referencia). */
    private String iniciar(PagoResponseDTO pago, UUID idAcudiente) {
        return servicio.iniciarWompi(idAcudiente, pago.id()).referencia();
    }

    private void recibir(String estado, String idTx, PagoResponseDTO pago, String referencia, String medio) throws Exception {
        recibir(evento(estado, idTx, pago.monto() * 100, referencia, medio));
    }

    // === Máquina de estados =================================================================================

    @Test
    void aprobadaSobrePendienteConMontoIgualApruebaYAvisa() throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);

        recibir("APPROVED", "tx-1", pago, ref, "CARD");

        PagoResponseDTO leido = leer(pago.id());
        assertThat(leido.estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(leido.metodo()).isEqualTo("TARJETA");
        assertThat(leido.fechaPago()).isEqualTo(HOY);
        assertThat(leido.idTransaccionWompi()).isEqualTo("tx-1");
        assertThat(leido.referenciaWompi()).isEqualTo(ref);
        assertThat(leido.numeroComprobante()).matches("FD-2026-\\d{6}");
        assertThat(avisos()).containsExactly("Tu pago de Mensualidad noviembre 2026 fue aprobado");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEQUI:NEQUI", "PSE:PSE", "BANCOLOMBIA_QR:QR", "BANCOLOMBIA_TRANSFER:BOTON_BANCOLOMBIA", "DAVIPLATA:WOMPI_OTRO",
            "PCOL:WOMPI_OTRO", "UN_MEDIO_NUEVO:WOMPI_OTRO"})
    void elMedioDeWompiSeMapeaYUnoDesconocidoCaeEnWompiOtro(String par) throws Exception {
        String[] medios = par.split(":");
        PagoResponseDTO pago = cobro();
        recibir("APPROVED", "tx-1", pago, iniciar(pago, acudiente), medios[0]);
        assertThat(leer(pago.id()).metodo()).isEqualTo(medios[1]);
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.APROBADO); // nunca se pierde un pago aprobado
    }

    @Test
    void montoDistintoNoApruebaYQuedaEnAuditoria() throws Exception {
        PagoResponseDTO pago = cobro();
        recibir(evento("APPROVED", "tx-1", 8_999_900, iniciar(pago, acudiente), "CARD"));

        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(leer(pago.id()).numeroComprobante()).isNull();
        assertThat(eventosDeAuditoria()).containsExactly("PAGO_MONTO_DISTINTO");
        assertThat(avisos()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECLINED", "ERROR", "VOIDED"})
    void noAprobadaSobrePendienteRechazaYAvisaSinCrearCobroNuevo(String estado) throws Exception {
        PagoResponseDTO pago = cobro();
        recibir(estado, "tx-1", pago, iniciar(pago, acudiente), "CARD");

        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(avisos()).containsExactly("Tu pago fue rechazado, puedes intentarlo de nuevo");
        assertThat(cantidadDePagos()).isEqualTo(1);
    }

    @Test
    void declinedYLuegoApprovedConLaMismaReferenciaYOtroIdQuedaAprobado() throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);

        recibir("DECLINED", "tx-1", pago, ref, "CARD");
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.RECHAZADO);
        recibir("APPROVED", "tx-2", pago, ref, "NEQUI"); // el reintento del checkout, con otro medio

        PagoResponseDTO leido = leer(pago.id());
        assertThat(leido.estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(leido.idTransaccionWompi()).isEqualTo("tx-2");
        assertThat(leido.metodo()).isEqualTo("NEQUI");
        assertThat(cantidadDePagos()).isEqualTo(1);
    }

    @Test
    void aprobadoTardioRechazaAlHermanoPendienteYApruebaElOriginal() throws Exception {
        PagoResponseDTO original = cobro();
        String ref = iniciar(original, acudiente);
        recibir("DECLINED", "tx-1", original, ref, "CARD");
        MiPagoResponseDTO hermano = servicio.reintentar(acudiente, original.id()); // nuevo PENDIENTE del mismo mes
        assertThat(hermano.estado()).isEqualTo(EstadoPago.PENDIENTE);

        recibir("APPROVED", "tx-2", original, ref, "CARD"); // el checkout aprobó con la referencia original

        assertThat(leer(original.id()).estado()).isEqualTo(EstadoPago.APROBADO);
        PagoResponseDTO reemplazado = leer(hermano.id());
        assertThat(reemplazado.estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(reemplazado.notaInterna()).isEqualTo("Reemplazado: el pago original fue aprobado por reintento");
        assertThat(eventosDeAuditoria()).containsExactly("PAGO_REEMPLAZADO");
    }

    @Test
    void aprobadoTardioConUnHermanoYaAprobadoNoCambiaNadaYAudita() throws Exception {
        PagoResponseDTO original = cobro();
        String refOriginal = iniciar(original, acudiente);
        recibir("DECLINED", "tx-1", original, refOriginal, "CARD");
        MiPagoResponseDTO hermano = servicio.reintentar(acudiente, original.id());
        String refHermano = servicio.iniciarWompi(acudiente, hermano.id()).referencia();
        recibir(evento("APPROVED", "tx-hermano", 9_000_000, refHermano, "CARD"));
        assertThat(leer(hermano.id()).estado()).isEqualTo(EstadoPago.APROBADO);

        recibir(evento("APPROVED", "tx-original", 9_000_000, refOriginal, "CARD")); // llega tarde la del cobro viejo

        assertThat(leer(original.id()).estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(leer(original.id()).idTransaccionWompi()).isNull();
        assertThat(leer(hermano.id()).estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(eventosDeAuditoria()).containsExactly("PAGO_POSIBLE_DUPLICADO");
    }

    @Test
    void elEventoRepetidoSeIgnora() throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        recibir("APPROVED", "tx-1", pago, ref, "CARD");
        String comprobante = leer(pago.id()).numeroComprobante();

        recibir("APPROVED", "tx-1", pago, ref, "CARD");
        recibir("APPROVED", "tx-1", pago, ref, "CARD");

        assertThat(leer(pago.id()).numeroComprobante()).isEqualTo(comprobante);
        assertThat(avisos()).hasSize(1);
        assertThat(eventosDeAuditoria()).isEmpty();
    }

    @Test
    void otraTransaccionAprobadaSobreUnCobroAprobadoEsUnPosibleDuplicado() throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        recibir("APPROVED", "tx-1", pago, ref, "CARD");

        recibir("APPROVED", "tx-2", pago, ref, "PSE");

        PagoResponseDTO leido = leer(pago.id());
        assertThat(leido.estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(leido.idTransaccionWompi()).isEqualTo("tx-1");
        assertThat(leido.metodo()).isEqualTo("TARJETA");
        assertThat(eventosDeAuditoria()).containsExactly("PAGO_POSIBLE_DUPLICADO");
    }

    @Test
    void voidedSobreAprobadoConElMismoIdLoPasaARechazadoYAudita() throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        recibir("APPROVED", "tx-1", pago, ref, "CARD");

        recibir("VOIDED", "tx-1", pago, ref, "CARD");

        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(eventosDeAuditoria()).containsExactly("PAGO_ANULADO_WOMPI");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECLINED", "ERROR", "VOIDED"})
    void unaNoAprobadaDeOtraTransaccionSobreUnCobroAprobadoSeIgnora(String estado) throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        recibir("APPROVED", "tx-1", pago, ref, "CARD");

        recibir(estado, "tx-otra", pago, ref, "NEQUI");

        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(eventosDeAuditoria()).isEmpty();
    }

    @Test
    void unaNoAprobadaSobreUnCobroYaRechazadoSeIgnora() throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        recibir("DECLINED", "tx-1", pago, ref, "CARD");
        recibir("ERROR", "tx-2", pago, ref, "CARD");
        recibir("VOIDED", "tx-3", pago, ref, "CARD");

        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(avisos()).hasSize(1);
    }

    @Test
    void pendingNoHaceNada() throws Exception {
        PagoResponseDTO pago = cobro();
        recibir("PENDING", "tx-1", pago, iniciar(pago, acudiente), "PSE");
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(avisos()).isEmpty();
        assertThat(eventosDeAuditoria()).isEmpty();
    }

    @Test
    void unaReferenciaDesconocidaSeIgnoraSinError() throws Exception {
        PagoResponseDTO pago = cobro();
        recibir(evento("APPROVED", "tx-1", 9_000_000, "FD-00000000-0000-0000-0000-000000000000", "CARD"));
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(cantidadDePagos()).isEqualTo(1);
    }

    // === Firma del webhook ==================================================================================

    @Test
    void unEventoConLaFirmaAlteradaSeRechazaYNoTocaNada() throws Exception {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        Evento bueno = evento("APPROVED", "tx-1", 9_000_000, ref, "CARD");

        // monto cambiado después de firmar
        String alterado = bueno.cuerpo().replace("9000000", "100");
        assertThatThrownBy(() -> transaccion.executeWithoutResult(s -> webhook.procesarEvento(alterado, bueno.checksum())))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
        // encabezado distinto, ausente o vacío
        for (String encabezado : new String[] {"0".repeat(64), null, ""}) {
            assertThatThrownBy(() -> transaccion.executeWithoutResult(s -> webhook.procesarEvento(bueno.cuerpo(), encabezado)))
                    .isInstanceOf(FirmaWebhookInvalidaException.class);
        }
        // otra lista de propiedades con el checksum de la lista de siempre
        String otraLista = bueno.cuerpo().replace("\"transaction.amount_in_cents\"", "\"transaction.reference\"");
        assertThatThrownBy(() -> transaccion.executeWithoutResult(s -> webhook.procesarEvento(otraLista, bueno.checksum())))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
        // un cuerpo que no es JSON
        assertThatThrownBy(() -> transaccion.executeWithoutResult(s -> webhook.procesarEvento("no es json", "x")))
                .isInstanceOf(FirmaWebhookInvalidaException.class);

        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(eventosDeAuditoria()).isEmpty();
    }

    @Test
    void conFirmaValidaUnEventoQueNoEsTransactionUpdatedSeIgnora() throws Exception {
        PagoResponseDTO pago = cobro();
        Evento e = evento("APPROVED", "tx-1", 9_000_000, iniciar(pago, acudiente), "CARD");
        recibir(new Evento(e.cuerpo().replace("transaction.updated", "nequi_token.updated"), e.checksum()));
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void sinElSecretoDeEventosElWebhookResponde503() throws Exception {
        PagoResponseDTO pago = cobro();
        Evento e = evento("APPROVED", "tx-1", 9_000_000, iniciar(pago, acudiente), "CARD");
        PagoWompiServiceImpl sinSecreto = armarWebhook(SIN_LLAVES);
        assertThatThrownBy(() -> transaccion.executeWithoutResult(s -> sinSecreto.procesarEvento(e.cuerpo(), e.checksum())))
                .isInstanceOf(PagosNoConfiguradosException.class);
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
    }

    // === Iniciar, reintentar y conciliar ====================================================================

    @Test
    void iniciarWompiDevuelveElMontoDeLaBaseYLaFirmaDeIntegridad() throws Exception {
        PagoResponseDTO pago = cobro();
        IniciarWompiResponseDTO r = servicio.iniciarWompi(acudiente, pago.id());

        assertThat(r.llavePublica()).isEqualTo("pub_test_publica");
        assertThat(r.referencia()).isEqualTo("FD-" + pago.id());
        assertThat(r.montoEnCentavos()).isEqualTo(9_000_000L);
        assertThat(r.moneda()).isEqualTo("COP");
        assertThat(r.firmaIntegridad()).isEqualTo(sha256("FD-" + pago.id() + "9000000COP" + SECRETO_INTEGRIDAD));
        assertThat(r.urlRedireccion()).isEqualTo("https://club.test/pagos/resultado");
        assertThat(r.toString()).doesNotContain("prv_test", SECRETO_INTEGRIDAD, SECRETO_EVENTOS);
    }

    @Test
    void iniciarWompiReutilizaLaReferencia() {
        PagoResponseDTO pago = cobro();
        assertThat(servicio.iniciarWompi(acudiente, pago.id()).referencia()).isEqualTo(servicio.iniciarWompi(acudiente, pago.id()).referencia());
        assertThat(leer(pago.id()).referenciaWompi()).isEqualTo("FD-" + pago.id());
    }

    @Test
    void unAcudienteNoIniciaElPagoDeOtroDeportistaYRecibe404() {
        PagoResponseDTO pago = cobro();
        assertThatThrownBy(() -> servicio.iniciarWompi(otroAcudiente, pago.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.iniciarWompi(otroAcudiente, UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(leer(pago.id()).referenciaWompi()).isNull();
    }

    @Test
    void soloSeIniciaUnCobroPendiente() throws Exception {
        PagoResponseDTO pago = cobro();
        recibir("DECLINED", "tx-1", pago, iniciar(pago, acudiente), "CARD");
        assertThatThrownBy(() -> servicio.iniciarWompi(acudiente, pago.id())).isInstanceOf(ConflictoException.class);
    }

    @Test
    void sinLlavesIniciarYConciliarResponden503() {
        PagoResponseDTO pago = cobro();
        PagoServiceImpl sinLlaves = armarServicio(SIN_LLAVES, new MensualidadProperties(90_000, 5, false));
        assertThatThrownBy(() -> sinLlaves.iniciarWompi(acudiente, pago.id())).isInstanceOf(PagosNoConfiguradosException.class);
        assertThatThrownBy(() -> sinLlaves.conciliar(admin, pago.id())).isInstanceOf(PagosNoConfiguradosException.class);
    }

    @Test
    void conLlavesDeProduccionYUrlDeSandboxTambienResponde503() {
        var mezcla = new WompiProperties("pub_prod_a", "prv_prod_b", "prod_events_c", "prod_integrity_d", WompiProperties.SANDBOX, "");
        PagoServiceImpl s = armarServicio(mezcla, new MensualidadProperties(90_000, 5, false));
        assertThatThrownBy(() -> s.iniciarWompi(acudiente, cobro().id())).isInstanceOf(PagosNoConfiguradosException.class);
    }

    @Test
    void reintentarCreaUnCobroNuevoIgualYEsIdempotente() throws Exception {
        PagoResponseDTO original = cobro();
        recibir("DECLINED", "tx-1", original, iniciar(original, acudiente), "CARD");

        MiPagoResponseDTO nuevo = servicio.reintentar(acudiente, original.id());
        MiPagoResponseDTO otraVez = servicio.reintentar(acudiente, original.id());

        assertThat(nuevo.id()).isNotEqualTo(original.id()).isEqualTo(otraVez.id());
        assertThat(nuevo.estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(nuevo).extracting(MiPagoResponseDTO::tipo, MiPagoResponseDTO::concepto, MiPagoResponseDTO::monto, MiPagoResponseDTO::periodo,
                MiPagoResponseDTO::fechaVencimiento, MiPagoResponseDTO::idDeportista)
                .containsExactly(original.tipo(), original.concepto(), original.monto(), original.periodo(), original.fechaVencimiento(), deportista);
        assertThat(cantidadDePagos()).isEqualTo(2);
        // y el nuevo tiene su propia referencia
        assertThat(servicio.iniciarWompi(acudiente, nuevo.id()).referencia()).isEqualTo("FD-" + nuevo.id()).isNotEqualTo("FD-" + original.id());
    }

    @Test
    void reintentarSoloSobreUnCobroRechazadoYPropio() throws Exception {
        PagoResponseDTO pago = cobro();
        assertThatThrownBy(() -> servicio.reintentar(acudiente, pago.id())).isInstanceOf(ConflictoException.class); // PENDIENTE
        recibir("DECLINED", "tx-1", pago, iniciar(pago, acudiente), "CARD");
        assertThatThrownBy(() -> servicio.reintentar(otroAcudiente, pago.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(cantidadDePagos()).isEqualTo(1);
    }

    @Test
    void conciliarAplicaLasReglasDelWebhookSobreLoQueDiceWompi() {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        transaccionesDeWompi.add(new TransaccionWompi("tx-1", "DECLINED", 9_000_000, ref, "CARD"));
        transaccionesDeWompi.add(new TransaccionWompi("tx-2", "APPROVED", 9_000_000, ref, "BANCOLOMBIA_QR"));
        transaccionesDeWompi.add(new TransaccionWompi("tx-ajena", "APPROVED", 1, "FD-otra", "CARD")); // de otra referencia: se ignora

        PagoResponseDTO r = servicio.conciliar(admin, pago.id());

        assertThat(r.estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(r.idTransaccionWompi()).isEqualTo("tx-2");
        assertThat(r.metodo()).isEqualTo("QR");
        assertThat(eventosDeAuditoria()).contains("PAGO_CONCILIADO");
        // conciliar otra vez no cambia nada
        assertThat(servicio.conciliar(admin, pago.id()).numeroComprobante()).isEqualTo(r.numeroComprobante());
    }

    @Test
    void conciliarNoApruebaUnMontoDistinto() {
        PagoResponseDTO pago = cobro();
        String ref = iniciar(pago, acudiente);
        transaccionesDeWompi.add(new TransaccionWompi("tx-1", "APPROVED", 100, ref, "CARD"));
        assertThat(servicio.conciliar(admin, pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(eventosDeAuditoria()).contains("PAGO_MONTO_DISTINTO");
    }

    @Test
    void conciliarSinReferenciaOConWompiCaido() {
        PagoResponseDTO pago = cobro();
        assertThatThrownBy(() -> servicio.conciliar(admin, pago.id())).isInstanceOf(ConflictoException.class);
        iniciar(pago, acudiente);
        wompiCaido = true;
        assertThatThrownBy(() -> servicio.conciliar(admin, pago.id())).isInstanceOf(WompiNoDisponibleException.class);
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
    }

    // === Aprobar, rechazar y comprobante ====================================================================

    @Test
    void aprobarUnPagoManualExigeFechaYMetodoYAsignaComprobante() {
        PagoResponseDTO pago = cobro();
        assertThatThrownBy(() -> servicio.aprobar(admin, pago.id(), new AprobarPagoRequestDTO(null, null, null)))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("fechaPago").hasMessageContaining("metodo");

        PagoResponseDTO r = servicio.aprobar(admin, pago.id(), new AprobarPagoRequestDTO(LocalDate.of(2026, 11, 8), MetodoPago.EFECTIVO, "Pagó en sede"));

        assertThat(r.estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(r.metodo()).isEqualTo("EFECTIVO");
        assertThat(r.fechaPago()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(r.numeroComprobante()).matches("FD-2026-\\d{6}");
        assertThat(r.referenciaWompi()).isNull();
        assertThat(eventosDeAuditoria()).containsExactly("PAGO_APROBADO_MANUAL");
    }

    @Test
    void aprobarManualNoAceptaMediosEnLineaNiFechasFuturasNiDosVeces() {
        PagoResponseDTO pago = cobro();
        assertThatThrownBy(() -> servicio.aprobar(admin, pago.id(), new AprobarPagoRequestDTO(HOY, MetodoPago.PSE, null)))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("metodo");
        assertThatThrownBy(() -> servicio.aprobar(admin, pago.id(), new AprobarPagoRequestDTO(HOY.plusDays(1), MetodoPago.EFECTIVO, null)))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("fechaPago");
        servicio.aprobar(admin, pago.id(), new AprobarPagoRequestDTO(HOY, MetodoPago.TRANSFERENCIA, null));
        assertThatThrownBy(() -> servicio.aprobar(admin, pago.id(), new AprobarPagoRequestDTO(HOY, MetodoPago.EFECTIVO, null)))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void unCobroYaIniciadoEnWompiNoSeApruebaAMano() {
        PagoResponseDTO pago = cobro();
        iniciar(pago, acudiente);
        assertThatThrownBy(() -> servicio.aprobar(admin, pago.id(), new AprobarPagoRequestDTO(HOY, MetodoPago.EFECTIVO, null)))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("Wompi");
        assertThat(leer(pago.id()).estado()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void rechazarSoloUnCobroPendiente() {
        PagoResponseDTO pago = cobro();
        PagoResponseDTO r = servicio.rechazar(admin, pago.id(), new RechazarPagoRequestDTO("Duplicado"));
        assertThat(r.estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(r.notaInterna()).isEqualTo("Duplicado");
        assertThatThrownBy(() -> servicio.rechazar(admin, pago.id(), null)).isInstanceOf(ConflictoException.class);
    }

    @Test
    void elComprobanteSoloExisteParaCobrosAprobadosYSoloLoVeElDueno() throws Exception {
        PagoResponseDTO pago = cobro();
        assertThatThrownBy(() -> servicio.comprobante(admin, true, pago.id())).isInstanceOf(ConflictoException.class);
        recibir("APPROVED", "tx-1", pago, iniciar(pago, acudiente), "CARD");

        byte[] delDueno = servicio.comprobante(acudiente, false, pago.id());
        byte[] delAdmin = servicio.comprobante(admin, true, pago.id());

        assertThat(new String(delDueno, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(new String(delAdmin, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThatThrownBy(() -> servicio.comprobante(otroAcudiente, false, pago.id())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // === Crear, listar y mis pagos ==========================================================================

    @Test
    void crearUnaMensualidadRepetidaEs409PeroOtrosTiposNo() {
        cobro();
        assertThatThrownBy(this::cobro).isInstanceOf(ConflictoException.class).hasMessageContaining("periodo");
        PagoResponseDTO torneo = servicio.crear(admin, new CrearPagoRequestDTO(deportista, TipoPago.TORNEO, "Copa Prado", 50_000L, HOY.plusDays(20), null));
        PagoResponseDTO uniforme = servicio.crear(admin, new CrearPagoRequestDTO(deportista, TipoPago.UNIFORME, "Uniforme", 120_000L, HOY.plusDays(20), null));
        assertThat(torneo.periodo()).isNull();
        assertThat(uniforme.tipo()).isEqualTo(TipoPago.UNIFORME);
        assertThat(cantidadDePagos()).isEqualTo(3);
    }

    @Test
    void crearValidaLosDatosYElDeportista() {
        assertThatThrownBy(() -> servicio.crear(admin, new CrearPagoRequestDTO(null, null, "", 0L, null, null)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("idDeportista").hasMessageContaining("tipo").hasMessageContaining("monto")
                .hasMessageContaining("fechaVencimiento").hasMessageContaining("concepto");
        assertThatThrownBy(() -> servicio.crear(admin, new CrearPagoRequestDTO(deportista, TipoPago.MENSUALIDAD, null, 90_000L, HOY, null)))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("periodo");
        assertThatThrownBy(() -> servicio.crear(admin, new CrearPagoRequestDTO(UUID.randomUUID(), TipoPago.UNIFORME, "x", 1L, HOY, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listarFiltraPaginaOrdenaYMarcaVencidos() {
        servicio.crear(admin, new CrearPagoRequestDTO(deportista, TipoPago.MENSUALIDAD, null, 90_000L, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1)));
        cobro();
        servicio.crear(admin, new CrearPagoRequestDTO(deportista, TipoPago.UNIFORME, "Uniforme", 120_000L, HOY.plusDays(20), null));

        PaginaResponseDTO<PagoResponseDTO> todos = servicio.listar(new FiltroPagos(null, null, null, null), "vencimiento,asc", 0, 2);
        assertThat(todos.totalElements()).isEqualTo(3);
        assertThat(todos.content()).hasSize(2).extracting(PagoResponseDTO::concepto).containsExactly("Mensualidad octubre 2026", "Mensualidad noviembre 2026");
        assertThat(todos.content().get(0).vencido()).isTrue();   // vencía el 5 de octubre
        assertThat(todos.content().get(1).vencido()).isFalse();  // vence el 5 de noviembre; hoy (reloj de la base) puede variar, no se afirma
        assertThat(todos.number()).isZero();
        assertThat(todos.size()).isEqualTo(2);
        assertThat(servicio.listar(new FiltroPagos(null, null, null, null), "vencimiento,asc", 1, 2).content()).hasSize(1);

        assertThat(servicio.listar(new FiltroPagos(null, TipoPago.UNIFORME, null, null), null, 0, 20).content()).hasSize(1);
        assertThat(servicio.listar(new FiltroPagos(EstadoPago.APROBADO, null, null, null), null, 0, 20).content()).isEmpty();
        assertThat(servicio.listar(new FiltroPagos(null, null, deportista, NOVIEMBRE), null, 0, 20).content()).hasSize(1);
        assertThat(servicio.listar(new FiltroPagos(null, null, UUID.randomUUID(), null), null, 0, 20).totalElements()).isZero();
    }

    @Test
    void listarRechazaOrdenesYTamanosInvalidosYNoPermiteInyectarElOrden() {
        FiltroPagos sin = new FiltroPagos(null, null, null, null);
        for (String sort : new String[] {"monto;DROP TABLE pago", "contrasena,asc", "monto,sideways", "monto,asc,mas", "p.monto"}) {
            assertThatThrownBy(() -> servicio.listar(sin, sort, 0, 20)).isInstanceOf(DatosInvalidosException.class);
        }
        assertThatThrownBy(() -> servicio.listar(sin, null, -1, 20)).isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.listar(sin, null, 0, 0)).isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.listar(sin, null, 0, 101)).isInstanceOf(DatosInvalidosException.class);
        assertThat(cantidadDePagos()).isZero();
    }

    @Test
    void misPagosSoloMuestraLosDeMisDeportistasSinDatosInternos() throws Exception {
        PagoResponseDTO mio = cobro();
        UUID deOtro = jdbc.queryForObject("SELECT id_deportista FROM acudiente_deportista ad JOIN acudiente a ON a.id_acudiente = ad.id_acudiente WHERE a.id_usuario = :u",
                Map.of("u", otroAcudiente), UUID.class);
        mensualidad(deOtro, NOVIEMBRE, 80_000);

        List<MiPagoResponseDTO> mis = servicio.misPagos(acudiente);
        assertThat(mis).extracting(MiPagoResponseDTO::id).containsExactly(mio.id());
        assertThat(mis.get(0).tieneComprobante()).isFalse();

        recibir("APPROVED", "tx-1", mio, iniciar(mio, acudiente), "CARD");
        assertThat(servicio.misPagos(acudiente).get(0).tieneComprobante()).isTrue();
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(servicio.misPagos(acudiente))).doesNotContain("referencia", "notaInterna", "tx-1", "registradoPor");
    }

    // === Mensualidades automáticas ==========================================================================

    @Test
    void generarMensualidadesCreaUnaPorDeportistaYEsIdempotente() {
        ResultadoGeneracionDTO primera = servicio.generarMensualidades(admin, YearMonth.of(2026, 12));
        assertThat(primera.creadas()).isEqualTo(2);
        assertThat(primera.yaExistentes()).isZero();
        assertThat(primera.periodo()).isEqualTo("2026-12");

        ResultadoGeneracionDTO segunda = servicio.generarMensualidades(admin, YearMonth.of(2026, 12));
        assertThat(segunda.creadas()).isZero();
        assertThat(segunda.yaExistentes()).isEqualTo(2);
        assertThat(cantidadDePagos()).isEqualTo(2);

        PagoResponseDTO generada = servicio.listar(new FiltroPagos(null, TipoPago.MENSUALIDAD, deportista, null), null, 0, 10).content().get(0);
        assertThat(generada).extracting(PagoResponseDTO::monto, PagoResponseDTO::periodo, PagoResponseDTO::fechaVencimiento, PagoResponseDTO::concepto,
                PagoResponseDTO::estado).containsExactly(90_000L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                "Mensualidad diciembre 2026", EstadoPago.PENDIENTE);
    }

    @Test
    void generarMensualidadesRespetaLasQueYaExistenYNoCuentaLasRechazadas() {
        PagoResponseDTO existente = mensualidad(deportista, LocalDate.of(2026, 12, 1), 95_000);
        assertThat(servicio.generarMensualidades(admin, YearMonth.of(2026, 12)).creadas()).isEqualTo(1);
        assertThat(leer(existente.id()).monto()).isEqualTo(95_000); // no se pisa

        servicio.rechazar(admin, existente.id(), null);
        assertThat(servicio.generarMensualidades(admin, YearMonth.of(2026, 12)).creadas()).isEqualTo(1); // el rechazado no cuenta
    }

    @Test
    void sinMontoConfiguradoNoGeneraNada() {
        PagoServiceImpl sinMonto = armarServicio(WOMPI, new MensualidadProperties(0, 5, false));
        assertThatThrownBy(() -> sinMonto.generarMensualidades(admin, YearMonth.of(2026, 12))).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.generarMensualidades(admin, null)).isInstanceOf(DatosInvalidosException.class);
        assertThat(cantidadDePagos()).isZero();
    }

    @Test
    void elDiaDeVencimientoSeAjustaAlFinDeMesCorto() {
        PagoServiceImpl dia31 = armarServicio(WOMPI, new MensualidadProperties(90_000, 31, false));
        dia31.generarMensualidades(admin, YearMonth.of(2027, 2));
        assertThat(dia31.listar(new FiltroPagos(null, null, null, null), null, 0, 10).content().get(0).fechaVencimiento())
                .isEqualTo(LocalDate.of(2027, 2, 28));
    }
}
