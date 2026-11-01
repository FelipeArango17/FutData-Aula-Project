package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EntrenadorDAOImpl y CategoriaDAOImpl contra un PostgreSQL real con las migraciones 001 a 007 (Testcontainers o
 * una base local). Sin ninguno de los dos, se omiten (ver {@link DockerDisponible}).
 */
@ExtendWith(DockerDisponible.class)
class EntrenadorDAOImplTest {

    private NamedParameterJdbcTemplate jdbc;
    private EntrenadorDAOImpl entrenadores;
    private CategoriaDAOImpl categorias;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        PostgresDePrueba.limpiar();
        entrenadores = new EntrenadorDAOImpl(jdbc);
        categorias = new CategoriaDAOImpl(jdbc);
    }

    private static Entrenador nuevo(String documento, String correo) {
        Entrenador e = new Entrenador();
        e.setNombre("Luis");
        e.setApellido("Pérez");
        e.setTipoDocumento("CC");
        e.setDocumento(documento);
        e.setFechaNacimiento(LocalDate.of(1990, 5, 14));
        e.setTelefono("3001112233");
        e.setCorreo(correo);
        e.setDireccion("Calle 40 Sur 75b-81");
        e.setHorarioTrabajo("Lunes a viernes, 3:30 p. m. a 7:30 p. m.");
        e.setEspecialidad("Porteros");
        return e;
    }

    @Test
    void creaYBuscaPorIdConTodosLosCampos() {
        UUID id = entrenadores.crear(nuevo("1098765432", "luis.perez@pradofc.co"));

        Entrenador leido = entrenadores.buscarPorId(id).orElseThrow();

        assertThat(leido.getId()).isEqualTo(id);
        assertThat(leido.getNombre()).isEqualTo("Luis");
        assertThat(leido.getApellido()).isEqualTo("Pérez");
        assertThat(leido.getTipoDocumento()).isEqualTo("CC");
        assertThat(leido.getDocumento()).isEqualTo("1098765432");
        assertThat(leido.getFechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(leido.getTelefono()).isEqualTo("3001112233");
        assertThat(leido.getCorreo()).isEqualTo("luis.perez@pradofc.co");
        assertThat(leido.getDireccion()).isEqualTo("Calle 40 Sur 75b-81");
        assertThat(leido.getHorarioTrabajo()).isEqualTo("Lunes a viernes, 3:30 p. m. a 7:30 p. m.");
        assertThat(leido.getEspecialidad()).isEqualTo("Porteros");
        assertThat(leido.getCategorias()).isEmpty();
        assertThat(leido.isActivo()).isTrue();
    }

    @Test
    void laMigracionCreaLasNueveCategoriasDelClubSinEntrenador() {
        assertThat(PostgresDePrueba.categoriasDeLaMigracion()).containsExactly(
                "Párvulos 4-6 sin entrenador", "Cachorros 7-8 sin entrenador", "Semillero 9-10 sin entrenador",
                "Sub-11 11-11 sin entrenador", "Sub-12 12-12 sin entrenador",
                "Sub-13 13-13 sin entrenador", "Sub-14 14-14 sin entrenador", "Sub-15 15-15 sin entrenador",
                "Sub-16 16-16 sin entrenador");
    }

    @Test
    void cambiaElEstadoActivoSinTocarLosDemasDatos() {
        UUID id = entrenadores.crear(nuevo("1098765432", "luis@pradofc.co"));

        assertThat(entrenadores.cambiarActivo(id, false)).isTrue();
        Entrenador leido = entrenadores.buscarPorId(id).orElseThrow();
        assertThat(leido.isActivo()).isFalse();
        assertThat(leido.getCorreo()).isEqualTo("luis@pradofc.co");
        assertThat(entrenadores.listar()).extracting(Entrenador::isActivo).containsExactly(false);

        assertThat(entrenadores.cambiarActivo(id, true)).isTrue();
        assertThat(entrenadores.buscarPorId(id).orElseThrow().isActivo()).isTrue();
        assertThat(entrenadores.cambiarActivo(UUID.randomUUID(), false)).isFalse();
    }

    @Test
    void actualizarNoCambiaElEstadoActivo() {
        UUID id = entrenadores.crear(nuevo("1098765432", "luis@pradofc.co"));
        entrenadores.cambiarActivo(id, false);
        Entrenador cambios = nuevo("1098765432", "luis@pradofc.co");
        cambios.setId(id);
        cambios.setNombre("Luis Fernando");

        entrenadores.actualizar(cambios);

        assertThat(entrenadores.buscarPorId(id).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void buscarUnIdQueNoExisteDevuelveVacio() {
        assertThat(entrenadores.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listaPorApellidoYNombreConLasCategoriasEnOrdenOficial() {
        UUID mora = PostgresDePrueba.entrenador("Andrés", "Mora", "1017234567", "andres.mora@pradofc.co");
        UUID diaz = PostgresDePrueba.entrenador("Carlos", "Díaz", "71654321", "carlos.diaz@pradofc.co");
        UUID ana = PostgresDePrueba.entrenador("Ana", "Díaz", "52123456", "ana.diaz@pradofc.co");
        PostgresDePrueba.categoria("Sub-16", diaz);
        PostgresDePrueba.categoria("Sub-12", mora);
        PostgresDePrueba.categoria("Cachorros", diaz);

        List<Entrenador> lista = entrenadores.listar();

        assertThat(lista).extracting(Entrenador::getId).containsExactly(ana, diaz, mora);
        assertThat(lista).extracting(Entrenador::getCategorias)
                .containsExactly(List.of(), List.of("Cachorros", "Sub-16"), List.of("Sub-12"));
    }

    @Test
    void existeDocumentoComparaTipoYNumeroYExcluyeAlMismo() {
        UUID id = PostgresDePrueba.entrenador("Andrés", "Mora", "1017234567", "andres.mora@pradofc.co");

        assertThat(entrenadores.existeDocumento("CC", "1017234567", null)).isTrue();
        assertThat(entrenadores.existeDocumento("PASAPORTE", "1017234567", null)).isFalse();
        assertThat(entrenadores.existeDocumento("CC", "1017234568", null)).isFalse();
        assertThat(entrenadores.existeDocumento("CC", "1017234567", id)).isFalse();
        assertThat(entrenadores.existeDocumento("CC", "1017234567", UUID.randomUUID())).isTrue();
    }

    @Test
    void existeCorreoSinDistinguirMayusculasYExcluyeAlMismo() {
        UUID id = PostgresDePrueba.entrenador("Andrés", "Mora", "1017234567", "Andres.Mora@PradoFC.co");

        assertThat(entrenadores.existeCorreo("andres.mora@pradofc.co", null)).isTrue();
        assertThat(entrenadores.existeCorreo("ANDRES.MORA@PRADOFC.CO", null)).isTrue();
        assertThat(entrenadores.existeCorreo("otro@pradofc.co", null)).isFalse();
        assertThat(entrenadores.existeCorreo("andres.mora@pradofc.co", id)).isFalse();
    }

    @Test
    void laRestriccionDeDocumentoSeTraduceAlCampoDocumento() {
        entrenadores.crear(nuevo("1098765432", "uno@pradofc.co"));

        DatoDuplicadoException e = catchThrowableOfType(DatoDuplicadoException.class,
                () -> entrenadores.crear(nuevo("1098765432", "dos@pradofc.co")));

        assertThat(e.getCampo()).isEqualTo("documento");
        assertThat(e.getMessage()).doesNotContain("1098765432");
    }

    @Test
    void laRestriccionDeCorreoSeTraduceAlCampoCorreo() {
        entrenadores.crear(nuevo("1098765432", "luis@pradofc.co"));

        DatoDuplicadoException e = catchThrowableOfType(DatoDuplicadoException.class,
                () -> entrenadores.crear(nuevo("1098765433", "luis@pradofc.co")));

        assertThat(e.getCampo()).isEqualTo("correo");
        assertThat(e.getMessage()).doesNotContain("luis@pradofc.co");
    }

    @Test
    void actualizaLosDatosPersonalesSinTocarDocumentosNiCuentaNiCategorias() {
        UUID id = entrenadores.crear(nuevo("1098765432", "luis@pradofc.co"));
        PostgresDePrueba.categoria("Semillero", id);
        jdbc.update("UPDATE entrenador SET documentos = 'carpeta/luis' WHERE id_entrenador = :id", Map.of("id", id));

        Entrenador cambios = nuevo("1098765499", "luis.perez@pradofc.co");
        cambios.setId(id);
        cambios.setNombre("Luis Fernando");
        cambios.setDireccion(null);
        assertThat(entrenadores.actualizar(cambios)).isTrue();

        Entrenador leido = entrenadores.buscarPorId(id).orElseThrow();
        assertThat(leido.getNombre()).isEqualTo("Luis Fernando");
        assertThat(leido.getDocumento()).isEqualTo("1098765499");
        assertThat(leido.getCorreo()).isEqualTo("luis.perez@pradofc.co");
        assertThat(leido.getDireccion()).isNull();
        assertThat(leido.getCategorias()).containsExactly("Semillero");
        assertThat(jdbc.queryForObject("SELECT documentos FROM entrenador WHERE id_entrenador = :id",
                Map.of("id", id), String.class)).isEqualTo("carpeta/luis");
    }

    @Test
    void actualizarUnIdQueNoExisteDevuelveFalse() {
        Entrenador cambios = nuevo("1098765432", "luis@pradofc.co");
        cambios.setId(UUID.randomUUID());

        assertThat(entrenadores.actualizar(cambios)).isFalse();
    }

    @Test
    void actualizarConElCorreoDeOtroSeTraduceAlCampoCorreo() {
        entrenadores.crear(nuevo("1098765432", "uno@pradofc.co"));
        UUID id = entrenadores.crear(nuevo("1098765433", "dos@pradofc.co"));
        Entrenador cambios = nuevo("1098765433", "uno@pradofc.co");
        cambios.setId(id);

        DatoDuplicadoException e = catchThrowableOfType(DatoDuplicadoException.class,
                () -> entrenadores.actualizar(cambios));

        assertThat(e.getCampo()).isEqualTo("correo");
    }

    @Test
    void listaLasCategoriasEnOrdenOficialConSuEntrenador() {
        UUID mora = PostgresDePrueba.entrenador("Andrés", "Mora", "1017234567", "andres.mora@pradofc.co");
        PostgresDePrueba.categoria("Sub-14", mora);
        PostgresDePrueba.categoria("Cachorros", mora);
        PostgresDePrueba.categoria("Semillero", mora);

        List<Categoria> lista = categorias.listar();

        assertThat(lista).extracting(Categoria::getNombre).containsExactly("Cachorros", "Semillero", "Sub-14");
        assertThat(lista).extracting(Categoria::getEdadMinima).containsExactly(7, 9, 14);
        assertThat(lista).extracting(Categoria::getEdadMaxima).containsExactly(8, 10, 14);
        assertThat(lista).extracting(Categoria::getIdEntrenador).containsOnly(mora);
    }

    @Test
    void asignaCategoriasAOtroEntrenadorDentroDeUnaTransaccion() {
        UUID mora = PostgresDePrueba.entrenador("Andrés", "Mora", "1017234567", "andres.mora@pradofc.co");
        UUID diaz = PostgresDePrueba.entrenador("Carlos", "Díaz", "71654321", "carlos.diaz@pradofc.co");
        UUID cachorros = PostgresDePrueba.categoria("Cachorros", mora);
        UUID semillero = PostgresDePrueba.categoria("Semillero", mora);
        PostgresDePrueba.categoria("Sub-12", mora);
        TransactionTemplate transaccion = new TransactionTemplate(new DataSourceTransactionManager(PostgresDePrueba.dataSource()));

        transaccion.executeWithoutResult(estado -> {
            assertThat(categorias.listarParaActualizar()).hasSize(3);
            categorias.asignarEntrenador(List.of(cachorros, semillero), diaz);
        });
        categorias.asignarEntrenador(List.of(), diaz);

        assertThat(entrenadores.buscarPorId(diaz).orElseThrow().getCategorias()).containsExactly("Cachorros", "Semillero");
        assertThat(entrenadores.buscarPorId(mora).orElseThrow().getCategorias()).containsExactly("Sub-12");
    }

    @Test
    void conEntrenadorNullDejaLasCategoriasSinEntrenador() {
        UUID mora = PostgresDePrueba.entrenador("Andrés", "Mora", "1017234567", "andres.mora@pradofc.co");
        UUID cachorros = PostgresDePrueba.categoria("Cachorros", mora);
        PostgresDePrueba.categoria("Semillero", mora);

        categorias.asignarEntrenador(List.of(cachorros), null);

        assertThat(categorias.listar()).extracting(Categoria::getNombre, Categoria::getIdEntrenador)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Cachorros", null),
                        org.assertj.core.groups.Tuple.tuple("Semillero", mora));
        assertThat(entrenadores.buscarPorId(mora).orElseThrow().getCategorias()).containsExactly("Semillero");
    }
}
