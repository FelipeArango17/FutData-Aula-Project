package co.edu.futdata.services.impl;

import co.edu.futdata.api.dto.UsuarioMapper;
import co.edu.futdata.api.dto.UsuarioRequestDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.domain.rules.ReglasUsuario;
import co.edu.futdata.persistence.dao.DatoDuplicadoException;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import co.edu.futdata.services.facade.IUsuarioService;
import co.edu.futdata.services.facade.RecursoNoEncontradoException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioServiceImpl implements IUsuarioService {
    private static final String NO_EXISTE = "No existe un usuario con ese id.";
    private static final Logger log = LoggerFactory.getLogger(UsuarioServiceImpl.class);
    private final IUsuarioDAO usuarios;
    private final ISupabaseAuthAdmin auth;

    @Autowired
    public UsuarioServiceImpl(IUsuarioDAO usuarios, ISupabaseAuthAdmin auth) {
        this.usuarios = usuarios;
        this.auth = auth;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {
        return usuarios.listar().stream().map(UsuarioMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponseDTO consultar(UUID id) {
        return UsuarioMapper.toDTO(buscar(id));
    }

    @Override
    @Transactional
    public UsuarioResponseDTO crear(UsuarioRequestDTO datos) {
        if (datos == null) throw DatosInvalidosException.de("cuerpo", "es obligatorio");
        String nombre = limpiar(datos.nombre());
        String apellido = limpiar(datos.apellido());
        String correo = limpiar(datos.correo());
        String rol = datos.rol() == null ? null : datos.rol().trim().toUpperCase();
        Map<String, String> errores = ReglasUsuario.validar(nombre, apellido, correo, rol);
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        if (usuarios.existeCorreo(correo)) throw duplicado();
        UUID id = auth.invitar(correo);
        try {
            Usuario nuevo = new Usuario();
            nuevo.setId(id); nuevo.setNombre(nombre); nuevo.setApellido(apellido); nuevo.setCorreo(correo);
            usuarios.crear(nuevo);
            usuarios.asignarRol(id, rol);
            return consultar(id);
        } catch (DuplicateKeyException | DatoDuplicadoException e) {
            eliminarInvitacion(id);
            throw duplicado();
        } catch (RuntimeException e) {
            eliminarInvitacion(id);
            throw e;
        }
    }

    @Override
    @Transactional
    public UsuarioResponseDTO cambiarRol(UUID id, String rol) {
        Usuario actual = buscar(id);
        String nuevo = rol == null ? null : rol.trim().toUpperCase();
        Map<String, String> errores = ReglasUsuario.validar("válido", "válido", "a@b.co", nuevo);
        errores.remove("nombre"); errores.remove("apellido"); errores.remove("correo");
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        if (nuevo.equals(actual.getRol())) throw new ConflictoException("El usuario ya tiene ese rol.", List.of("rol"));
        usuarios.bloquearAdministradores();
        if ("ADMINISTRADOR".equals(actual.getRol()) && !"ADMINISTRADOR".equals(nuevo)
                && usuarios.administradoresActivos() <= 1) {
            throw new ConflictoException("Debe existir al menos un administrador activo.", List.of("rol"));
        }
        usuarios.actualizarRol(id, nuevo);
        return consultar(id);
    }

    @Override
    @Transactional
    public UsuarioResponseDTO cambiarAcceso(UUID id, boolean activo) {
        Usuario actual = buscar(id);
        if (actual.isActivo() == activo) {
            throw new ConflictoException(activo ? "El usuario ya tiene acceso." : "El acceso ya está revocado.",
                    List.of("activo"));
        }
        usuarios.bloquearAdministradores();
        if (!activo && "ADMINISTRADOR".equals(actual.getRol()) && usuarios.administradoresActivos() <= 1) {
            throw new ConflictoException("Debe existir al menos un administrador activo.", List.of("activo"));
        }
        auth.cambiarAcceso(id, activo);
        try {
            usuarios.actualizarActivo(id, activo);
        } catch (RuntimeException e) {
            try {
                auth.cambiarAcceso(id, !activo);
            } catch (RuntimeException rollback) {
                log.error("No se pudo revertir el acceso de Auth para {}", id, rollback);
            }
            throw e;
        }
        return consultar(id);
    }

    private void eliminarInvitacion(UUID id) {
        try {
            auth.eliminar(id);
        } catch (RuntimeException rollback) {
            log.error("No se pudo limpiar la cuenta de Auth {}", id, rollback);
        }
    }

    private Usuario buscar(UUID id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
    }

    private static String limpiar(String valor) { return valor == null ? null : valor.trim(); }
    private static ConflictoException duplicado() {
        return new ConflictoException("correo: ya está registrado en otro usuario", List.of("correo"));
    }
}
