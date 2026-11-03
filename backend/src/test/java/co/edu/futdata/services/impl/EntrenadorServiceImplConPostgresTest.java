package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.persistence.impl.CategoriaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.EntrenadorDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.persistence.impl.SesionVerificadaDAOImpl;
import co.edu.futdata.persistence.impl.UsuarioDAOImpl;
import co.edu.futdata.services.facade.ConflictoException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El servicio con los DAO reales sobre PostgreSQL (Testcontainers o una base local), cada llamada en su transacción
 * como lo hace {@code @Transactional}. Sin ninguno de los dos, se omite.
 */
@ExtendWith(DockerDisponible.class)
class EntrenadorServiceImplConPostgresTest {

    private EntrenadorServiceImpl servicio;
    private TransactionTemplate transaccion;
    private ISupabaseAuthAdmin auth;
    private INotificador notificador;
    private UUID mora;
    private UUID diaz;

    @BeforeEach
    void prepararBase() {
        PostgresDePrueba.limpiar();
        NamedParameterJdbcTemplate jdbc = PostgresDePrueba.jdbc();
        // Las cuentas creadas por pruebas anteriores (los entrenadores ya los borró limpiar())
        jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@pradofc.co'", Map.of());
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource()));
        // Supabase Auth y n8n simulados; la cuenta de Auth se crea de verdad en auth.users para que la FK se cumpla
        auth = mock(ISupabaseAuthAdmin.class);
        when(auth.crearUsuario(anyString())).thenAnswer(i -> {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", id));
            return id;
        });
        when(auth.generarTokenRecuperacion(anyString())).thenReturn("token-inventado");
        notificador = mock(INotificador.class);
        CuentasDeAcceso cuentas = new CuentasDeAcceso(auth, new UsuarioDAOImpl(jdbc), new SesionVerificadaDAOImpl(jdbc),
                notificador, new FrontendProperties("http://localhost:5173"));
        servicio = new EntrenadorServiceImpl(new EntrenadorDAOImpl(jdbc), new CategoriaDAOImpl(jdbc), cuentas,
                transaccion, Clock.systemDefaultZone());
        mora = PostgresDePrueba.entrenador("Andrés", "Mora", "1017234567", "andres.mora@pradofc.co");
        diaz = PostgresDePrueba.entrenador("Carlos", "Díaz", "71654321", "carlos.diaz@pradofc.co");
        PostgresDePrueba.categoria("Cachorros", diaz);
        PostgresDePrueba.categoria("Semillero", diaz);
        PostgresDePrueba.categoria("Sub-12", mora);
    }

    private <T> T enTransaccion(Supplier<T> accion) {
        return transaccion.execute(estado -> accion.get());
    }

    @Test
    void reasignaUnaCategoriaDeUnEntrenadorAOtro() {
        enTransaccion(() -> servicio.asignarCategorias(mora,
                new AsignarCategoriasRequestDTO(List.of("Semillero", "Sub-12"), true)));

        List<CategoriaResponseDTO> lista = enTransaccion(servicio::listarCategorias);
        assertThat(lista).extracting(CategoriaResponseDTO::nombre).containsExactly("Cachorros", "Semillero", "Sub-12");
        assertThat(lista).extracting(c -> c.entrenador().id()).containsExactly(diaz, mora, mora);
    }

    @Test
    void unConflictoNoDejaCambiosAMedias() {
        assertThatThrownBy(() -> enTransaccion(() -> servicio.asignarCategorias(diaz,
                new AsignarCategoriasRequestDTO(List.of("Cachorros", "Sub-12"), false))))
                .isInstanceOf(ConflictoException.class)
                .hasMessageStartingWith("Sub-12 ya tiene otro entrenador");

        assertThat(enTransaccion(() -> servicio.consultar(diaz)).categorias()).containsExactly("Cachorros", "Semillero");
        assertThat(enTransaccion(() -> servicio.consultar(mora)).categorias()).containsExactly("Sub-12");
    }

    @Test
    void quitarYTomarDeOtroQuedaGuardadoEnLaBase() {
        enTransaccion(() -> servicio.asignarCategorias(diaz,
                new AsignarCategoriasRequestDTO(List.of("Cachorros", "Sub-12"), true)));

        List<CategoriaResponseDTO> lista = enTransaccion(servicio::listarCategorias);
        assertThat(lista).extracting(CategoriaResponseDTO::nombre).containsExactly("Cachorros", "Semillero", "Sub-12");
        assertThat(lista).extracting(c -> c.entrenador() == null ? null : c.entrenador().id())
                .containsExactly(diaz, null, diaz);
    }

    @Test
    void desactivarLiberaSusCategoriasEnLaBase() {
        var r = enTransaccion(() -> servicio.cambiarEstado(diaz, new CambiarEstadoEntrenadorRequestDTO(false)));

        assertThat(r.activo()).isFalse();
        assertThat(enTransaccion(servicio::listarCategorias)).extracting(c -> c.entrenador() == null ? "-" : c.entrenador().apellido())
                .containsExactly("-", "-", "Mora");
    }

    @Test
    void elCorreoSeGuardaEnMinusculasYLaUnicidadDeLaTablaYaNoDistingueMayusculas() {
        UUID id = enTransaccion(() -> servicio.crear(EntrenadorServiceImplTest.conCorreo("Luis.Perez@PradoFC.co"))).id();

        assertThat(enTransaccion(() -> servicio.consultar(id)).correo()).isEqualTo("luis.perez@pradofc.co");
        assertThatThrownBy(() -> enTransaccion(() -> servicio.crear(EntrenadorServiceImplTest.conCorreo("LUIS.PEREZ@pradofc.co"))))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("documento: ya está registrado en otro entrenador; correo: ya está registrado en otro entrenador");
    }

    // ---- Cuenta de acceso (regla 9), sin transacción de afuera: la del servicio hace el commit de verdad ----

    @Test
    void elAltaGuardaUsuarioRolEIdUsuarioYDespuesEnviaElEnlace() {
        var creado = servicio.crear(EntrenadorServiceImplTest.conCorreo("luis.perez@pradofc.co"));

        assertThat(creado.correoEnviado()).isTrue();
        Map<String, Object> fila = PostgresDePrueba.jdbc().queryForMap("""
                SELECT e.id_usuario, u.correo, u.activo, r.nombre AS rol
                  FROM entrenador e
                  JOIN usuario u ON u.id_usuario = e.id_usuario
                  JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario
                  JOIN rol r ON r.id_rol = ur.id_rol
                 WHERE e.id_entrenador = :id""", Map.of("id", creado.id()));
        assertThat(fila).containsEntry("correo", "luis.perez@pradofc.co").containsEntry("activo", true)
                .containsEntry("rol", "ENTRENADOR");
        verify(notificador).notificar(eq(INotificador.ENLACE_CONTRASENA), anyMap());
    }

    @Test
    void siLaBaseFallaDespuesDeCrearLaCuentaNoQuedaNadaYSeBorraLaCuentaDeAuth() {
        // Otra petición registra el mismo correo en usuario justo después de que se revisó: la base lo rechaza
        when(auth.crearUsuario(anyString())).thenAnswer(i -> {
            String correo = i.getArgument(0);
            UUID id = UUID.randomUUID();
            UUID otro = UUID.randomUUID();
            NamedParameterJdbcTemplate jdbc = PostgresDePrueba.jdbc();
            jdbc.update("INSERT INTO auth.users (id) VALUES (:id), (:otro)", Map.of("id", id, "otro", otro));
            jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:otro, 'Otra', 'Cuenta', :correo)",
                    Map.of("otro", otro, "correo", correo));
            return id;
        });

        assertThatThrownBy(() -> servicio.crear(EntrenadorServiceImplTest.conCorreo("luis.perez@pradofc.co")))
                .isInstanceOf(ConflictoException.class).hasMessage(CuentasDeAcceso.OTRA_CUENTA);

        assertThat(PostgresDePrueba.jdbc().queryForObject(
                "SELECT count(*) FROM entrenador WHERE correo = 'luis.perez@pradofc.co'", Map.of(), Long.class))
                .as("el entrenador se deshizo con la transacción").isZero();
        verify(auth).eliminar(any(UUID.class));
        verifyNoInteractions(notificador);
    }

    @Test
    void desactivarConCuentaRevocaElAccesoYBorraSusSesionesYReactivarLoRestaura() {
        var creado = servicio.crear(EntrenadorServiceImplTest.conCorreo("luis.perez@pradofc.co"));
        NamedParameterJdbcTemplate jdbc = PostgresDePrueba.jdbc();
        UUID idUsuario = jdbc.queryForObject("SELECT id_usuario FROM entrenador WHERE id_entrenador = :id",
                Map.of("id", creado.id()), UUID.class);
        jdbc.update("""
                INSERT INTO sesion_verificada (id_sesion, id_usuario, verificada_en, expira_en)
                VALUES (gen_random_uuid(), :u, now(), now() + interval '1 hour')""", Map.of("u", idUsuario));

        servicio.cambiarEstado(creado.id(), new CambiarEstadoEntrenadorRequestDTO(false));

        verify(auth).cambiarAcceso(idUsuario, false);
        assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id_usuario = :u", Map.of("u", idUsuario), Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion_verificada WHERE id_usuario = :u", Map.of("u", idUsuario), Long.class)).isZero();

        servicio.cambiarEstado(creado.id(), new CambiarEstadoEntrenadorRequestDTO(true));

        verify(auth).cambiarAcceso(idUsuario, true);
        assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id_usuario = :u", Map.of("u", idUsuario), Boolean.class)).isTrue();
    }

    @Test
    void elReenvioSinCuentaLaCreaYLaVincula() {
        servicio.reenviarEnlace(mora);

        UUID idUsuario = PostgresDePrueba.jdbc().queryForObject("SELECT id_usuario FROM entrenador WHERE id_entrenador = :id",
                Map.of("id", mora), UUID.class);
        assertThat(idUsuario).isNotNull();
        assertThat(PostgresDePrueba.jdbc().queryForObject("SELECT correo FROM usuario WHERE id_usuario = :u",
                Map.of("u", idUsuario), String.class)).isEqualTo("andres.mora@pradofc.co");
        verify(notificador).notificar(eq(INotificador.ENLACE_CONTRASENA), anyMap());
    }
}
