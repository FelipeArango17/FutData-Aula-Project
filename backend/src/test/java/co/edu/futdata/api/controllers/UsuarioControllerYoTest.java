package co.edu.futdata.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.futdata.domain.entities.Categoria;
import co.edu.futdata.domain.entities.Entrenador;
import co.edu.futdata.domain.entities.Usuario;
import co.edu.futdata.persistence.dao.ICategoriaDAO;
import co.edu.futdata.persistence.dao.IEntrenadorDAO;
import co.edu.futdata.persistence.dao.ISesionVerificadaDAO;
import co.edu.futdata.persistence.dao.IUsuarioDAO;
import co.edu.futdata.util.security.JwtValidator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /api/usuarios/yo con un JWT que pasa por la cadena de seguridad real y el servicio real; solo los DAO están
 * simulados. Revisa el bloque {@code entrenador} del 4B-1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:pruebas.properties")
class UsuarioControllerYoTest {

    @TestConfiguration
    static class Configuracion {
        /** Sin base de datos: las anotaciones {@code @Transactional} del servicio real no abren conexión. */
        @Bean
        @Primary
        PlatformTransactionManager transaccionesSinBase() {
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definicion) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus estado) {
                }

                @Override
                public void rollback(TransactionStatus estado) {
                }
            };
        }
    }

    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID ENTRENADOR = UUID.fromString("e0000000-0000-0000-0000-00000000000a");
    private static final UUID SEMILLERO = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6c");
    private static final UUID SUB12 = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;

    @MockitoBean
    private IUsuarioDAO usuarios;

    @MockitoBean
    private ISesionVerificadaDAO sesiones;

    @MockitoBean
    private IEntrenadorDAO entrenadores;

    @MockitoBean
    private ICategoriaDAO categorias;

    @BeforeEach
    void preparar() {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "ES256")
                .subject(USUARIO.toString()).claim(JwtValidator.CLAIM_SESION, UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
        when(sesiones.estaVerificada(any(), any(), any())).thenReturn(true);
    }

    private void conRol(String rol) {
        when(usuarios.rolesActivos(USUARIO)).thenReturn(List.of(rol));
        Usuario u = new Usuario();
        u.setId(USUARIO);
        u.setNombre("Luis");
        u.setApellido("Pérez");
        u.setCorreo("luis@pradofc.co");
        u.setRol(rol);
        u.setActivo(true);
        when(usuarios.buscarPorId(USUARIO)).thenReturn(Optional.of(u));
    }

    private ResultActions yo() throws Exception {
        return mvc.perform(get("/api/usuarios/yo").header(HttpHeaders.AUTHORIZATION, "Bearer token"));
    }

    @Test
    void elEntrenadorVeSuIdYSusCategoriasEnOrdenOficial() throws Exception {
        conRol("ENTRENADOR");
        Entrenador e = new Entrenador();
        e.setId(ENTRENADOR);
        when(entrenadores.buscarPorIdUsuario(USUARIO)).thenReturn(Optional.of(e));
        when(categorias.listarPorEntrenador(ENTRENADOR)).thenReturn(List.of(
                new Categoria(SEMILLERO, "Semillero", 9, 10, ENTRENADOR),
                new Categoria(SUB12, "Sub-12", 12, 12, ENTRENADOR)));

        yo().andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("entrenador"))
                .andExpect(jsonPath("$.nombre").value("Luis"))
                .andExpect(jsonPath("$.entrenador.idEntrenador").value(ENTRENADOR.toString()))
                .andExpect(jsonPath("$.entrenador.categorias.length()").value(2))
                .andExpect(jsonPath("$.entrenador.categorias[0].idCategoria").value(SEMILLERO.toString()))
                .andExpect(jsonPath("$.entrenador.categorias[0].nombre").value("Semillero"))
                .andExpect(jsonPath("$.entrenador.categorias[1].nombre").value("Sub-12"));
    }

    @Test
    void elEntrenadorSinCategoriasVeElBloqueConListaVacia() throws Exception {
        conRol("ENTRENADOR");
        Entrenador e = new Entrenador();
        e.setId(ENTRENADOR);
        e.setActivo(false);
        when(entrenadores.buscarPorIdUsuario(USUARIO)).thenReturn(Optional.of(e));
        when(categorias.listarPorEntrenador(ENTRENADOR)).thenReturn(List.of());

        yo().andExpect(status().isOk())
                .andExpect(jsonPath("$.entrenador.idEntrenador").value(ENTRENADOR.toString()))
                .andExpect(jsonPath("$.entrenador.categorias").isEmpty());
    }

    @Test
    void elEntrenadorSinFilaVinculadaVeEntrenadorNull() throws Exception {
        conRol("ENTRENADOR");
        when(entrenadores.buscarPorIdUsuario(USUARIO)).thenReturn(Optional.empty());

        yo().andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("entrenador"))
                .andExpect(jsonPath("$.entrenador").hasJsonPath())
                .andExpect(jsonPath("$.entrenador").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void losDemasRolesVenEntrenadorNullSiempreEnElJson() throws Exception {
        for (String rol : List.of("ADMINISTRADOR", "TESORERO", "ACUDIENTE")) {
            conRol(rol);
            yo().andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value(rol.toLowerCase()))
                    .andExpect(jsonPath("$.correo").value("luis@pradofc.co"))
                    .andExpect(jsonPath("$.entrenador").hasJsonPath())
                    .andExpect(jsonPath("$.entrenador").value(org.hamcrest.Matchers.nullValue()));
        }
    }
}
