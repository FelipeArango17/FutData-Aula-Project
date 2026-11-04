package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.persistence.dao.Destinatario;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@link DestinatariosDAOImpl} contra un PostgreSQL real. Año 2026: Semillero son los nacidos en 2016 y 2017; Sub-12, los
 * de 2014; Sub-13, los de 2013. La categoría se calcula por edad, no por {@code deportista.id_categoria}.
 */
@ExtendWith(DockerDisponible.class)
class DestinatariosDAOImplTest {

    private static final int ANIO = 2026;

    private NamedParameterJdbcTemplate jdbc;
    private DestinatariosDAOImpl dao;
    private UUID semillero;
    private UUID sub12;
    private UUID sub13;
    private int contador;

    @BeforeEach
    void prepararBase() {
        jdbc = PostgresDePrueba.jdbc();
        limpiarTodo();
        dao = new DestinatariosDAOImpl(jdbc);
        UUID entrenador = PostgresDePrueba.entrenador("Luis", "Mora", "1000000002", "luis@pradofc.co");
        semillero = PostgresDePrueba.categoria("Semillero", entrenador);
        sub12 = PostgresDePrueba.categoria("Sub-12", entrenador);
        sub13 = PostgresDePrueba.categoria("Sub-13", entrenador);
    }

    @AfterEach
    void limpiarTodo() {
        for (String tabla : List.of("auditoria", "notificacion_pago", "pago", "acudiente_deportista", "acudiente", "documento", "deportista")) {
            jdbc.getJdbcOperations().update("DELETE FROM " + tabla);
        }
        PostgresDePrueba.limpiar();
    }

