package co.edu.futdata.util.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.futdata.persistence.dao.IUsuarioDAO;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * JwtValidator con firmas reales: un servidor HTTP local publica las llaves públicas (como el JWKS de Supabase) y
 * los tokens se firman con llaves generadas en la prueba. Sin red ni Supabase.
 */
class JwtValidatorTest {

    private static HttpServer servidor;
    private static String supabaseUrl;
    private static ECKey llaveEc;
    private static RSAKey llaveRsa;
    private static JwtDecoder decoder;

    @BeforeAll
    static void publicarLlaves() throws Exception {
        llaveEc = new ECKeyGenerator(Curve.P_256).keyID("ec-1").generate();
        llaveRsa = new RSAKeyGenerator(2048).keyID("rsa-1").generate();
        byte[] jwks = new JWKSet(List.of(llaveEc.toPublicJWK(), llaveRsa.toPublicJWK())).toString()
                .getBytes(StandardCharsets.UTF_8);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/auth/v1/.well-known/jwks.json", intercambio -> {
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, jwks.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(jwks);
            }
        });
        servidor.start();
        supabaseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        decoder = JwtValidator.decodificador(supabaseUrl + "/");
    }

    @AfterAll
    static void detener() {
        servidor.stop(0);
    }

    private static JWTClaimsSet.Builder reclamos() {
        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(supabaseUrl + "/auth/v1")
                .audience("authenticated")
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(3600)));
    }

    private static String firmar(JWSAlgorithm algoritmo, String kid, JWSSigner firmante, JWTClaimsSet reclamos)
            throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algoritmo).keyID(kid).build(), reclamos);
        jwt.sign(firmante);
        return jwt.serialize();
    }

    private static String conEc(JWTClaimsSet reclamos) throws JOSEException {
        return firmar(JWSAlgorithm.ES256, "ec-1", new ECDSASigner(llaveEc), reclamos);
    }

    @Nested
    class Decodificador {

        @Test
        void aceptaUnTokenEs256DeSupabase() throws Exception {
            JWTClaimsSet reclamos = reclamos().build();

            Jwt jwt = decoder.decode(conEc(reclamos));

            assertThat(jwt.getSubject()).isEqualTo(reclamos.getSubject());
            assertThat(jwt.getAudience()).containsExactly("authenticated");
        }

        @Test
        void aceptaUnTokenRs256() throws Exception {
            String token = firmar(JWSAlgorithm.RS256, "rsa-1", new RSASSASigner(llaveRsa), reclamos().build());

            assertThat(decoder.decode(token).getSubject()).isNotBlank();
        }

        @Test
        void rechazaUnTokenVencido() throws Exception {
            Instant hace2Horas = Instant.now().minusSeconds(7200);
            String token = conEc(reclamos().issueTime(Date.from(hace2Horas))
                    .expirationTime(Date.from(hace2Horas.plusSeconds(3600))).build());

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }

        @Test
        void rechazaOtroEmisor() throws Exception {
            String token = conEc(reclamos().issuer("https://otro-proyecto.supabase.co/auth/v1").build());

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }

        @Test
        void rechazaUnTokenQueNoEsDeUnUsuarioAutenticado() throws Exception {
            String anonimo = conEc(reclamos().audience("anon").build());
            String sinAudiencia = conEc(reclamos().audience((String) null).build());

            assertThatThrownBy(() -> decoder.decode(anonimo)).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder.decode(sinAudiencia)).isInstanceOf(JwtException.class);
        }

        @Test
        void rechazaUnaFirmaConOtraLlave() throws Exception {
            ECKey ajena = new ECKeyGenerator(Curve.P_256).keyID("ec-1").generate();
            String token = firmar(JWSAlgorithm.ES256, "ec-1", new ECDSASigner(ajena), reclamos().build());

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }

        @Test
        void rechazaElSecretoCompartidoHs256() throws Exception {
            byte[] secreto = new byte[32];
            String token = firmar(JWSAlgorithm.HS256, null, new MACSigner(secreto), reclamos().build());

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }

        @Test
        void rechazaTextoQueNoEsUnJwt() {
            assertThatThrownBy(() -> decoder.decode("no-es-un-token")).isInstanceOf(JwtException.class);
        }

        @Test
        void sinSupabaseUrlTodoTokenSeRechaza() throws Exception {
            JwtDecoder sinConfigurar = JwtValidator.decodificador(" ");
            String token = conEc(reclamos().build());

            assertThatThrownBy(() -> sinConfigurar.decode(token))
                    .isInstanceOf(BadJwtException.class)
                    .hasMessageContaining("SUPABASE_URL");
        }
    }

    @Nested
    class Roles {

        private static final UUID ADMIN = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");

        private final IUsuarioDAO usuarios = mock(IUsuarioDAO.class);

        Roles() {
            when(usuarios.rolesActivos(any())).thenReturn(List.of());
            when(usuarios.rolesActivos(ADMIN)).thenReturn(List.of("ADMINISTRADOR", "TESORERO"));
        }

        private Jwt jwt(String sub) {
            return Jwt.withTokenValue("t").header("alg", "ES256").subject(sub).claim("aud", List.of("authenticated")).build();
        }

        @Test
        void losRolesDeLaBaseSonAutoridadesConPrefijoRole() {
            var autenticacion = new JwtValidator(usuarios).convert(jwt(ADMIN.toString()));

            assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMINISTRADOR", "ROLE_TESORERO");
            assertThat(autenticacion.getName()).isEqualTo(ADMIN.toString());
            assertThat(autenticacion.isAuthenticated()).isTrue();
        }

        @Test
        void sinUsuarioOConLaCuentaInactivaNoTieneRoles() {
            var autenticacion = new JwtValidator(usuarios).convert(jwt(UUID.randomUUID().toString()));

            assertThat(autenticacion.getAuthorities()).isEmpty();
        }

        @Test
        void unSubQueNoEsUuidNoConsultaLaBase() {
            var autenticacion = new JwtValidator(usuarios).convert(jwt("no-es-uuid"));

            assertThat(autenticacion.getAuthorities()).isEmpty();
            verify(usuarios, never()).rolesActivos(any());
        }
    }
}
