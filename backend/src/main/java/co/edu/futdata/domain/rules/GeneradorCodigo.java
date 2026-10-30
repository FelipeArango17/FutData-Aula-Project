package co.edu.futdata.domain.rules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class GeneradorCodigo {
    private static final int DIGITOS = 1_000_000;
    private static final int TAMANO_SAL = 16;
    private final SecureRandom aleatorio;

    public GeneradorCodigo() {
        this(new SecureRandom());
    }

    GeneradorCodigo(SecureRandom aleatorio) {
        this.aleatorio = aleatorio;
    }

    public String generar() {
        return "%06d".formatted(aleatorio.nextInt(DIGITOS));
    }

    public String hash(String codigo) {
        byte[] sal = new byte[TAMANO_SAL];
        aleatorio.nextBytes(sal);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sal) + "$"
                + digest(sal, codigo);
    }

    public boolean coincide(String codigo, String almacenado) {
        if (codigo == null || almacenado == null) return false;
        int separador = almacenado.indexOf('$');
        if (separador <= 0 || separador == almacenado.length() - 1) return false;
        try {
            byte[] sal = Base64.getUrlDecoder().decode(almacenado.substring(0, separador));
            byte[] esperado = Base64.getUrlDecoder().decode(almacenado.substring(separador + 1));
            byte[] actual = Base64.getUrlDecoder().decode(digest(sal, codigo));
            return MessageDigest.isEqual(actual, esperado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String digest(byte[] sal, String codigo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sal);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    digest.digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El algoritmo de hash no está disponible", e);
        }
    }
}
