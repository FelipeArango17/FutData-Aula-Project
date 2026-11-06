package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import co.edu.futdata.api.dto.MiJugadorFichaResponseDTO;
import co.edu.futdata.api.dto.MiJugadorResponseDTO;
import co.edu.futdata.config.FotosProperties;
import co.edu.futdata.datasource.external.IAlmacenamientoArchivos;
import co.edu.futdata.domain.rules.ReglasDeportista;
import co.edu.futdata.persistence.impl.DeportistaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * "Mis jugadores" del acudiente con el DAO y el servicio reales sobre PostgreSQL: la propiedad se decide en el SQL, así
 * que un deportista de otro acudiente no aparece ni en la lista ni en la ficha. Storage es un simulado sin fotos.
 */
@ExtendWith(DockerDisponible.class)
class MisJugadoresConPostgresTest {

    /** 10 de noviembre de 2026: nacidos en 2018 y 2019 son Cachorros; 2015, Sub-11. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("America/Bogota"));

    private NamedParameterJdbcTemplate jdbc;
    private DeportistaServiceImpl servicio;
    private int contador;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        jdbc.getJdbcOperations().update("DELETE FROM auditoria");
        PostgresDePrueba.limpiarDatosDeDeportistas();
        jdbc.getJdbcOperations().update("DELETE FROM acudiente");
        jdbc.getJdbcOperations().update("DELETE FROM usuario WHERE correo LIKE 'jugadores-%'");
        jdbc.getJdbcOperations().update("DELETE FROM auth.users WHERE id NOT IN (SELECT id_usuario FROM usuario)");
        PostgresDePrueba.limpiar();
        for (ReglasDeportista.CategoriaClub c : ReglasDeportista.CATEGORIAS) {
            jdbc.update("INSERT INTO categoria (nombre, edad_minima, edad_maxima) VALUES (:n, :min, :max)",
                    new MapSqlParameterSource("n", c.nombre()).addValue("min", c.edadMinima()).addValue("max", c.edadMaxima()));
        }
        servicio = new DeportistaServiceImpl(new DeportistaDAOImpl(jdbc), mock(IAlmacenamientoArchivos.class),
                new FotosProperties("fotos", Duration.ofMinutes(60)), RELOJ);
    }

    /** Las demás clases insertan sus propias categorías: no se dejan las de esta. */
    @AfterEach
    void limpiarCategorias() {
        PostgresDePrueba.limpiar();
    }

    // --- Datos ---------------------------------------------------------------------------------------------

