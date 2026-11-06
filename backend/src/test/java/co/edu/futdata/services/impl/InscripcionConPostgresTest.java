package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.AuthNoDisponibleException;
import co.edu.futdata.datasource.external.CorreoYaRegistradoException;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.datasource.external.NotificacionRechazadaException;
import co.edu.futdata.datasource.external.SesionRecuperacion;
import co.edu.futdata.domain.entities.DatosAcudiente;
import co.edu.futdata.persistence.impl.AcudienteDAOImpl;
import co.edu.futdata.persistence.impl.DeportistaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.persistence.impl.SesionVerificadaDAOImpl;
import co.edu.futdata.persistence.impl.UsuarioDAOImpl;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La inscripción única de punta a punta con los DAO y el servicio reales sobre PostgreSQL: lo que queda en la base
 * cuando sale bien y, sobre todo, que cuando algo falla no queda nada a medias.
 */
@ExtendWith(DockerDisponible.class)
class InscripcionConPostgresTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final String CORREO = "insc-pg-ana@prueba.co";

    /**
     * Doble de Supabase Auth en memoria (nunca el real): crea y borra la fila de {@code auth.users} que exige la FK de
     * {@code usuario}, recuerda los correos para responder "ya existe" y se puede "caer".
     */
    static final class AuthEnMemoria implements ISupabaseAuthAdmin {
        private final NamedParameterJdbcTemplate jdbc;
        final Map<String, UUID> porCorreo = new ConcurrentHashMap<>();
        final List<UUID> borradas = Collections.synchronizedList(new ArrayList<>());
        volatile boolean caido;
        volatile boolean borrarFalla;

        AuthEnMemoria(NamedParameterJdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public synchronized UUID crearUsuario(String correo) {
            if (caido) throw new AuthNoDisponibleException("Supabase Auth no responde", 503);
            if (porCorreo.containsKey(correo.toLowerCase())) throw new CorreoYaRegistradoException(422);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", id));
            porCorreo.put(correo.toLowerCase(), id);
            return id;
        }

        @Override
        public synchronized void eliminar(UUID id) {
            if (borrarFalla) throw new AuthNoDisponibleException("Supabase Auth no responde", 503);
            jdbc.update("DELETE FROM auth.users WHERE id = :id", Map.of("id", id));
            porCorreo.values().remove(id);
            borradas.add(id);
        }

        @Override
        public String generarTokenRecuperacion(String correo) {
            if (caido) throw new AuthNoDisponibleException("Supabase Auth no responde", 503);
            return "token-de-prueba";
        }

        @Override
        public UUID invitar(String correo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cambiarAcceso(UUID idUsuario, boolean activo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SesionRecuperacion verificarTokenRecuperacion(String tokenHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fijarContrasena(UUID idUsuario, String contrasena) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cerrarSesion(String tokenAcceso) {
            throw new UnsupportedOperationException();
        }
    }

    /** n8n en memoria: anota cada aviso y puede fallar. */
    static final class N8nEnMemoria implements INotificador {
        final List<Map<String, Object>> enviados = Collections.synchronizedList(new ArrayList<>());
        volatile RuntimeException falla;

        @Override
        public void notificar(String evento, Map<String, Object> datos) {
            if (falla != null) throw falla;
            assertThat(evento).isEqualTo(INotificador.ENLACE_CONTRASENA);
            enviados.add(datos);
        }
    }

    private NamedParameterJdbcTemplate jdbc;
    private AcudienteDAOImpl acudientes;
    private AuthEnMemoria auth;
    private N8nEnMemoria n8n;
    private InscripcionServiceImpl servicio;
    private final Solicitante admin = new Solicitante(UUID.randomUUID(), Solicitante.ADMINISTRADOR);

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        acudientes = new AcudienteDAOImpl(jdbc);
        auth = new AuthEnMemoria(jdbc);
        n8n = new N8nEnMemoria();
        servicio = servicio(acudientes);
        // La categoría se calcula con la tabla categoria, que otras clases vacían: cada prueba pone las que usa
        UUID entrenador = PostgresDePrueba.entrenador("Insc", "Pg", "INSC-PG-1", "insc-pg-entrenador@prueba.co");
        PostgresDePrueba.categoria("Semillero", entrenador);
        PostgresDePrueba.categoria("Cachorros", entrenador);
    }

    @AfterEach
    void limpiarTodo() {
        // La base de pruebas se comparte con otras clases, que dejan cobros y avisos de sus deportistas
        for (String tabla : List.of("auditoria", "notificacion_pago", "pago", "documento", "acudiente_deportista", "acudiente",
                "deportista")) {
            jdbc.getJdbcOperations().update("DELETE FROM " + tabla);
        }
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'insc-pg-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
        PostgresDePrueba.limpiar();
    }

    private InscripcionServiceImpl servicio(AcudienteDAOImpl dao) {
        UsuarioDAOImpl usuarios = new UsuarioDAOImpl(jdbc);
        CuentasDeAcceso cuentas = new CuentasDeAcceso(auth, usuarios, new SesionVerificadaDAOImpl(jdbc), n8n,
                new FrontendProperties("https://futdata.test"));
        return new InscripcionServiceImpl(new DeportistaDAOImpl(jdbc), dao, usuarios, cuentas,
                new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource())), RELOJ);
    }

    // --- Lo que queda cuando sale bien -----------------------------------------------------------------------

    @Test
    void laInscripcionCompletaGuardaDeportistaAcudienteVinculoYCuentaYEnviaElEnlace() {
        InscripcionResultado r = servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);

        assertThat(cuenta("deportista")).isEqualTo(1);
        assertThat(cuenta("acudiente")).isEqualTo(1);
        assertThat(cuenta("acudiente_deportista")).isEqualTo(1);
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT a.nombre || '|' || a.correo || '|' || a.telefono || '|' || ad.parentesco
                  FROM acudiente a JOIN acudiente_deportista ad ON ad.id_acudiente = a.id_acudiente
                 WHERE ad.id_deportista = ?""", String.class, r.deportista().getId()))
                .isEqualTo("Ana|" + CORREO + "|3001234567|Madre");
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT count(*) FROM acudiente WHERE tipo_documento IS NULL AND documento_identidad IS NULL "
                        + "AND fecha_nacimiento IS NULL", Long.class)).isEqualTo(1);
        // La cuenta: auth.users, usuario con el rol ACUDIENTE y la ficha enlazada
        UUID idCuenta = auth.porCorreo.get(CORREO);
        assertThat(idCuenta).isNotNull();
        assertThat(r.acudiente().idUsuario()).isEqualTo(idCuenta);
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT u.nombre || '|' || u.apellido || '|' || u.correo || '|' || r.nombre
                  FROM usuario u JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario JOIN rol r ON r.id_rol = ur.id_rol
                 WHERE u.id_usuario = ?""", String.class, idCuenta)).isEqualTo("Ana|Ríos|" + CORREO + "|ACUDIENTE");
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT id_usuario FROM acudiente", UUID.class)).isEqualTo(idCuenta);
        // El enlace, después del commit, con la plantilla nueva
        assertThat(n8n.enviados).hasSize(1);
        assertThat(n8n.enviados.get(0)).containsEntry("plantilla", "BIENVENIDA_ACUDIENTE")
                .containsEntry("correo", CORREO).containsEntry("nombre", "Ana").containsEntry("vigenciaHoras", 1);
        assertThat((String) n8n.enviados.get(0).get("enlace")).startsWith("https://futdata.test/");
        assertThat(r.reutilizado()).isFalse();
        assertThat(r.cuentaCreada()).isTrue();
        assertThat(r.correoEnviado()).isTrue();
        assertThat(r.parentesco()).isEqualTo("Madre");
        assertThat(r.acudiente().correo()).isEqualTo(CORREO);
    }

    @Test
    void elHermanoReutilizaAcudienteYCuentaSinCrearOtraNiEnviarOtroCorreo() {
        InscripcionResultado primero = servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);
        InscripcionResultado segundo = servicio.inscribir(entrada("1234567891", CORREO, "Madre"), admin);

        assertThat(segundo.reutilizado()).isTrue();
        assertThat(segundo.cuentaCreada()).isFalse();
        assertThat(segundo.correoEnviado()).isNull();
        assertThat(segundo.acudiente().idUsuario()).isEqualTo(primero.acudiente().idUsuario());
        assertThat(auth.porCorreo).hasSize(1);
        assertThat(n8n.enviados).hasSize(1);
        assertThat(cuenta("usuario WHERE correo LIKE 'insc-pg-%'")).isEqualTo(1);
    }

    @Test
    void unCorreoDeUnaCuentaDeOtroRolEsUn409YNoSeCreaNada() {
        cuentaExistente("insc-pg-admin@prueba.co", "ADMINISTRADOR");

        assertThatThrownBy(() -> servicio.inscribir(entrada("1234567890", "INSC-PG-ADMIN@prueba.co", "Madre"), admin))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("acudiente.correo: ese correo ya pertenece a una cuenta con otro rol")
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("acudiente.correo"));

        assertThat(cuenta("deportista")).isZero();
        assertThat(cuenta("acudiente")).isZero();
        assertThat(auth.porCorreo).isEmpty();
        assertThat(n8n.enviados).isEmpty();
    }

    @Test
    void unaCuentaAcudienteSinFichaRecibeLaFichaSinTocarAuthNiEnviarCorreo() {
        UUID existente = cuentaExistente(CORREO, "ACUDIENTE");

        InscripcionResultado r = servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);

        assertThat(r.cuentaCreada()).isFalse();
        assertThat(r.reutilizado()).isFalse();
        assertThat(r.correoEnviado()).isNull();
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT id_usuario FROM acudiente", UUID.class))
                .isEqualTo(existente);
        assertThat(auth.porCorreo).isEmpty();
        assertThat(n8n.enviados).isEmpty();
    }

    @Test
    void unAcudienteSinCuentaRecibeUnaCuentaNuevaEnlazadaYElEnlace() {
        UUID ficha = acudientes.insertar("Ana", "Ríos", CORREO, "3001234567", null).id();

        InscripcionResultado r = servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);

        UUID idCuenta = auth.porCorreo.get(CORREO);
        assertThat(r.reutilizado()).isTrue();
        assertThat(r.cuentaCreada()).isTrue();
        assertThat(r.correoEnviado()).isTrue();
        assertThat(r.acudiente().id()).isEqualTo(ficha);
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT id_usuario FROM acudiente WHERE id_acudiente = ?",
                UUID.class, ficha)).isEqualTo(idCuenta);
        assertThat(cuenta("acudiente")).isEqualTo(1);
        assertThat(n8n.enviados).hasSize(1);
    }

    @Test
    void siAuthEstaCaidoEsUn503YLaBaseQuedaIntacta() {
        auth.caido = true;

        assertThatThrownBy(() -> servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin))
                .isInstanceOf(AuthNoDisponibleException.class);

        assertThat(cuenta("deportista")).isZero();
        assertThat(cuenta("acudiente")).isZero();
        assertThat(cuenta("usuario WHERE correo LIKE 'insc-pg-%'")).isZero();
        assertThat(n8n.enviados).isEmpty();
    }

    @Test
    void siLaTransaccionFallaLaCuentaRecienCreadaSeBorra() {
        AcudienteDAOImpl quePierdeElVinculo = new AcudienteDAOImpl(jdbc) {
            @Override
            public boolean vincular(UUID idAcudiente, UUID idDeportista, String parentesco) {
                throw new IllegalStateException("falla simulada en el vínculo");
            }
        };

        assertThatThrownBy(() -> servicio(quePierdeElVinculo).inscribir(entrada("1234567890", CORREO, "Madre"), admin))
                .isInstanceOf(IllegalStateException.class).hasMessage("falla simulada en el vínculo");

        assertThat(auth.borradas).hasSize(1);
        assertThat(auth.porCorreo).isEmpty();
        assertThat(cuenta("auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)")).isZero();
        assertThat(cuenta("usuario WHERE correo LIKE 'insc-pg-%'")).isZero();
        assertThat(cuenta("deportista")).isZero();
        assertThat(cuenta("acudiente")).isZero();
        assertThat(n8n.enviados).isEmpty();
    }

    @Test
    void siLaTransaccionYElBorradoFallanSaleLaExcepcionOriginal() {
        auth.borrarFalla = true;
        AcudienteDAOImpl quePierdeElVinculo = new AcudienteDAOImpl(jdbc) {
            @Override
            public boolean vincular(UUID idAcudiente, UUID idDeportista, String parentesco) {
                throw new IllegalStateException("falla simulada en el vínculo");
            }
        };

        assertThatThrownBy(() -> servicio(quePierdeElVinculo).inscribir(entrada("1234567890", CORREO, "Madre"), admin))
                .isInstanceOf(IllegalStateException.class).hasMessage("falla simulada en el vínculo");
        assertThat(cuenta("deportista")).isZero();
        assertThat(cuenta("usuario WHERE correo LIKE 'insc-pg-%'")).isZero();
        // la cuenta queda huérfana en Auth (se registra para borrarla a mano); la limpieza la quita
    }

    @Test
    void siElCorreoFallaTodoQuedaGuardadoYCorreoEnviadoEsFalso() {
        n8n.falla = new NotificacionRechazadaException("ENLACE_CONTRASENA",
                "plantilla debe ser una de: BIENVENIDA_ENTRENADOR");

        InscripcionResultado r = servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);

        assertThat(r.correoEnviado()).isFalse();
        assertThat(r.cuentaCreada()).isTrue();
        assertThat(cuenta("deportista")).isEqualTo(1);
        assertThat(cuenta("acudiente WHERE id_usuario IS NOT NULL")).isEqualTo(1);
        assertThat(cuenta("usuario WHERE correo LIKE 'insc-pg-%'")).isEqualTo(1);
        assertThat(auth.borradas).isEmpty();
    }

    @Test
    void laCategoriaSeDerivaDeLaFechaDeNacimiento_yElDeportistaNaceActivoConInscripcionPendiente() {
        // 2016 -> 10 años por año de nacimiento en 2026 -> Semillero
        InscripcionResultado r = servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);

        assertThat(r.deportista().getCategoria()).isEqualTo("Semillero");
        assertThat(r.deportista().isActivo()).isTrue();
        assertThat(r.deportista().getEstadoInscripcion()).isEqualTo("PENDIENTE");
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT activo FROM deportista WHERE id_deportista = ?", Boolean.class, r.deportista().getId())).isTrue();
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT count(*) FROM documento", Long.class)).isZero();
    }

    @Test
    void elGrupoDeCachorrosSeGuarda() {
        DeportistaRequestDTO cachorro = new DeportistaRequestDTO("Leo", "Ríos", "RC", "1098765432",
                LocalDate.of(2018, 6, 1), "3001234567", "Calle 1 # 2-3", "Delantero", "mañana", true);

        InscripcionResultado r = servicio.inscribir(new InscripcionEntrada(cachorro, acudiente(CORREO, "Padre")), admin);

        assertThat(r.deportista().getCategoria()).isEqualTo("Cachorros");
        assertThat(r.deportista().getGrupo()).isEqualTo("mañana");
        assertThat(r.deportista().getPosicion()).isEqualTo("Delantero");
    }

    @Test
    void dosHermanosCompartenAlMismoAcudientePorCorreoAunqueCambieLaCapitalizacion() {
        InscripcionResultado primero = servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);
        InscripcionResultado segundo = servicio.inscribir(
                entrada("1234567891", "  INSC-PG-ANA@Prueba.CO ", "Madre"), admin);

        assertThat(primero.reutilizado()).isFalse();
        assertThat(segundo.reutilizado()).isTrue();
        assertThat(segundo.acudiente().id()).isEqualTo(primero.acudiente().id());
        assertThat(cuenta("deportista")).isEqualTo(2);
        assertThat(cuenta("acudiente")).isEqualTo(1);
        assertThat(cuenta("acudiente_deportista")).isEqualTo(2);
    }

    @Test
    void elAcudienteReutilizadoConservaSusDatosYSeIgnoranLosNuevos() {
        servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);

        InscripcionResultado r = servicio.inscribir(new InscripcionEntrada(deportista("1234567891"),
                new DatosAcudiente("Otro Nombre", "Otro Apellido", CORREO, "3119999999", "Padre")), admin);

        assertThat(r.reutilizado()).isTrue();
        assertThat(r.acudiente().nombre()).isEqualTo("Ana");
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT nombre || '|' || apellido || '|' || telefono FROM acudiente", String.class))
                .isEqualTo("Ana|Ríos|3001234567");
        // el parentesco es del vínculo, no de la persona: el segundo hijo lleva el suyo
        assertThat(jdbc.getJdbcOperations().queryForList(
                "SELECT parentesco FROM acudiente_deportista ORDER BY parentesco", String.class))
                .containsExactly("Madre", "Padre");
    }

    @Test
    void tiaYTioSeGuardanSinTilde() {
        servicio.inscribir(entrada("1234567890", CORREO, "Tía"), admin);

        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT parentesco FROM acudiente_deportista", String.class))
                .isEqualTo("Tia");
    }

    // --- Cuando algo falla, la base queda intacta ---------------------------------------------------------------

    @Test
    void unDocumentoRepetidoDejaLaBaseIntacta() {
        servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);

        assertThatThrownBy(() -> servicio.inscribir(entrada("1234567890", "insc-pg-otra@prueba.co", "Padre"), admin))
                .isInstanceOf(ConflictoException.class)
                .satisfies(e -> assertThat(((ConflictoException) e).getCampos()).containsExactly("deportista.documento"));

        assertThat(cuenta("deportista")).isEqualTo(1);
        assertThat(cuenta("acudiente")).isEqualTo(1); // no se creó el acudiente de la segunda
        assertThat(cuenta("acudiente_deportista")).isEqualTo(1);
    }

    @Test
    void unDocumentoRepetidoConOtroTipoTambienEsDuplicado() {
        servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);
        DeportistaRequestDTO igualConOtroTipo = new DeportistaRequestDTO("Otro", "Niño", "RC", "1234567890",
                LocalDate.of(2016, 1, 1), "3001234567", "Calle 1 # 2-3");

        assertThatThrownBy(() -> servicio.inscribir(
                new InscripcionEntrada(igualConOtroTipo, acudiente("insc-pg-x@prueba.co", "Madre")), admin))
                .isInstanceOf(ConflictoException.class);
        assertThat(cuenta("deportista")).isEqualTo(1);
    }

    @Test
    void siFallaElVinculoSeRevierteElDeportistaYElAcudienteNuevo() {
        AcudienteDAOImpl quePierdeElVinculo = new AcudienteDAOImpl(jdbc) {
            @Override
            public boolean vincular(UUID idAcudiente, UUID idDeportista, String parentesco) {
                throw new IllegalStateException("falla simulada en el vínculo");
            }
        };

        assertThatThrownBy(() -> servicio(quePierdeElVinculo).inscribir(entrada("1234567890", CORREO, "Madre"), admin))
                .isInstanceOf(IllegalStateException.class);

        assertThat(cuenta("deportista")).isZero();
        assertThat(cuenta("acudiente")).isZero();
        assertThat(cuenta("acudiente_deportista")).isZero();
    }

    @Test
    void siFallaElVinculoConUnAcudienteExistenteSoloSeRevierteElDeportista() {
        servicio.inscribir(entrada("1234567890", CORREO, "Madre"), admin);
        AcudienteDAOImpl quePierdeElVinculo = new AcudienteDAOImpl(jdbc) {
            @Override
            public boolean vincular(UUID idAcudiente, UUID idDeportista, String parentesco) {
                throw new IllegalStateException("falla simulada en el vínculo");
            }
        };

        assertThatThrownBy(() -> servicio(quePierdeElVinculo).inscribir(entrada("1234567891", CORREO, "Madre"), admin))
                .isInstanceOf(IllegalStateException.class);

        assertThat(cuenta("deportista")).isEqualTo(1);
        assertThat(cuenta("acudiente")).isEqualTo(1);
        assertThat(cuenta("acudiente_deportista")).isEqualTo(1);
    }

    @Test
    void unaValidacionFallidaNoTocaLaBase() {
        DatosAcudiente malo = new DatosAcudiente("Ana", "Ríos", "no-es-un-correo", "3001234567", "Madre");

        assertThatThrownBy(() -> servicio.inscribir(new InscripcionEntrada(deportista("1234567890"), malo), admin))
                .isInstanceOf(DatosInvalidosException.class);

        assertThat(cuenta("deportista")).isZero();
        assertThat(cuenta("acudiente")).isZero();
    }

    @Test
    void unDeportistaFueraDeLosCuatroALosDieciseisAniosNoSeGuarda() {
        DeportistaRequestDTO grande = new DeportistaRequestDTO("Juan", "Ríos", "TI", "1234567890",
                LocalDate.of(2008, 1, 1), "3001234567", "Calle 1 # 2-3");

        assertThatThrownBy(() -> servicio.inscribir(new InscripcionEntrada(grande, acudiente(CORREO, "Padre")), admin))
                .isInstanceOf(DatosInvalidosException.class)
                .satisfies(e -> assertThat(((DatosInvalidosException) e).getCampos())
                        .containsExactly("deportista.fechaNacimiento"));
        assertThat(cuenta("deportista")).isZero();
    }

    @Test
    void otroRolNoInscribeYNoSeGuardaNada() {
        assertThatThrownBy(() -> servicio.inscribir(entrada("1234567890", CORREO, "Madre"),
                new Solicitante(UUID.randomUUID(), Solicitante.ACUDIENTE))).isInstanceOf(SinPermisoException.class);

        assertThat(cuenta("deportista")).isZero();
    }

    @Test
    void dosInscripcionesAlMismoTiempoConElMismoCorreoNuncaDejanDeportistasHuerfanos() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (String documento : List.of("1234567890", "1234567891")) {
                resultados.add(pool.submit(() -> {
                    listos.countDown();
                    listos.await();
                    try {
                        servicio.inscribir(entrada(documento, CORREO, "Madre"), admin);
                        return true;
                    } catch (ConflictoException e) {
                        assertThat(e.getCampos()).containsExactly("acudiente.correo");
                        return false;
                    }
                }));
            }
            long exitosas = 0;
            for (Future<Boolean> r : resultados) {
                if (r.get(30, TimeUnit.SECONDS)) {
                    exitosas++;
                }
            }
            // Puede que las dos salgan bien (la segunda reutiliza) o que una pida reintentar; nunca queda algo a medias
            assertThat(exitosas).isBetween(1L, 2L);
            assertThat(cuenta("acudiente")).isEqualTo(1);
            assertThat(cuenta("deportista")).isEqualTo(exitosas);
            assertThat(cuenta("acudiente_deportista")).isEqualTo(exitosas);
            // una sola cuenta y ninguna huérfana en Auth
            assertThat(cuenta("usuario WHERE correo LIKE 'insc-pg-%'")).isEqualTo(1);
            assertThat(auth.porCorreo).hasSize(1);
            assertThat(cuenta("auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)")).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    // --- Ayudas ----------------------------------------------------------------------------------------------

    /** Una cuenta ya existente (auth.users + usuario + un rol), sin ficha de acudiente. */
    private UUID cuentaExistente(String correo, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, 'Cuenta', 'Previa', :c)",
                Map.of("id", id, "c", correo));
        jdbc.update("INSERT INTO usuario_rol (id_usuario, id_rol) SELECT :id, id_rol FROM rol WHERE nombre = :rol",
                Map.of("id", id, "rol", rol));
        return id;
    }

    private long cuenta(String tabla) {
        return jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM " + tabla, Long.class);
    }

    private static InscripcionEntrada entrada(String documento, String correo, String parentesco) {
        return new InscripcionEntrada(deportista(documento), acudiente(correo, parentesco));
    }

    private static DeportistaRequestDTO deportista(String documento) {
        return new DeportistaRequestDTO("Mateo", "Ríos", "TI", documento, LocalDate.of(2016, 3, 3), "3001234567",
                "Calle 1 # 2-3");
    }

    private static DatosAcudiente acudiente(String correo, String parentesco) {
        return new DatosAcudiente("Ana", "Ríos", correo, "3001234567", parentesco);
    }
}
