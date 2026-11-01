package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.futdata.api.dto.AsignarCategoriasRequestDTO;
import co.edu.futdata.api.dto.CambiarEstadoEntrenadorRequestDTO;
import co.edu.futdata.api.dto.CategoriaResponseDTO;
import co.edu.futdata.persistence.impl.CategoriaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.EntrenadorDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.services.facade.ConflictoException;
import java.time.Clock;
import java.util.List;
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
    private UUID mora;
    private UUID diaz;

    @BeforeEach
    void prepararBase() {
        PostgresDePrueba.limpiar();
        NamedParameterJdbcTemplate jdbc = PostgresDePrueba.jdbc();
        servicio = new EntrenadorServiceImpl(new EntrenadorDAOImpl(jdbc), new CategoriaDAOImpl(jdbc), Clock.systemDefaultZone());
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource()));
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
}