    private UUID usuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id) VALUES (:id)", Map.of("id", id));
        jdbc.update("INSERT INTO usuario (id_usuario, nombre, apellido, correo) VALUES (:id, 'Ana', 'Prueba', :c)",
                Map.of("id", id, "c", "jugadores-" + id + "@pradofc.co"));
        return id;
    }

    private UUID acudiente(UUID idUsuario) {
        return jdbc.queryForObject("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, id_usuario)
                VALUES ('María', 'Torres', 'CC', :doc, '1985-05-05', :usuario) RETURNING id_acudiente""",
                new MapSqlParameterSource("doc", "9" + (++contador) + System.nanoTime() % 100000).addValue("usuario", idUsuario), UUID.class);
    }

    private UUID deportista(String nombre, String apellido, String nacimiento, boolean activo) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, grupo, activo)
                VALUES (:n, :a, 'TI', :doc, CAST(:nac AS date), 'tarde', :activo) RETURNING id_deportista""",
                new MapSqlParameterSource("n", nombre).addValue("a", apellido).addValue("nac", nacimiento).addValue("activo", activo)
                        .addValue("doc", "10" + (++contador) + System.nanoTime() % 100000), UUID.class);
    }

    private void vincular(UUID idAcudiente, UUID idDeportista, String parentesco) {
        jdbc.update("INSERT INTO acudiente_deportista (id_acudiente, id_deportista, parentesco) VALUES (:a, :d, :p)",
                new MapSqlParameterSource("a", idAcudiente).addValue("d", idDeportista).addValue("p", parentesco));
    }

    private void documento(UUID idDeportista, String tipo, String estado) {
        jdbc.update("""
                INSERT INTO documento (id_deportista, tipo, ruta, nombre_original, tipo_contenido, tamano_bytes, estado, comentario, revisado_en)
                VALUES (:d, :t, :ruta, 'x.pdf', 'application/pdf', 100, :e,
                        CASE WHEN :e = 'RECHAZADO' THEN 'Ilegible' END, CASE WHEN :e = 'PENDIENTE' THEN NULL ELSE now() END)""",
                new MapSqlParameterSource("d", idDeportista).addValue("t", tipo).addValue("e", estado)
                        .addValue("ruta", idDeportista + "/" + UUID.randomUUID()));
    }

    // --- Pruebas -------------------------------------------------------------------------------------------

    @Test
    void listaSoloLosHijosPropiosOrdenadosConParentescoYCategoria() {
        UUID yo = usuario();
        UUID miAcudiente = acudiente(yo);
        UUID luis = deportista("Luis", "Zapata", "2018-03-03", true);   // Cachorros
        UUID sara = deportista("Sara", "Alvarez", "2015-06-06", true);  // Sub-11
        UUID ana = deportista("Ana", "Zapata", "2010-01-01", true);     // Sub-16, mismo apellido que Luis
        vincular(miAcudiente, luis, "Padre");
        vincular(miAcudiente, sara, "Tia");
        vincular(miAcudiente, ana, null);
        UUID ajeno = deportista("Pedro", "Ajeno", "2018-03-03", true);
        vincular(acudiente(usuario()), ajeno, "Madre");

        List<MiJugadorResponseDTO> lista = servicio.misJugadores(yo);

        // apellido, luego nombre: Alvarez, Zapata Ana, Zapata Luis
        assertThat(lista).extracting(MiJugadorResponseDTO::id).containsExactly(sara, ana, luis);
        assertThat(lista).extracting(MiJugadorResponseDTO::parentesco).containsExactly("Tia", null, "Padre");
        assertThat(lista).extracting(MiJugadorResponseDTO::categoria).containsExactly("Sub-11", "Sub-16", "Cachorros");
        // El grupo guardado ('tarde') solo se ve en Cachorros y Semillero
        assertThat(lista).extracting(MiJugadorResponseDTO::grupo).containsExactly(null, null, "tarde");
    }

    @Test
    void unHijoInactivoSigueEnLaListaConActivoFalso() {
        UUID yo = usuario();
        UUID hijo = deportista("Luis", "Retirado", "2018-03-03", false);
        vincular(acudiente(yo), hijo, "Madre");

        assertThat(servicio.misJugadores(yo)).singleElement().satisfies(j -> {
            assertThat(j.id()).isEqualTo(hijo);
            assertThat(j.activo()).isFalse();
        });
    }

    @Test
    void unNacidoFueraDeRangoSaleSinCategoria() {
        UUID yo = usuario();
        vincular(acudiente(yo), deportista("Juan", "Mayor", "2005-01-01", true), "Padre");

        assertThat(servicio.misJugadores(yo)).singleElement().satisfies(j -> {
            assertThat(j.categoria()).isNull();
            assertThat(j.idCategoria()).isNull();
            assertThat(j.grupo()).isNull();
        });
    }

    @Test
    void laFichaDeUnHijoAjenoOInexistenteEs404ConElMismoMensaje() {
        UUID yo = usuario();
        vincular(acudiente(yo), deportista("Luis", "Propio", "2018-03-03", true), "Madre");
        UUID ajeno = deportista("Pedro", "Ajeno", "2018-03-03", true);
        vincular(acudiente(usuario()), ajeno, "Madre");

        assertThatThrownBy(() -> servicio.miJugador(yo, ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe un deportista con ese id.");
        assertThatThrownBy(() -> servicio.miJugador(yo, UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe un deportista con ese id.");
    }

    @Test
    void laFichaTraeLosDatosDeContactoYElParentescoDeEsteVinculo() {
        UUID yo = usuario();
        UUID hijo = deportista("Luis", "Propio", "2018-03-03", true);
        vincular(acudiente(yo), hijo, "Abuela");

        MiJugadorFichaResponseDTO ficha = servicio.miJugador(yo, hijo);

        assertThat(ficha.id()).isEqualTo(hijo);
        assertThat(ficha.parentesco()).isEqualTo("Abuela");
        assertThat(ficha.tipoDocumento()).isEqualTo("TI");
        assertThat(ficha.documento()).isNotBlank();
        assertThat(ficha.categoria()).isEqualTo("Cachorros");
        assertThat(ficha.edad()).isEqualTo(8);
    }

    @Test
    void unHijoConDosAcudientesSeVeConElParentescoDeCadaUno() {
        UUID mama = usuario();
        UUID papa = usuario();
        UUID hijo = deportista("Luis", "Compartido", "2018-03-03", true);
        vincular(acudiente(mama), hijo, "Madre");
        vincular(acudiente(papa), hijo, "Padre");

        assertThat(servicio.misJugadores(mama)).singleElement().satisfies(j -> assertThat(j.parentesco()).isEqualTo("Madre"));
        assertThat(servicio.miJugador(papa, hijo).parentesco()).isEqualTo("Padre");
    }

    @Test
    void unUsuarioSinFilaDeAcudienteVeListaVaciaYFicha404() {
        UUID sinFila = usuario();
        UUID hijo = deportista("Luis", "Huerfano", "2018-03-03", true);
        vincular(acudiente(usuario()), hijo, "Madre");

        assertThat(servicio.misJugadores(sinFila)).isEmpty();
        assertThatThrownBy(() -> servicio.miJugador(sinFila, hijo)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(servicio.misJugadores(UUID.randomUUID())).isEmpty();
    }

    @Test
    void estadoCompletaSiYSoloSiNoFaltanDocumentos() {
        UUID yo = usuario();
        UUID acudiente = acudiente(yo);
        UUID sinDocs = deportista("A", "Aaa", "2018-03-03", true);
        UUID parcial = deportista("B", "Bbb", "2018-03-03", true);
        UUID completo = deportista("C", "Ccc", "2018-03-03", true);
        for (UUID d : List.of(sinDocs, parcial, completo)) {
            vincular(acudiente, d, "Madre");
        }
        // parcial: identidad aprobada, certificado solo pendiente, formulario rechazado, habeas data aprobado y un opcional aprobado
        documento(parcial, "DOCUMENTO_IDENTIDAD", "APROBADO");
        documento(parcial, "CERTIFICADO_ESTUDIO", "PENDIENTE");
        documento(parcial, "FORMULARIO_INSCRIPCION", "RECHAZADO");
        documento(parcial, "AUTORIZACION_HABEAS_DATA", "APROBADO");
        documento(parcial, "PAZ_Y_SALVO", "APROBADO");
        for (String tipo : ReglasDeportista.DOCUMENTOS_OBLIGATORIOS) {
            documento(completo, tipo, "APROBADO");
        }

        Map<UUID, String> estados = new HashMap<>();
        servicio.misJugadores(yo).forEach(j -> estados.put(j.id(), j.estadoInscripcion()));
        assertThat(estados).containsEntry(sinDocs, "PENDIENTE").containsEntry(parcial, "PENDIENTE").containsEntry(completo, "COMPLETA");

        assertThat(servicio.miJugador(yo, sinDocs).documentosFaltantes()).containsExactlyElementsOf(ReglasDeportista.DOCUMENTOS_OBLIGATORIOS);
        assertThat(servicio.miJugador(yo, parcial).documentosFaltantes()).containsExactly("CERTIFICADO_ESTUDIO", "FORMULARIO_INSCRIPCION");
        assertThat(servicio.miJugador(yo, completo).documentosFaltantes()).isEmpty();
        for (UUID d : List.of(sinDocs, parcial, completo)) {
            MiJugadorFichaResponseDTO ficha = servicio.miJugador(yo, d);
            assertThat(ficha.estadoInscripcion().equals("COMPLETA")).isEqualTo(ficha.documentosFaltantes().isEmpty());
        }
    }
}
