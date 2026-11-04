package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.EstadoPago;
import co.edu.futdata.domain.entities.Pago;
import co.edu.futdata.domain.entities.TipoPago;
import co.edu.futdata.persistence.dao.AcudienteConCorreo;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.DeportistaDeCiclo;
import co.edu.futdata.persistence.dao.FiltroPagos;
import co.edu.futdata.persistence.dao.IPagoDAO;
import co.edu.futdata.persistence.dao.PaginaPagos;
import co.edu.futdata.util.reports.ComprobantePagoDTO.MetodoPago;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code pago} con {@link NamedParameterJdbcTemplate}; las lecturas usan la vista {@code pago_con_estado} (columna "vencido"). */
@Repository
public class PagoDAOImpl implements IPagoDAO {

    /** Restricciones unique (migraciones 004 y 009), para decir qué se repite. */
    static final String UNICO_MENSUALIDAD = "pago_mensualidad_unica_idx";
    static final String UNICO_TRANSACCION = "pago_id_transaccion_wompi_idx";

    private static final String SELECT = """
            SELECT p.id_pago, p.id_deportista, p.tipo_pago, p.concepto, p.monto, p.periodo, p.fecha_vencimiento, p.estado_pago,
                   p.fecha_pago, p.metodo, p.referencia_wompi, p.id_transaccion_wompi, p.numero_comprobante, p.nota_interna,
                   p.registrado_por, p.creado_en, p.vencido, d.nombre || ' ' || d.apellido AS nombre_deportista
              FROM pago_con_estado p JOIN deportista d ON d.id_deportista = p.id_deportista""";

    private static final RowMapper<Pago> FILA_A_PAGO = PagoDAOImpl::aPago;

    private final NamedParameterJdbcTemplate jdbc;

