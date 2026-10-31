package co.edu.futdata.datasource.external;

import java.util.UUID;

public interface ISupabaseAuthAdmin {
    UUID invitar(String correo);
    void eliminar(UUID idUsuario);
    void cambiarAcceso(UUID idUsuario, boolean activo);
}
