package co.edu.futdata.api.controllers;

import co.edu.futdata.services.facade.Solicitante;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** Arma el {@link Solicitante} (el {@code sub} del token y su rol) desde la autenticación de la petición. */
final class Solicitantes {

    private Solicitantes() {
    }

    /**
     * El {@code sub} del token y su rol (si hubiera varios, el de más permisos). Sin ningún rol conocido, el rol queda
     * vacío: el servicio decide, y los servicios que filtran por rol fallan cerrado con ese valor. Sin token (una
     * autenticación que no es JWT) no hay a quién identificar: {@code 403}.
     */
    static Solicitante de(Jwt jwt, Authentication autenticacion) {
        if (jwt == null || autenticacion == null) {
            throw new AccessDeniedException("No se pudo identificar a quien hace la petición");
        }
        Set<String> roles = autenticacion.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());
        String rol = Stream.of("ADMINISTRADOR", "ENTRENADOR", "TESORERO", "ACUDIENTE")
                .filter(roles::contains).findFirst().orElse("");
        return new Solicitante(UUID.fromString(jwt.getSubject()), rol);
    }
}