    @Test
    void devuelveLosAcudientesDeLosDeportistasActivosDeLaCategoria() {
        UUID hijo = deportista("2017-03-03", true);
        vincular(acudiente("María", "Torres", "maria@correo.co"), hijo);
        vincular(acudiente("Pedro", "Alvarez", "pedro@correo.co"), hijo);

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).containsExactly(
                new Destinatario("pedro@correo.co", "Pedro Alvarez"), new Destinatario("maria@correo.co", "María Torres"));
    }

    @Test
    void laCategoriaSeCalculaPorEdadNoPorElValorHeredado() {
        UUID nacidoEn2017 = deportista("2017-03-03", true);
        jdbc.update("UPDATE deportista SET id_categoria = :c WHERE id_deportista = :d",
                new MapSqlParameterSource("c", sub12).addValue("d", nacidoEn2017));
        vincular(acudiente("María", "Torres", "maria@correo.co"), nacidoEn2017);

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).extracting(Destinatario::correo).containsExactly("maria@correo.co");
        assertThat(dao.acudientesDeCategoria(sub12, ANIO)).isEmpty();
    }

    @Test
    void unAcudienteConHijosEnDosCategoriasSaleEnLasDos() {
        UUID acudiente = acudiente("Carlos", "Gómez", "carlos@correo.co");
        vincular(acudiente, deportista("2016-06-06", true)); // Semillero
        vincular(acudiente, deportista("2014-01-01", true)); // Sub-12

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).extracting(Destinatario::correo).containsExactly("carlos@correo.co");
        assertThat(dao.acudientesDeCategoria(sub12, ANIO)).extracting(Destinatario::correo).containsExactly("carlos@correo.co");
        assertThat(dao.acudientesDeCategoria(sub13, ANIO)).isEmpty();
    }

    @Test
    void unAcudienteConDosHijosEnLaMismaCategoriaSaleUnaVez() {
        UUID acudiente = acudiente("Carlos", "Gómez", "carlos@correo.co");
        vincular(acudiente, deportista("2016-06-06", true));
        vincular(acudiente, deportista("2017-02-02", true));

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).hasSize(1);
    }

    @Test
    void cambiaConElAnioQueSePasa() {
        vincular(acudiente("María", "Torres", "maria@correo.co"), deportista("2017-03-03", true));

        // Nacido en 2017: 9 años en 2026 y 10 en 2027 (Semillero es de 9 a 10); 11 en 2028 ya es Sub-11
        assertThat(dao.acudientesDeCategoria(semillero, 2026)).hasSize(1);
        assertThat(dao.acudientesDeCategoria(semillero, 2027)).hasSize(1);
        assertThat(dao.acudientesDeCategoria(semillero, 2028)).isEmpty();
    }

    @Test
    void unDeportistaInactivoNoCuenta() {
        vincular(acudiente("María", "Torres", "maria@correo.co"), deportista("2017-03-03", false));

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).isEmpty();
    }

    @Test
    void unAcudienteSinCorreoOConCorreoEnBlancoNoCuenta() {
        UUID hijo = deportista("2017-03-03", true);
        vincular(acudiente("Sin", "Correo", null), hijo);
        vincular(acudiente("Correo", "Blanco", "   "), hijo);
        vincular(acudiente("María", "Torres", "maria@correo.co"), hijo);

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).extracting(Destinatario::correo).containsExactly("maria@correo.co");
    }

    @Test
    void ignoraLosCorreosRepetidosSinDistinguirMayusculasNiEspacios() {
        UUID hijo = deportista("2017-03-03", true);
        vincular(acudiente("Ana", "Zapata", "Mama@Correo.co"), hijo);
        vincular(acudiente("Ana", "Alvarez", " mama@correo.co "), hijo);

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).singleElement().satisfies(d -> {
            // Se queda el primero por apellido y nombre, con el correo sin espacios
            assertThat(d.correo()).isEqualTo("mama@correo.co");
            assertThat(d.nombre()).isEqualTo("Ana Alvarez");
        });
    }

    @Test
    void noFiltraPorGrupoYNoTraeALosDeportistasFueraDeRangoNiAOtrasCategorias() {
        UUID manana = deportista("2017-03-03", true);
        UUID tarde = deportista("2016-03-03", true);
        jdbc.getJdbcOperations().update("UPDATE deportista SET grupo = 'mañana' WHERE id_deportista = ?", manana);
        jdbc.getJdbcOperations().update("UPDATE deportista SET grupo = 'tarde' WHERE id_deportista = ?", tarde);
        vincular(acudiente("Uno", "Mañana", "manana@correo.co"), manana);
        vincular(acudiente("Dos", "Tarde", "tarde@correo.co"), tarde);
        vincular(acudiente("Tres", "Mayor", "mayor@correo.co"), deportista("2005-01-01", true)); // fuera de 4 a 16
        vincular(acudiente("Cuatro", "Sub12", "sub12@correo.co"), deportista("2014-01-01", true));

        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).extracting(Destinatario::correo)
                .containsExactlyInAnyOrder("manana@correo.co", "tarde@correo.co");
    }

    @Test
    void unaCategoriaSinDeportistasODesconocidaDevuelveListaVacia() {
        assertThat(dao.acudientesDeCategoria(semillero, ANIO)).isEmpty();
        assertThat(dao.acudientesDeCategoria(UUID.randomUUID(), ANIO)).isEmpty();
    }

    // --- Datos ----------------------------------------------------------------------------------------------

    private UUID acudiente(String nombre, String apellido, String correo) {
        return jdbc.queryForObject("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo)
                VALUES (:n, :a, 'CC', :doc, '1985-05-05', :correo) RETURNING id_acudiente""",
                new MapSqlParameterSource("n", nombre).addValue("a", apellido).addValue("correo", correo)
                        .addValue("doc", "9" + (++contador) + System.nanoTime() % 100000), UUID.class);
    }

    private UUID deportista(String nacimiento, boolean activo) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, activo)
                VALUES ('Hijo', 'Prueba', 'TI', :doc, CAST(:nac AS date), :activo) RETURNING id_deportista""",
                new MapSqlParameterSource("nac", nacimiento).addValue("activo", activo)
                        .addValue("doc", "10" + (++contador) + System.nanoTime() % 100000), UUID.class);
    }

    private void vincular(UUID idAcudiente, UUID idDeportista) {
        jdbc.update("INSERT INTO acudiente_deportista (id_acudiente, id_deportista) VALUES (:a, :d)",
                new MapSqlParameterSource("a", idAcudiente).addValue("d", idDeportista));
    }
}
