package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import co.edu.futdata.api.dto.ResultadoCiclosDTO;
import co.edu.futdata.config.CobrosCicloProperties;
import co.edu.futdata.config.FrontendProperties;
import co.edu.futdata.config.MensualidadProperties;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.persistence.impl.AuditoriaDAOImpl;
import co.edu.futdata.persistence.impl.DockerDisponible;
import co.edu.futdata.persistence.impl.PagoDAOImpl;
import co.edu.futdata.persistence.impl.PostgresDePrueba;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Cobros por ciclo con los DAO reales sobre PostgreSQL: filtros de alcance, idempotencia, RECHAZADO e índice único. */
@ExtendWith(DockerDisponible.class)
class CobrosCicloConPostgresTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    private NamedParameterJdbcTemplate jdbc;
    private PagoDAOImpl pagos;
    private final List<Map<String, Object>> enviados = new ArrayList<>();
    private boolean n8nCaido;
    private int contador;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        for (String tabla : List.of("auditoria", "notificacion_pago", "pago", "acudiente_deportista", "acudiente", "documento", "deportista")) {
            jdbc.getJdbcOperations().update("DELETE FROM " + tabla);
        }
        enviados.clear();
        n8nCaido = false;
        pagos = new PagoDAOImpl(jdbc);
    }

    private CobrosCicloServiceImpl servicio(PagoDAOImpl dao) {
        INotificador n8n = (evento, datos) -> {
            if (n8nCaido) {
                throw new NotificacionNoEnviadaException("caído", evento, 502, null);
            }
            enviados.add(datos);
        };
        return new CobrosCicloServiceImpl(dao, new AuditoriaDAOImpl(jdbc, new ObjectMapper()), n8n, new MensualidadProperties(90_000, 5, false),
                new CobrosCicloProperties(150_000, 90_000, 5, 7), new FrontendProperties("https://club.test"),
                new DataSourceTransactionManager(PostgresDePrueba.dataSource()));
    }

    private UUID deportista(String nombre, String registro, String estado, boolean activo) {
        return jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, fecha_registro, estado_inscripcion, activo)
                VALUES (:n, 'Gómez', 'TI', :doc, '2016-03-03', :registro, :estado, :activo) RETURNING id_deportista""",
                new MapSqlParameterSource("n", nombre).addValue("doc", "10" + (++contador) + System.nanoTime() % 100000)
                        .addValue("registro", LocalDate.parse(registro)).addValue("estado", estado).addValue("activo", activo), UUID.class);
    }

    private void acudiente(UUID idDeportista, String nombre, String correo) { // correo puede ser null
        UUID id = jdbc.queryForObject("""
                INSERT INTO acudiente (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento, correo)
                VALUES (:n, 'Torres', 'CC', :doc, '1985-05-05', :correo) RETURNING id_acudiente""",
                new MapSqlParameterSource("n", nombre).addValue("doc", "9" + (++contador) + System.nanoTime() % 100000).addValue("correo", correo), UUID.class);
        jdbc.update("INSERT INTO acudiente_deportista (id_acudiente, id_deportista) VALUES (:a, :d)", Map.of("a", id, "d", idDeportista));
    }

    private List<String> cobrosDe(UUID idDeportista) {
        return jdbc.getJdbcOperations().queryForList(
                "SELECT tipo_pago || ':' || coalesce(periodo::text, '-') || ':' || fecha_vencimiento || ':' || estado_pago FROM pago WHERE id_deportista = ? ORDER BY tipo_pago, periodo",
                String.class, idDeportista);
    }

    @Test
    void soloSeCobraALosPendientesYActivosNoALosRetiradosNiInactivos() {
        UUID pendiente = deportista("Pendiente", "2025-11-14", "PENDIENTE", true);
        UUID retirada = deportista("Retirada", "2025-11-14", "RETIRADA", true);
        UUID inactivo = deportista("Inactivo", "2025-11-14", "PENDIENTE", false);

        ResultadoCiclosDTO r = servicio(pagos).generarCobrosDeCiclo(HOY, null);

        assertThat(r.creadas()).isEqualTo(1);
        assertThat(cobrosDe(pendiente)).containsExactly("MENSUALIDAD:2026-11-01:2026-11-14:PENDIENTE");
        assertThat(cobrosDe(retirada)).isEmpty();
        assertThat(cobrosDe(inactivo)).isEmpty();
    }

    @Test
    void unDeportistaActivoConInscripcionActivaTambienSeCobra() {
        PostgresDePrueba.limpiar(); // borra las categorías de la migración para insertar la suya
        UUID entrenador = PostgresDePrueba.entrenador("Carlos", "Mendoza", "123456789", "carlos-ciclo@pradofc.co");
        UUID categoria = PostgresDePrueba.categoria("Cachorros", entrenador);
        UUID activa = deportista("Activa", "2025-11-14", "PENDIENTE", true);
        jdbc.update("UPDATE deportista SET estado_inscripcion = 'ACTIVA', id_categoria = :c WHERE id_deportista = :d", Map.of("c", categoria, "d", activa));

        servicio(pagos).generarCobrosDeCiclo(HOY, null);

        assertThat(cobrosDe(activa)).hasSize(1);
    }

    @Test
    void esIdempotenteYUnRegistradoHaceDosAnosSoloTieneElCicloVigente() {
        UUID viejo = deportista("Viejo", "2024-11-20", "PENDIENTE", true);

        ResultadoCiclosDTO primera = servicio(pagos).generarCobrosDeCiclo(HOY, null);
        ResultadoCiclosDTO segunda = servicio(pagos).generarCobrosDeCiclo(HOY, null);

        assertThat(primera).isEqualTo(new ResultadoCiclosDTO(1, 0, 0, 0, 0));
        assertThat(segunda).isEqualTo(new ResultadoCiclosDTO(0, 1, 0, 0, 0));
        // vigente = el del 20 de octubre (el 20 de noviembre aún está a más de 5 días); vence hoy porque ya pasó
        assertThat(cobrosDe(viejo)).containsExactly("MENSUALIDAD:2026-10-01:2026-11-10:PENDIENTE");
    }

    @Test
    void unaMensualidadRechazadaAPropositoNoSeRegenera() {
        UUID id = deportista("Rechazada", "2025-11-14", "PENDIENTE", true);
        servicio(pagos).generarCobrosDeCiclo(HOY, null);
        jdbc.update("UPDATE pago SET estado_pago = 'RECHAZADO' WHERE id_deportista = :d", Map.of("d", id));

        ResultadoCiclosDTO r = servicio(pagos).generarCobrosDeCiclo(HOY, null);

        assertThat(r.creadas()).isZero();
        assertThat(cobrosDe(id)).containsExactly("MENSUALIDAD:2026-11-01:2026-11-14:RECHAZADO");
    }

    @Test
    void unIngresoRecibeInscripcionYUniformeUnaSolaVez() {
        UUID nuevo = deportista("Nuevo", "2026-11-08", "PENDIENTE", true);

        servicio(pagos).generarCobrosDeCiclo(HOY, null);
        ResultadoCiclosDTO segunda = servicio(pagos).generarCobrosDeCiclo(HOY, null);

        assertThat(cobrosDe(nuevo)).containsExactly(
                "INSCRIPCION:-:2026-11-10:PENDIENTE", "MENSUALIDAD:2026-11-01:2026-11-10:PENDIENTE", "UNIFORME:-:2026-11-10:PENDIENTE");
        assertThat(segunda).isEqualTo(new ResultadoCiclosDTO(0, 3, 0, 0, 0));
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM pago WHERE monto IN (150000, 90000)", Integer.class)).isEqualTo(3);
    }

    @Test
    void avisaSoloDelCobroAnticipadoALosAcudientesConCorreo() {
        UUID id = deportista("Pedro", "2025-11-14", "PENDIENTE", true);
        acudiente(id, "María", "maria@correo.co");
        acudiente(id, "Luis", "luis@correo.co");
        acudiente(id, "Sin correo", null); // no se le avisa

        ResultadoCiclosDTO r = servicio(pagos).generarCobrosDeCiclo(HOY, null);
        servicio(pagos).generarCobrosDeCiclo(HOY, null); // idempotente: no vuelve a avisar

        assertThat(r.avisosEnviados()).isEqualTo(2);
        assertThat(enviados).extracting(d -> d.get("correo")).containsExactlyInAnyOrder("maria@correo.co", "luis@correo.co");
        assertThat(enviados.get(0)).containsEntry("nombreDeportista", "Pedro Gómez").containsEntry("diasRestantes", 4)
                .containsEntry("fechaVencimiento", "2026-11-14").containsEntry("enlacePagos", "https://club.test/acudiente/pagos");
    }

    @Test
    void siN8nFallaElCobroQuedaCreado() {
        UUID id = deportista("Pedro", "2025-11-14", "PENDIENTE", true);
        acudiente(id, "María", "maria@correo.co");
        n8nCaido = true;

        ResultadoCiclosDTO r = servicio(pagos).generarCobrosDeCiclo(HOY, null);

        assertThat(r).isEqualTo(new ResultadoCiclosDTO(1, 0, 0, 1, 0));
        assertThat(cobrosDe(id)).hasSize(1);
    }

    @Test
    void elChoqueConElIndiceUnicoSeCuentaComoYaExistente() {
        UUID id = deportista("Pedro", "2025-11-14", "PENDIENTE", true);
        servicio(pagos).generarCobrosDeCiclo(HOY, null);
        // Simula la carrera: la revisión dice que no existe, pero otra ejecución ya la creó
        PagoDAOImpl ciego = spy(pagos);
        doReturn(false).when(ciego).existeMensualidad(eq(id), any());

        ResultadoCiclosDTO r = servicio(ciego).generarCobrosDeCiclo(HOY, null);

        assertThat(r).isEqualTo(new ResultadoCiclosDTO(0, 1, 0, 0, 0));
        assertThat(cobrosDe(id)).hasSize(1);
    }
}
