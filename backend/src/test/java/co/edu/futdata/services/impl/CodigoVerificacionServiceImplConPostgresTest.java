package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.domain.entities.CodigoVerificacion;
import co.edu.futdata.domain.rules.GeneradorCodigo;
import co.edu.futdata.domain.rules.ReglasVerificacion;
import co.edu.futdata.persistence.dao.ICodigoVerificacionDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.persistence.impl.CodigoVerificacionDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.persistence.impl.SesionVerificadaDAOImpl;
import co.edu.futdata.persistence.impl.UsuarioDAOImpl;
import co.edu.futdata.services.facade.CodigoNoEnviadoException;
import co.edu.futdata.services.facade.CodigoVerificacionException;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * El servicio de 2FA con los DAO reales sobre PostgreSQL y con {@code @Transactional} aplicado de verdad (un contexto
 * de Spring con proxy transaccional). Con mocks no se ve si una excepción deshace el contador de intentos.
 */
@ExtendWith(DockerDisponible.class)
class CodigoVerificacionServiceImplConPostgresTest {

    private static final AtomicBoolean CORREO_SALE = new AtomicBoolean(true);
    private static final String CODIGO_REAL = "123456";
    private static final String CODIGO_INCORRECTO = "000000";

    @Configuration
    @EnableTransactionManagement
    static class Config {
        /** El pool compartido de PostgresDePrueba: Spring no debe cerrarlo al cerrar el contexto. */
        @Bean(destroyMethod = "")
        DataSource dataSource() {
            return PostgresDePrueba.dataSource();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        NamedParameterJdbcTemplate jdbc(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        ICodigoVerificacionDAO codigos(NamedParameterJdbcTemplate jdbc) {
            return new CodigoVerificacionDAOImpl(jdbc);
        }

        @Bean
        ISesionVerificadaDAO sesiones(NamedParameterJdbcTemplate jdbc) {
            return new SesionVerificadaDAOImpl(jdbc);
        }

        @Bean
        ICodigoVerificacionService servicio(NamedParameterJdbcTemplate jdbc, ICodigoVerificacionDAO codigos,
                                            ISesionVerificadaDAO sesiones) {
            IUsuarioDAO usuarios = new UsuarioDAOImpl(jdbc);
            INotificador notificador = (evento, datos) -> {
                if (!CORREO_SALE.get()) {
                    throw new NotificacionNoEnviadaException("n8n no responde", evento, null, null);
                }
            };
            return new CodigoVerificacionServiceImpl(usuarios, codigos, sesiones, notificador, new GeneradorCodigo(),
                    Clock.systemUTC());
        }
    }

    private AnnotationConfigApplicationContext contexto;
    private ICodigoVerificacionService servicio;
    private ICodigoVerificacionDAO codigos;
    private ISesionVerificadaDAO sesiones;
    private NamedParameterJdbcTemplate jdbc;
    private UUID idUsuario;

    @BeforeEach
    void prepararBase() {
        CORREO_SALE.set(true);
        contexto = new AnnotationConfigApplicationContext(Config.class);
        servicio = contexto.getBean(ICodigoVerificacionService.class);
        codigos = contexto.getBean(ICodigoVerificacionDAO.class);
        sesiones = contexto.getBean(ISesionVerificadaDAO.class);
        jdbc = PostgresDePrueba.jdbc();
        idUsuario = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", idUsuario));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, 'Ana', 'Pérez', :correo)",
                Map.of("id", idUsuario, "correo", idUsuario + "@pradofc.co"));
        jdbc.update("INSERT INTO usuario_rol (id_usuario, id_rol) SELECT :id, id_rol FROM rol WHERE nombre = 'ENTRENADOR'",
                Map.of("id", idUsuario));
    }

    @AfterEach
    void limpiarBase() {
        contexto.close();
        jdbc.update("DELETE FROM usuario WHERE id_usuario = :id", Map.of("id", idUsuario));
        jdbc.update("DELETE FROM auth.users WHERE id = :id", Map.of("id", idUsuario));
    }

    private void codigoPendiente() {
        CodigoVerificacion codigo = new CodigoVerificacion();
        codigo.setUsuarioId(idUsuario);
        codigo.setCodigoHash(new GeneradorCodigo().hash(CODIGO_REAL));
        codigo.setExpiraEn(Instant.now().plusSeconds(600));
        codigo.setUltimoEnvio(Instant.now());
        codigos.guardar(codigo);
    }

