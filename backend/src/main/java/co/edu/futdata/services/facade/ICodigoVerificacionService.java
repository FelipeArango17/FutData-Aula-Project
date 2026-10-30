package co.edu.futdata.services.facade;

import co.edu.futdata.api.dto.Codigo2FAResponseDTO;
import co.edu.futdata.api.dto.UsuarioResponseDTO;

public interface ICodigoVerificacionService {
    Codigo2FAResponseDTO solicitar(String correo);
    Codigo2FAResponseDTO reenviar(String correo);
    UsuarioResponseDTO verificar(String correo, String codigo);
}
