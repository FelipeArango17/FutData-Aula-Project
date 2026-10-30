package co.edu.futdata.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import co.edu.futdata.api.dto.DeportistaRequestDTO;
import co.edu.futdata.domain.entities.Deportista;
import co.edu.futdata.persistence.dao.IDeportistaDAO;
import co.edu.futdata.services.facade.ConflictoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeportistaServiceImplTest {

    private static final UUID ID = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b");
    private IDeportistaDAO dao;
    private DeportistaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        dao = mock(IDeportistaDAO.class);
        servicio = new DeportistaServiceImpl(dao,
                Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), ZoneId.of("America/Bogota")));
    }

    private DeportistaRequestDTO datos() {
        return new DeportistaRequestDTO("Ana", "Pérez", "TI", "12345678",
                java.time.LocalDate.of(2012, 4, 10), "3001234567", "Calle 10 # 20-30");
    }

    @Test
    void creaYCalculaEdad() {
        when(dao.existeDocumento("12345678")).thenReturn(false);
        when(dao.crear(any())).thenReturn(ID);
        Deportista d = new Deportista();
        d.setId(ID); d.setNombre("Ana"); d.setApellido("Pérez"); d.setTipoDocumento("TI");
        d.setDocumento("12345678"); d.setFechaNacimiento(java.time.LocalDate.of(2012, 4, 10));
        d.setTelefono("3001234567"); d.setDireccion("Calle 10 # 20-30");
        when(dao.buscarPorId(ID)).thenReturn(java.util.Optional.of(d));

        assertThat(servicio.crear(datos()).edad()).isEqualTo(14);
        verify(dao).crear(any(Deportista.class));
    }

    @Test
    void documentoDuplicadoDevuelve409ConCampo() {
        when(dao.existeDocumento("12345678")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(datos()))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("documento");
        verify(dao, never()).crear(any());
    }
}
