package co.edu.futdata.services.facade;

import java.util.List;

public class CodigoVerificacionException extends RuntimeException {
    private final String codigo;
    private final Long msRestantes;

    public CodigoVerificacionException(String codigo, String mensaje) {
        this(codigo, mensaje, null);
    }

    public CodigoVerificacionException(String codigo, String mensaje, Long msRestantes) {
        super(mensaje);
        this.codigo = codigo;
        this.msRestantes = msRestantes;
    }

    public String getCodigo() { return codigo; }
    public Long getMsRestantes() { return msRestantes; }
    public List<String> getCampos() { return List.of("codigo"); }
}
