package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.api.dto.UsuarioMapper;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.datasource.external.INotificador;
import co.edu.futdata.datasource.external.NotificacionNoEnviadaException;
import co.edu.futdata.domain.entities.CodigoVerificacion;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.domain.rules.GeneradorCodigo;
import co.edu.futdata.domain.rules.ReglasVerificacion;
import co.edu.futdata.persistence.dao.ICodigoVerificacionDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.CodigoNoEnviadoException;
import co.edu.futdata.services.facade.CodigoVerificacionException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.ICodigoVerificacionService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodigoVerificacionServiceImpl implements ICodigoVerificacionService {
    private final IUsuarioDAO usuarios;
    private final ICodigoVerificacionDAO codigos;
    private final INotificador notificador;
    private final GeneradorCodigo generador;
    private final Clock reloj;

    @Autowired
    public CodigoVerificacionServiceImpl(IUsuarioDAO usuarios, ICodigoVerificacionDAO codigos,
                                         INotificador notificador) {
        this(usuarios, codigos, notificador, new GeneradorCodigo(), Clock.systemUTC());
    }

    CodigoVerificacionServiceImpl(IUsuarioDAO usuarios, ICodigoVerificacionDAO codigos,
                                  INotificador notificador, GeneradorCodigo generador, Clock reloj) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.notificador = notificador;
        this.generador = generador;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Codigo2FAResponseDTO solicitar(UUID idUsuario) {
        Usuario usuario = usuarioActivo(idUsuario);
        Instant ahora = reloj.instant();
        codigos.bloquearUsuario(usuario.getId());
        codigos.limpiarVencidos(usuario.getId(), ahora);
        CodigoVerificacion actual = codigos.buscarUltimo(usuario.getId()).orElse(null);
        revisarBloqueo(actual, ahora);
        if (vigente(actual, ahora) && ahora.isBefore(actual.getUltimoEnvio().plus(ReglasVerificacion.INTERVALO_REENVIO))) {
            return respuesta(usuario, actual, ahora);
        }
        codigos.invalidarPendientes(usuario.getId());
        return emitir(usuario, ahora);
    }

    @Override
    @Transactional
    public Codigo2FAResponseDTO reenviar(UUID idUsuario) {
        Usuario usuario = usuarioActivo(idUsuario);
        Instant ahora = reloj.instant();
        codigos.bloquearUsuario(usuario.getId());
        codigos.limpiarVencidos(usuario.getId(), ahora);
        CodigoVerificacion actual = codigos.buscarUltimo(usuario.getId()).orElse(null);
        revisarBloqueo(actual, ahora);
        if (actual != null && ahora.isBefore(actual.getUltimoEnvio().plus(ReglasVerificacion.INTERVALO_REENVIO))) {
            long ms = actual.getUltimoEnvio().plus(ReglasVerificacion.INTERVALO_REENVIO).toEpochMilli()
                    - ahora.toEpochMilli();
            throw new CodigoVerificacionException("DEMASIADO_PRONTO",
                    "Debes esperar antes de solicitar otro código.", ms);
        }
        codigos.invalidarPendientes(usuario.getId());
        return emitir(usuario, ahora);
    }

    @Override
    @Transactional(noRollbackFor = CodigoVerificacionException.class)
    public UsuarioResponseDTO verificar(UUID idUsuario, String codigo) {
        Usuario usuario = usuarioActivo(idUsuario);
        codigos.bloquearUsuario(usuario.getId());
        CodigoVerificacion actual = codigos.buscarUltimo(usuario.getId())
                .orElseThrow(() -> new CodigoVerificacionException("SIN_SOLICITUD",
                        "No hay un código pendiente de verificación."));
        Instant ahora = reloj.instant();
        revisarBloqueo(actual, ahora);
        if (!ReglasVerificacion.formatoValido(codigo)) {
            throw new DatosInvalidosException(Map.of("codigo", "debe tener exactamente 6 dígitos"));
        }
        if (actual.isUsado()) {
            throw new CodigoVerificacionException("USADO", "El código ya fue utilizado. Solicita uno nuevo.");
        }
        if (!ahora.isBefore(actual.getExpiraEn())) {
            throw new CodigoVerificacionException("VENCIDO", "El código expiró. Solicita uno nuevo.");
        }
        if (!generador.coincide(codigo, actual.getCodigoHash())) {
            int intentos = codigos.incrementarIntentos(actual.getId());
            if (intentos >= ReglasVerificacion.MAX_INTENTOS) {
                Instant hasta = ahora.plus(ReglasVerificacion.BLOQUEO);
                codigos.bloquear(actual.getId(), hasta);
                throw new CodigoVerificacionException("BLOQUEADO",
                        "Se alcanzó el máximo de intentos. Intenta de nuevo más tarde.",
                        ReglasVerificacion.BLOQUEO.toMillis());
            }
            throw new CodigoVerificacionException("INCORRECTO",
                    "El código no es correcto.", null);
        }
        codigos.marcarUsado(actual.getId());
        codigos.marcarSesionVerificada(usuario.getId(), ahora.plus(ReglasVerificacion.VIGENCIA_SESION));
        return UsuarioMapper.toDTO(usuario);
    }

    private Codigo2FAResponseDTO emitir(Usuario usuario, Instant ahora) {
        String codigo = generador.generar();
        CodigoVerificacion nuevo = new CodigoVerificacion();
        nuevo.setUsuarioId(usuario.getId());
        nuevo.setCodigoHash(generador.hash(codigo));
        nuevo.setExpiraEn(ahora.plus(ReglasVerificacion.VIGENCIA_CODIGO));
        nuevo.setUltimoEnvio(ahora);
        UUID id = codigos.guardar(nuevo);
        nuevo.setId(id);
        try {
            notificador.notificar(INotificador.CODIGO_2FA, Map.of(
                    "correo", usuario.getCorreo(),
                    "nombre", usuario.getNombre(),
                    "codigo", codigo,
                    "vigenciaMinutos", ReglasVerificacion.VIGENCIA_CODIGO.toMinutes()));
        } catch (NotificacionNoEnviadaException e) {
            throw new CodigoNoEnviadoException();
        }
        return respuesta(usuario, nuevo, ahora);
    }

    /** El usuario del token. Sin perfil en {@code usuario} (cuenta de Auth sin registrar en FutData): 404. */
    private Usuario usuarioActivo(UUID idUsuario) {
        if (idUsuario == null) {
            throw new IllegalArgumentException("El id del usuario sale del JWT y no puede ser nulo");
        }
        Usuario usuario = usuarios.buscarPorId(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta no está registrada en FutData."));
        if (!usuario.isActivo()) {
            throw new RecursoNoEncontradoException("La cuenta no está activa.");
        }
        return usuario;
    }

    private static boolean vigente(CodigoVerificacion codigo, Instant ahora) {
        return codigo != null && !codigo.isUsado() && ahora.isBefore(codigo.getExpiraEn());
    }

    private static void revisarBloqueo(CodigoVerificacion codigo, Instant ahora) {
        if (codigo != null && codigo.getBloqueadoHasta() != null && ahora.isBefore(codigo.getBloqueadoHasta())) {
            long ms = codigo.getBloqueadoHasta().toEpochMilli() - ahora.toEpochMilli();
            throw new CodigoVerificacionException("BLOQUEADO",
                    "La verificación está bloqueada temporalmente.", ms);
        }
    }

    private static Codigo2FAResponseDTO respuesta(Usuario usuario, CodigoVerificacion codigo, Instant ahora) {
        long reenvio = Math.max(0, codigo.getUltimoEnvio().plus(ReglasVerificacion.INTERVALO_REENVIO)
                .toEpochMilli() - ahora.toEpochMilli());
        return new Codigo2FAResponseDTO(enmascarar(usuario.getCorreo()),
                ReglasVerificacion.VIGENCIA_CODIGO.toSeconds(), reenvio);
    }

    private static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 0) return "***";
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
