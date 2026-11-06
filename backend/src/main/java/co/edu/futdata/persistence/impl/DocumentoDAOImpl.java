package co.edu.futdata.persistence.impl;

import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.EstadoDocumento;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IDocumentoDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link IDocumentoDAO} con JDBC. Sin registros: ni correos, ni rutas, ni comentarios (son datos de menores y de sus
 * familias).
 */
@Repository
public class DocumentoDAOImpl implements IDocumentoDAO {

    /**
     * El rol de quien subió el documento: ADMINISTRADOR si la cuenta lo tiene, si no ACUDIENTE, si no {@code null}
     * (incluida una cuenta que ya no existe, porque {@code cargado_por} queda en {@code null}).
     */
    private static final String SELECT = """
            select doc.id_documento, doc.id_deportista, doc.tipo, doc.ruta, doc.nombre_original, doc.tipo_contenido,
                   doc.tamano_bytes, doc.estado, doc.comentario, doc.fecha_vencimiento,
                   case when exists (select 1 from usuario_rol x join rol r on r.id_rol = x.id_rol
                                      where x.id_usuario = doc.cargado_por and r.nombre = 'ADMINISTRADOR')
                        then 'ADMINISTRADOR'
                        when exists (select 1 from usuario_rol x join rol r on r.id_rol = x.id_rol
                                      where x.id_usuario = doc.cargado_por and r.nombre = 'ACUDIENTE')
                        then 'ACUDIENTE' end as rol_cargado_por,
                   uc.correo as cargado_por_correo, doc.cargado_en,
                   ur.correo as revisado_por_correo, doc.revisado_en
              from documento doc
              left join usuario uc on uc.id_usuario = doc.cargado_por
              left join usuario ur on ur.id_usuario = doc.revisado_por
            """;

    private static final RowMapper<Documento> FILA = DocumentoDAOImpl::mapear;

    private final NamedParameterJdbcTemplate jdbc;

    public DocumentoDAOImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Documento insertar(UUID idDeportista, String tipo, String ruta, String nombreOriginal, String tipoContenido,
                              int tamanoBytes, LocalDate fechaVencimiento, UUID idUsuarioCarga) {
        UUID id;
        try {
            id = jdbc.queryForObject("""
                    insert into documento (id_deportista, tipo, ruta, nombre_original, tipo_contenido, tamano_bytes,
                                           fecha_vencimiento, cargado_por)
                    values (:deportista, :tipo, :ruta, :nombre, :contenido, :tamano, :vencimiento, :cargadoPor)
                    returning id_documento""",
                    new MapSqlParameterSource("deportista", idDeportista).addValue("tipo", tipo).addValue("ruta", ruta)
                            .addValue("nombre", nombreOriginal).addValue("contenido", tipoContenido)
                            .addValue("tamano", tamanoBytes).addValue("vencimiento", fechaVencimiento)
                            .addValue("cargadoPor", idUsuarioCarga), UUID.class);
        } catch (DuplicateKeyException e) {
            throw new DatoDuplicadoException("ruta", e);
        }
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("El documento recién insertado no se encontró"));
    }

    @Override
    public List<Documento> listarPorDeportista(UUID idDeportista) {
        return jdbc.query(SELECT + " where doc.id_deportista = :id order by doc.cargado_en desc, doc.id_documento",
                new MapSqlParameterSource("id", idDeportista), FILA);
    }

    @Override
    public Optional<Documento> buscarPorId(UUID idDocumento) {
        return jdbc.query(SELECT + " where doc.id_documento = :id", new MapSqlParameterSource("id", idDocumento), FILA)
                .stream().findFirst();
    }

    @Override
    public boolean bloquearDeportista(UUID idDeportista) {
        return !jdbc.queryForList("select id_deportista from deportista where id_deportista = :id for no key update",
                new MapSqlParameterSource("id", idDeportista), UUID.class).isEmpty();
    }

    @Override
    public boolean existeActivoDelTipo(UUID idDeportista, String tipo) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from documento
                                where id_deportista = :deportista and tipo = :tipo
                                  and estado in ('PENDIENTE', 'APROBADO'))""",
                new MapSqlParameterSource("deportista", idDeportista).addValue("tipo", tipo), Boolean.class));
    }

    @Override
    public boolean aprobar(UUID idDocumento, UUID idUsuarioRevisor) {
        return jdbc.update("""
                update documento
                   set estado = 'APROBADO', revisado_por = :revisor, revisado_en = now()
                 where id_documento = :id and estado = 'PENDIENTE'""",
                new MapSqlParameterSource("id", idDocumento).addValue("revisor", idUsuarioRevisor)) == 1;
    }

    @Override
    public boolean rechazar(UUID idDocumento, UUID idUsuarioRevisor, String comentario) {
        return jdbc.update("""
                update documento
                   set estado = 'RECHAZADO', comentario = :comentario, revisado_por = :revisor, revisado_en = now()
                 where id_documento = :id and estado = 'PENDIENTE'""",
                new MapSqlParameterSource("id", idDocumento).addValue("revisor", idUsuarioRevisor)
                        .addValue("comentario", comentario)) == 1;
    }

    @Override
    public boolean esAcudienteDe(UUID idUsuario, UUID idDeportista) {
        if (idUsuario == null || idDeportista == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from acudiente_deportista ad
                                 join acudiente a on a.id_acudiente = ad.id_acudiente
                                where a.id_usuario = :usuario and ad.id_deportista = :deportista)""",
                new MapSqlParameterSource("usuario", idUsuario).addValue("deportista", idDeportista), Boolean.class));
    }

    private static Documento mapear(ResultSet r, int fila) throws SQLException {
        OffsetDateTime revisadoEn = r.getObject("revisado_en", OffsetDateTime.class);
        return new Documento(
                r.getObject("id_documento", UUID.class),
                r.getObject("id_deportista", UUID.class),
                r.getString("tipo"),
                r.getString("ruta"),
                r.getString("nombre_original"),
                r.getString("tipo_contenido"),
                r.getInt("tamano_bytes"),
                EstadoDocumento.valueOf(r.getString("estado")),
                r.getString("comentario"),
                r.getObject("fecha_vencimiento", LocalDate.class),
                r.getString("rol_cargado_por"),
                r.getString("cargado_por_correo"),
                r.getObject("cargado_en", OffsetDateTime.class).toInstant(),
                r.getString("revisado_por_correo"),
                revisadoEn == null ? null : revisadoEn.toInstant());
    }
}
