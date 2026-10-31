package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import co.edu.futdata.api.dto.UsuarioRequestDTO;
import co.edu.futdata.datasource.external.ISupabaseAuthAdmin;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.services.facade.ConflictoException;
import co.edu.futdata.services.facade.DatosInvalidosException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsuarioServiceImplTest {
    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private IUsuarioDAO dao;
    private ISupabaseAuthAdmin auth;
    private ISesionVerificadaDAO sesiones;
    private UsuarioServiceImpl servicio;

    @BeforeEach
    void preparar() {
        dao = mock(IUsuarioDAO.class);
        auth = mock(ISupabaseAuthAdmin.class);
        sesiones = mock(ISesionVerificadaDAO.class);
        servicio = new UsuarioServiceImpl(dao, auth, sesiones);
    }

    @Test
    void rechazaDatosIncompletosSinCrearCuenta() {
        assertThatThrownBy(() -> servicio.crear(new UsuarioRequestDTO("", "", "", "")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("nombre")
                .hasMessageContaining("correo")
                .hasMessageContaining("rol");
        verifyNoInteractions(auth);
        verify(dao, never()).crear(any());
    }

    @Test
    void rechazaCorreoExistenteSinInvitar() {
        when(dao.existeCorreo("persona@pradofc.co")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(
                new UsuarioRequestDTO("Ana", "Pérez", "persona@pradofc.co", "acudiente")))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("correo");
        verifyNoInteractions(auth);
    }

    @Test
    void creaInvitacionRegistroYRol() {
        when(auth.invitar("ana@pradofc.co")).thenReturn(ID);
        Usuario guardado = usuario("Ana", "Pérez", "ACUDIENTE", true);
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(guardado));

        var respuesta = servicio.crear(new UsuarioRequestDTO(
                " Ana ", "Pérez", "ana@pradofc.co", "acudiente"));

        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.rol()).isEqualTo("acudiente");
        verify(dao).crear(any(Usuario.class));
        verify(dao).asignarRol(ID, "ACUDIENTE");
    }

    @Test
    void noPermiteRevocarUltimoAdministrador() {
        Usuario admin = usuario("Admin", "Principal", "ADMINISTRADOR", true);
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(admin));
        when(dao.administradoresActivos()).thenReturn(1L);

        assertThatThrownBy(() -> servicio.cambiarAcceso(ID, false))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("administrador");
        verify(dao, never()).actualizarActivo(any(), anyBoolean());
        verifyNoInteractions(sesiones);
    }

    @Test
    void revocarElAccesoBorraTodasLasSesionesVerificadasDelUsuario() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(usuario("Ana", "Pérez", "ACUDIENTE", true)),
                Optional.of(usuario("Ana", "Pérez", "ACUDIENTE", false)));

        servicio.cambiarAcceso(ID, false);

        verify(dao).actualizarActivo(ID, false);
        verify(sesiones).eliminarDeUsuario(ID);
    }

    @Test
    void restablecerElAccesoNoCreaNiBorraSesiones() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(usuario("Ana", "Pérez", "ACUDIENTE", false)),
                Optional.of(usuario("Ana", "Pérez", "ACUDIENTE", true)));

        servicio.cambiarAcceso(ID, true);

        verify(dao).actualizarActivo(ID, true);
        verifyNoInteractions(sesiones);
    }

    @Test
    void siNoSePuedenBorrarLasSesionesSeRevierteElAccesoEnAuth() {
        when(dao.buscarPorId(ID)).thenReturn(Optional.of(usuario("Ana", "Pérez", "ACUDIENTE", true)));
        when(sesiones.eliminarDeUsuario(ID)).thenThrow(new IllegalStateException("base caída"));

        assertThatThrownBy(() -> servicio.cambiarAcceso(ID, false)).isInstanceOf(IllegalStateException.class);

        verify(auth).cambiarAcceso(ID, false);
        verify(auth).cambiarAcceso(ID, true);
    }

    private static Usuario usuario(String nombre, String apellido, String rol, boolean activo) {
        Usuario u = new Usuario();
        u.setId(ID); u.setNombre(nombre); u.setApellido(apellido); u.setCorreo("ana@pradofc.co");
        u.setRol(rol); u.setActivo(activo);
        return u;
    }
}