    public PagoDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID crear(Pago p) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO pago (id_deportista, tipo_pago, concepto, monto, periodo, fecha_vencimiento, estado_pago, registrado_por)
                    VALUES (:deportista, :tipo, :concepto, :monto, :periodo, :vencimiento, 'PENDIENTE', :registradoPor)
                    RETURNING id_pago""",
                    new MapSqlParameterSource().addValue("deportista", p.idDeportista()).addValue("tipo", p.tipo().name())
                            .addValue("concepto", p.concepto()).addValue("monto", p.monto()).addValue("periodo", p.periodo())
                            .addValue("vencimiento", p.fechaVencimiento()).addValue("registradoPor", p.registradoPor()),
                    UUID.class);
        } catch (DuplicateKeyException e) {
            throw traducir(e);
        }
    }

    @Override
    public Optional<Pago> buscarPorId(UUID id) {
        return jdbc.query(SELECT + " WHERE p.id_pago = :id", Map.of("id", id), FILA_A_PAGO).stream().findFirst();
    }

    @Override
    public Optional<Pago> buscarPorIdParaActualizar(UUID id) {
        bloquear("id_pago = :v", id);
        return buscarPorId(id);
    }

    @Override
    public Optional<Pago> buscarPorReferenciaParaActualizar(String referencia) {
        bloquear("referencia_wompi = :v", referencia);
        return jdbc.query(SELECT + " WHERE p.referencia_wompi = :referencia", Map.of("referencia", referencia), FILA_A_PAGO)
                .stream().findFirst();
    }

    /** {@code FOR UPDATE} sobre la tabla base: la vista con join no admite el bloqueo en las dos tablas. */
    private void bloquear(String condicion, Object valor) {
        jdbc.queryForList("SELECT id_pago FROM pago WHERE " + condicion + " FOR UPDATE", Map.of("v", valor), UUID.class);
    }

    @Override
    public PaginaPagos listar(FiltroPagos filtro, String columnaOrden, boolean ascendente, int page, int size) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        StringBuilder donde = new StringBuilder(" WHERE 1 = 1");
        if (filtro.estado() != null) {
            donde.append(" AND p.estado_pago = :estado");
            parametros.addValue("estado", filtro.estado().name());
        }
        if (filtro.tipo() != null) {
            donde.append(" AND p.tipo_pago = :tipo");
            parametros.addValue("tipo", filtro.tipo().name());
        }
        if (filtro.idDeportista() != null) {
            donde.append(" AND p.id_deportista = :deportista");
            parametros.addValue("deportista", filtro.idDeportista());
        }
        if (filtro.periodo() != null) {
            donde.append(" AND p.periodo = :periodo");
            parametros.addValue("periodo", filtro.periodo());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM pago_con_estado p" + donde, parametros, Long.class);
        parametros.addValue("limite", size).addValue("desde", (long) page * size);
        // columnaOrden viene de una lista fija del servicio: nunca de la petición tal cual
        List<Pago> contenido = jdbc.query(SELECT + donde + " ORDER BY " + columnaOrden + (ascendente ? " ASC" : " DESC")
                + ", p.id_pago LIMIT :limite OFFSET :desde", parametros, FILA_A_PAGO);
        return new PaginaPagos(contenido, total == null ? 0 : total);
    }

    @Override
    public List<Pago> listarDeAcudiente(UUID idUsuario) {
        return jdbc.query(SELECT + "\n" + """
                 WHERE p.id_deportista IN (
                       SELECT ad.id_deportista FROM acudiente_deportista ad
                         JOIN acudiente a ON a.id_acudiente = ad.id_acudiente
                        WHERE a.id_usuario = :usuario)
                 ORDER BY p.fecha_vencimiento DESC, p.id_pago""", Map.of("usuario", idUsuario), FILA_A_PAGO);
    }

    @Override
    public boolean esAcudienteDe(UUID idUsuario, UUID idDeportista) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM acudiente_deportista ad JOIN acudiente a ON a.id_acudiente = ad.id_acudiente
                                WHERE a.id_usuario = :usuario AND ad.id_deportista = :deportista)""",
                Map.of("usuario", idUsuario, "deportista", idDeportista), Boolean.class));
    }

    @Override
    public List<UUID> acudientesDe(UUID idDeportista) {
        return jdbc.queryForList("SELECT id_acudiente FROM acudiente_deportista WHERE id_deportista = :d ORDER BY id_acudiente",
                Map.of("d", idDeportista), UUID.class);
    }

    @Override
    public List<UUID> idsDeportistas() {
        return jdbc.queryForList("SELECT id_deportista FROM deportista ORDER BY apellido, nombre, id_deportista", Map.of(), UUID.class);
    }

    @Override
    public boolean existeMensualidadNoRechazada(UUID idDeportista, LocalDate periodo) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pago WHERE id_deportista = :d AND tipo_pago = 'MENSUALIDAD' AND periodo = :periodo
                                  AND estado_pago <> 'RECHAZADO')""", Map.of("d", idDeportista, "periodo", periodo), Boolean.class));
    }

    @Override
    public List<DeportistaDeCiclo> deportistasParaCiclo() {
        // activo = false es como queda un deportista retirado desde la migración 008; estado_inscripcion (heredada) se respeta también
        return jdbc.query("""
                SELECT id_deportista, nombre || ' ' || apellido AS nombre, fecha_registro FROM deportista
                 WHERE activo AND estado_inscripcion IN ('PENDIENTE', 'ACTIVA')
                 ORDER BY fecha_registro, id_deportista""", Map.of(),
                (f, n) -> new DeportistaDeCiclo(f.getObject("id_deportista", UUID.class), f.getString("nombre"),
                        f.getObject("fecha_registro", LocalDate.class)));
    }

    @Override
    public boolean existeMensualidad(UUID idDeportista, LocalDate periodo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pago WHERE id_deportista = :d AND tipo_pago = 'MENSUALIDAD' AND periodo = :periodo)",
                Map.of("d", idDeportista, "periodo", periodo), Boolean.class));
    }

    @Override
    public boolean existeCobroDeTipo(UUID idDeportista, TipoPago tipo) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pago WHERE id_deportista = :d AND tipo_pago = :tipo)",
                Map.of("d", idDeportista, "tipo", tipo.name()), Boolean.class));
    }

    @Override
    public void bloquearDeportista(UUID idDeportista) {
        jdbc.queryForList("SELECT id_deportista FROM deportista WHERE id_deportista = :d FOR UPDATE", Map.of("d", idDeportista), UUID.class);
    }

    @Override
    public List<AcudienteConCorreo> acudientesConCorreo(UUID idDeportista) {
        List<AcudienteConCorreo> todos = jdbc.query("""
                SELECT a.nombre || ' ' || a.apellido AS nombre, btrim(a.correo) AS correo
                  FROM acudiente_deportista ad JOIN acudiente a ON a.id_acudiente = ad.id_acudiente
                 WHERE ad.id_deportista = :d AND a.correo IS NOT NULL AND btrim(a.correo) <> ''
                 ORDER BY a.apellido, a.nombre, a.id_acudiente""", Map.of("d", idDeportista),
                (f, n) -> new AcudienteConCorreo(f.getString("nombre"), f.getString("correo")));
        java.util.Set<String> vistos = new java.util.HashSet<>();
        return todos.stream().filter(a -> vistos.add(a.correo().toLowerCase(java.util.Locale.ROOT))).toList();
    }

    @Override
    public boolean transaccionYaUsada(String idTransaccionWompi) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pago WHERE id_transaccion_wompi = :t)",
                Map.of("t", idTransaccionWompi), Boolean.class));
    }

    @Override
    public Optional<String> categoriaDeDeportista(UUID idDeportista) {
        return jdbc.queryForList("""
                SELECT c.nombre FROM deportista d JOIN categoria c ON c.id_categoria = d.id_categoria
                 WHERE d.id_deportista = :d""", Map.of("d", idDeportista), String.class).stream().findFirst();
    }

    @Override
    public Optional<String> nombreAcudiente(UUID idDeportista, UUID idUsuarioPreferido) {
        return jdbc.queryForList("""
                SELECT a.nombre || ' ' || a.apellido FROM acudiente_deportista ad JOIN acudiente a ON a.id_acudiente = ad.id_acudiente
                 WHERE ad.id_deportista = :d ORDER BY (a.id_usuario IS NOT DISTINCT FROM :usuario) DESC, a.apellido, a.nombre, a.id_acudiente""",
                new MapSqlParameterSource().addValue("d", idDeportista).addValue("usuario", idUsuarioPreferido), String.class)
                .stream().findFirst();
    }

    @Override
    public List<Pago> hermanosPendientes(Pago pago) {
        return hermanos(pago, EstadoPago.PENDIENTE);
    }

    @Override
    public boolean hayHermanoAprobado(Pago pago) {
        return !hermanos(pago, EstadoPago.APROBADO).isEmpty();
    }

    /** Mismo deportista y tipo, y el mismo periodo (mensualidad) o el mismo concepto (los demás tipos). */
    private List<Pago> hermanos(Pago pago, EstadoPago estado) {
        boolean porPeriodo = pago.tipo() == TipoPago.MENSUALIDAD;
        return jdbc.query(SELECT + " WHERE p.id_deportista = :d AND p.tipo_pago = :tipo AND p.estado_pago = :estado AND p.id_pago <> :id AND "
                + (porPeriodo ? "p.periodo = :clave" : "p.concepto = :clave") + " ORDER BY p.creado_en, p.id_pago",
                new MapSqlParameterSource().addValue("d", pago.idDeportista()).addValue("tipo", pago.tipo().name())
                        .addValue("estado", estado.name()).addValue("id", pago.id())
                        .addValue("clave", porPeriodo ? pago.periodo() : pago.concepto()),
                FILA_A_PAGO);
    }

    @Override
    public Optional<Pago> pendienteEquivalente(Pago pago) {
        return hermanosPendientes(pago).stream()
                .filter(h -> h.monto() == pago.monto() && h.concepto().equals(pago.concepto())
                        && h.fechaVencimiento().equals(pago.fechaVencimiento()))
                .findFirst();
    }

    @Override
    public void asignarReferenciaWompi(UUID id, String referencia) {
        jdbc.update("UPDATE pago SET referencia_wompi = :referencia WHERE id_pago = :id", Map.of("referencia", referencia, "id", id));
    }

    @Override
    public String siguienteNumeroComprobante() {
        return jdbc.queryForObject("SELECT siguiente_numero_comprobante()", Map.of(), String.class);
    }

    @Override
    public void aprobar(UUID id, LocalDate fechaPago, MetodoPago metodo, String idTransaccionWompi, String numeroComprobante,
                        UUID registradoPor, String notaInterna) {
        try {
            jdbc.update("""
                    UPDATE pago SET estado_pago = 'APROBADO', fecha_pago = :fecha, metodo = :metodo,
                           id_transaccion_wompi = :transaccion, numero_comprobante = :numero,
                           registrado_por = coalesce(:registradoPor, registrado_por), nota_interna = :nota
                     WHERE id_pago = :id""",
                    new MapSqlParameterSource().addValue("id", id).addValue("fecha", fechaPago).addValue("metodo", metodo.name())
                            .addValue("transaccion", idTransaccionWompi).addValue("numero", numeroComprobante)
                            .addValue("registradoPor", registradoPor).addValue("nota", notaInterna));
        } catch (DuplicateKeyException e) {
            throw traducir(e);
        }
    }

    @Override
    public void rechazar(UUID id, String notaInterna, UUID registradoPor) {
        jdbc.update("""
                UPDATE pago SET estado_pago = 'RECHAZADO', nota_interna = :nota, registrado_por = coalesce(:registradoPor, registrado_por)
                 WHERE id_pago = :id""", new MapSqlParameterSource().addValue("id", id).addValue("nota", notaInterna)
                .addValue("registradoPor", registradoPor));
    }

    private static RuntimeException traducir(DuplicateKeyException e) {
        String mensaje = String.valueOf(e.getMostSpecificCause().getMessage());
        if (mensaje.contains("\"" + UNICO_MENSUALIDAD + "\"")) {
            return new DatoDuplicadoException("mensualidad", e);
        }
        if (mensaje.contains("\"" + UNICO_TRANSACCION + "\"")) {
            return new DatoDuplicadoException("transaccion", e);
        }
        return e;
    }

    private static Pago aPago(ResultSet f, int n) throws SQLException {
        String metodo = f.getString("metodo");
        java.sql.Timestamp creado = f.getTimestamp("creado_en");
        return new Pago(f.getObject("id_pago", UUID.class), f.getObject("id_deportista", UUID.class),
                TipoPago.valueOf(f.getString("tipo_pago")), f.getString("concepto"), f.getBigDecimal("monto").longValueExact(),
                f.getObject("periodo", LocalDate.class), f.getObject("fecha_vencimiento", LocalDate.class),
                EstadoPago.valueOf(f.getString("estado_pago")), f.getObject("fecha_pago", LocalDate.class),
                metodo == null ? null : MetodoPago.valueOf(metodo), f.getString("referencia_wompi"),
                f.getString("id_transaccion_wompi"), f.getString("numero_comprobante"), f.getString("nota_interna"),
                f.getObject("registrado_por", UUID.class), creado == null ? null : creado.toInstant(), f.getBoolean("vencido"),
                f.getString("nombre_deportista"));
    }
}
