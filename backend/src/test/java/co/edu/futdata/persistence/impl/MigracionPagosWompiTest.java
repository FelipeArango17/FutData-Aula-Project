package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Las reglas que deja la migración 009 (pagos con Wompi) contra un PostgreSQL real: más medios de pago, los tipos TORNEO y
 * UNIFORME, el periodo solo obligatorio en mensualidades y la columna {@code id_transaccion_wompi} con su índice único
 * parcial. Cada rechazo se comprueba por el nombre de la restricción.
 */
@ExtendWith(DockerDisponible.class)
class MigracionPagosWompiTest {

    private NamedParameterJdbcTemplate jdbc;
    private UUID deportista;

    @BeforeEach
    void preparar() {
        jdbc = PostgresDePrueba.jdbc();
        limpiar();
        deportista = jdbc.queryForObject("""
                INSERT INTO deportista (nombre, apellido, tipo_documento, documento_identidad, fecha_nacimiento)
                VALUES ('Pedro', 'Gómez', 'TI', '1099000001', '2016-03-03') RETURNING id_deportista""", new MapSqlParameterSource(), UUID.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.getJdbcOperations().update("DELETE FROM pago");
        jdbc.getJdbcOperations().update("DELETE FROM deportista");
    }

    private void insertar(String tipo, LocalDate periodo, String estado, String metodo, String referencia, String transaccion) {
        jdbc.update("""
                INSERT INTO pago (id_deportista, tipo_pago, periodo, fecha_vencimiento, monto, concepto, estado_pago, fecha_pago, metodo,
                                  referencia_wompi, id_transaccion_wompi)
                VALUES (:d, :tipo, :periodo, '2026-11-05', 90000, 'Concepto', :estado, :fecha, :metodo, :referencia, :transaccion)""",
                new MapSqlParameterSource().addValue("d", deportista).addValue("tipo", tipo).addValue("periodo", periodo)
                        .addValue("estado", estado).addValue("fecha", "APROBADO".equals(estado) ? LocalDate.of(2026, 11, 3) : null)
                        .addValue("metodo", metodo).addValue("referencia", referencia).addValue("transaccion", transaccion));
    }

    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 1);

    @Test
    void losTresMediosNuevosSeAceptanConReferenciaWompi() {
        int n = 0;
        for (String metodo : new String[] {"QR", "BOTON_BANCOLOMBIA", "WOMPI_OTRO", "PSE", "NEQUI", "TARJETA"}) {
            insertar("UNIFORME", null, "APROBADO", metodo, "FD-ref-" + n++, null);
        }
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT count(*) FROM pago", Integer.class)).isEqualTo(6);
    }

    @Test
    void losMediosEnLineaSinReferenciaSeRechazan() {
        for (String metodo : new String[] {"QR", "BOTON_BANCOLOMBIA", "WOMPI_OTRO"}) {
            assertThatThrownBy(() -> insertar("UNIFORME", null, "APROBADO", metodo, null, null))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_referencia_wompi_ck");
        }
    }