    private int intentos() {
        return jdbc.queryForObject("SELECT intentos FROM codigo_verificacion WHERE id_usuario = :id",
                Map.of("id", idUsuario), Integer.class);
    }

    private boolean bloqueado() {
        return jdbc.queryForObject("SELECT bloqueado_hasta IS NOT NULL FROM codigo_verificacion WHERE id_usuario = :id",
                Map.of("id", idUsuario), Boolean.class);
    }

    @Test
    void unCodigoIncorrectoConservaElIntentoAunqueSeLanceLaExcepcion() {
        codigoPendiente();

        assertThatThrownBy(() -> servicio.verificar(idUsuario, UUID.randomUUID(), CODIGO_INCORRECTO))
                .isInstanceOfSatisfying(CodigoVerificacionException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("INCORRECTO"));

        assertThat(intentos()).isEqualTo(1);
    }

    @Test
    void alQuintoIncorrectoQuedaBloqueadoYElSiguienteIntentoResponderBloqueado() {
        codigoPendiente();

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> servicio.verificar(idUsuario, UUID.randomUUID(), CODIGO_INCORRECTO))
                    .isInstanceOfSatisfying(CodigoVerificacionException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo("INCORRECTO"));
        }
        assertThatThrownBy(() -> servicio.verificar(idUsuario, UUID.randomUUID(), CODIGO_INCORRECTO))
                .isInstanceOfSatisfying(CodigoVerificacionException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("BLOQUEADO"));

        assertThat(intentos()).isEqualTo(5);
        assertThat(bloqueado()).isTrue();
        // Ni siquiera el código real pasa mientras dura el bloqueo.
        assertThatThrownBy(() -> servicio.verificar(idUsuario, UUID.randomUUID(), CODIGO_REAL))
                .isInstanceOfSatisfying(CodigoVerificacionException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("BLOQUEADO"));
    }

    @Test
    void siElCorreoNoSaleNoQuedaUnCodigoGuardado() {
        CORREO_SALE.set(false);

        assertThatThrownBy(() -> servicio.solicitar(idUsuario)).isInstanceOf(CodigoNoEnviadoException.class);

        Integer filas = jdbc.queryForObject("SELECT count(*) FROM codigo_verificacion WHERE id_usuario = :id",
                Map.of("id", idUsuario), Integer.class);
        assertThat(filas).isZero();
    }

    @Test
    void verificarEnLaSesionANoHabilitaLaSesionBDelMismoUsuario() {
        UUID sesionA = UUID.randomUUID();
        UUID sesionB = UUID.randomUUID();
        codigoPendiente();

        servicio.verificar(idUsuario, sesionA, CODIGO_REAL);

        assertThat(sesiones.estaVerificada(sesionA, idUsuario, Instant.now())).isTrue();
        assertThat(sesiones.estaVerificada(sesionB, idUsuario, Instant.now())).isFalse();
        Instant expira = jdbc.queryForObject("SELECT expira_en FROM sesion_verificada WHERE id_sesion = :id",
                Map.of("id", sesionA), java.time.OffsetDateTime.class).toInstant();
        assertThat(expira).isBetween(Instant.now().plus(ReglasVerificacion.VIGENCIA_SESION).minusSeconds(60),
                Instant.now().plus(ReglasVerificacion.VIGENCIA_SESION));
    }

    @Test
    void elLogoutCierraSoloLaSesionDelToken() {
        UUID sesionA = UUID.randomUUID();
        UUID sesionB = UUID.randomUUID();
        codigoPendiente();
        servicio.verificar(idUsuario, sesionA, CODIGO_REAL);
        codigoPendiente();
        servicio.verificar(idUsuario, sesionB, CODIGO_REAL);

        servicio.cerrarSesion(idUsuario, sesionA);

        assertThat(sesiones.estaVerificada(sesionA, idUsuario, Instant.now())).isFalse();
        assertThat(sesiones.estaVerificada(sesionB, idUsuario, Instant.now())).isTrue();
    }

    @Test
    void elLogoutConLaSesionDeOtroUsuarioNoLaCierra() {
        UUID sesion = UUID.randomUUID();
        codigoPendiente();
        servicio.verificar(idUsuario, sesion, CODIGO_REAL);

        servicio.cerrarSesion(UUID.randomUUID(), sesion);

        assertThat(sesiones.estaVerificada(sesion, idUsuario, Instant.now())).isTrue();
    }
}
