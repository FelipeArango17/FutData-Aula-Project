package co.edu.futdata.util.security;

import co.edu.futdata.config.CronProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Vía de la tarea programada (n8n) para {@code POST /api/pagos/generar-ciclos}: si la petición trae la cabecera
 * {@value #CABECERA} igual a {@code N8N_CRON_TOKEN}, queda autenticada con el rol {@value #ROL} (que solo ese endpoint acepta,
 * con {@code @PreAuthorize}). No es un bean de Spring a propósito: se arma en {@link SeguridadConfig}, dentro de la cadena de
 * seguridad, para que no se registre también como filtro suelto del servidor.
 *
 * <ul>
 *   <li><b>Solo esa ruta y ese método.</b> En cualquier otra petición no hace nada: el token no abre ningún otro endpoint.</li>
 *   <li><b>Deshabilitada sin la variable.</b> Con {@code N8N_CRON_TOKEN} vacía, nunca autentica.</li>
 *   <li><b>Token incorrecto o ausente:</b> no autentica; sin JWT, la cadena responde {@code 401}. Si la petición trae
 *       {@code Authorization}, manda el JWT y esta vía se ignora.</li>
 *   <li><b>Comparación en tiempo constante:</b> se comparan los SHA-256 de ambos valores con {@link MessageDigest#isEqual},
 *       así ni el contenido ni el largo del token se filtran por el tiempo de respuesta.</li>
 * </ul>
 * El token nunca se registra en los logs.
 */
public class CronTokenFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Cron-Token";
    public static final String ROL = "CRON";
    public static final String RUTA = "/api/pagos/generar-ciclos";

    private final byte[] huellaDelToken;
    private final RequestMatcher ruta = org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
            .withDefaults().matcher(HttpMethod.POST, RUTA);

    public CronTokenFilter(CronProperties propiedades) {
        this.huellaDelToken = propiedades.habilitado() ? sha256(propiedades.token()) : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (huellaDelToken != null && request.getHeader(HttpHeaders.AUTHORIZATION) == null && ruta.matches(request)) {
            String recibido = request.getHeader(CABECERA);
            if (recibido != null && MessageDigest.isEqual(huellaDelToken, sha256(recibido))) {
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "n8n-cron", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROL))));
            }
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