    @Test
    void efectivoYTransferenciaSiguenSiendoManuales() {
        insertar("UNIFORME", null, "APROBADO", "EFECTIVO", null, null);
        insertar("UNIFORME", null, "APROBADO", "TRANSFERENCIA", null, null);
        assertThatThrownBy(() -> insertar("UNIFORME", null, "APROBADO", "EFECTIVO", "FD-ref-x", null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_referencia_wompi_ck");
    }

    @Test
    void unMetodoDesconocidoSeRechaza() {
        assertThatThrownBy(() -> insertar("UNIFORME", null, "APROBADO", "BITCOIN", "FD-ref-y", null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_metodo_ck");
    }

    @Test
    void torneoYUniformeSonTiposValidosYOtroNo() {
        insertar("TORNEO", null, "PENDIENTE", null, null, null);
        insertar("UNIFORME", null, "PENDIENTE", null, null, null);
        insertar("INSCRIPCION", null, "PENDIENTE", null, null, null);
        assertThatThrownBy(() -> insertar("OTRO", null, "PENDIENTE", null, null, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_tipo_ck");
    }

    @Test
    void elPeriodoSoloEsObligatorioEnLaMensualidad() {
        assertThatThrownBy(() -> insertar("MENSUALIDAD", null, "PENDIENTE", null, null, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_periodo_ck");
        insertar("MENSUALIDAD", NOVIEMBRE, "PENDIENTE", null, null, null);
        insertar("TORNEO", null, "PENDIENTE", null, null, null);
        insertar("UNIFORME", NOVIEMBRE, "PENDIENTE", null, null, null); // opcional: si viene, es un primer día de mes
        assertThatThrownBy(() -> insertar("UNIFORME", LocalDate.of(2026, 11, 15), "PENDIENTE", null, null, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_periodo_ck");
    }

    @Test
    void laMensualidadSigueSiendoUnicaPorDeportistaYPeriodo() {
        insertar("MENSUALIDAD", NOVIEMBRE, "PENDIENTE", null, null, null);
        assertThatThrownBy(() -> insertar("MENSUALIDAD", NOVIEMBRE, "PENDIENTE", null, null, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_mensualidad_unica_idx");
        insertar("MENSUALIDAD", NOVIEMBRE, "RECHAZADO", null, null, null); // los rechazados no cuentan
    }

    @Test
    void metodoAdmiteElNombreMasLargo() {
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'pago' AND column_name = 'metodo'""", Integer.class))
                .isGreaterThanOrEqualTo("BOTON_BANCOLOMBIA".length());
    }

    @Test
    void laVistaSigueDandoVencidoYAhoraIncluyeLaTransaccion() {
        jdbc.getJdbcOperations().update("""
                INSERT INTO pago (id_deportista, tipo_pago, fecha_vencimiento, monto, concepto, estado_pago)
                VALUES ('%s', 'UNIFORME', '2020-01-01', 1000, 'Uniforme', 'PENDIENTE')""".formatted(deportista));
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT vencido FROM pago_con_estado WHERE id_deportista = '%s'".formatted(deportista), Boolean.class)).isTrue();
        assertThat(jdbc.getJdbcOperations().queryForList(
                "SELECT id_transaccion_wompi FROM pago_con_estado WHERE id_deportista = '%s'".formatted(deportista))).hasSize(1);
    }

    @Test
    void idTransaccionWompiEsVarchar60() {
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'pago' AND column_name = 'id_transaccion_wompi'""", Integer.class))
                .isEqualTo(60);
    }

    @Test
    void unaTransaccionNoPuedeAprobarDosCobrosPeroLosNulosSePermiten() {
        insertar("UNIFORME", null, "PENDIENTE", null, null, null);
        insertar("UNIFORME", null, "PENDIENTE", null, null, null);
        insertar("UNIFORME", null, "APROBADO", "PSE", "FD-ref-1", "1234-1610641025-49201");
        assertThatThrownBy(() -> insertar("TORNEO", null, "APROBADO", "PSE", "FD-ref-2", "1234-1610641025-49201"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("pago_id_transaccion_wompi_idx");
    }

    @Test
    void correrLaMigracionDosVecesSeDetieneConUnMensajeClaro() throws IOException {
        String sql = Files.readString(PostgresDePrueba.MIGRACIONES.resolve("009_pagos_wompi.sql"));
        // Por una conexión propia: el script abre su transacción, falla y esa conexión no debe volver al pool
        assertThatThrownBy(() -> PostgresDePrueba.ejecutarConConexionPropia(sql))
                .hasMessageContaining("ya tiene la columna id_transaccion_wompi");
        // y no dejó nada a medias: las restricciones siguen siendo las nuevas
        insertar("TORNEO", null, "PENDIENTE", null, null, null);
    }

    @Test
    void laMigracionEstaEnElDirectorio() {
        assertThat(Path.of(PostgresDePrueba.MIGRACIONES.toString(), "009_pagos_wompi.sql")).exists();
    }
}
