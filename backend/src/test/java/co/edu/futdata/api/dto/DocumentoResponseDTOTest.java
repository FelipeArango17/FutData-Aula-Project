package co.edu.futdata.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.futdata.domain.entities.Documento;
import co.edu.futdata.domain.entities.EstadoDocumento;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentoResponseDTOTest {

    private static Documento documento(String rol, EstadoDocumento estado) {
        return new Documento(UUID.randomUUID(), UUID.randomUUID(), "PAZ_Y_SALVO",
                "11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222.pdf", "paz.pdf",
                "application/pdf", 1234, estado, estado == EstadoDocumento.RECHAZADO ? "Borroso" : null,
                LocalDate.of(2027, 1, 31), rol, "carga@ejemplo.com", Instant.parse("2026-11-05T15:00:00Z"),
                estado == EstadoDocumento.PENDIENTE ? null : "admin@ejemplo.com",
                estado == EstadoDocumento.PENDIENTE ? null : Instant.parse("2026-11-05T16:00:00Z"));
    }

    @Test
    void tieneExactamenteLaFormaQueUsaElFrontend() {
        List<String> campos = Arrays.stream(DocumentoResponseDTO.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertThat(campos).containsExactly("id", "idDeportista", "tipo", "nombreOriginal", "tipoContenido",
                "tamanoBytes", "estado", "comentario", "fechaVencimiento", "cargadoPor", "cargadoPorCorreo",
                "cargadoEn", "revisadoPor", "revisadoEn");
    }

    @Test
    void nuncaExponeLaRutaDelArchivo() {
        assertThat(Arrays.stream(DocumentoResponseDTO.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("ruta", "rolCargadoPor");
    }

    @Test
    void cargadoPorSeDerivaDelRol() {
        assertThat(DocumentoResponseDTO.de(documento("ADMINISTRADOR", EstadoDocumento.PENDIENTE)).cargadoPor())
                .isEqualTo("Administrador");
        assertThat(DocumentoResponseDTO.de(documento("ACUDIENTE", EstadoDocumento.PENDIENTE)).cargadoPor())
                .isEqualTo("Acudiente");
        assertThat(DocumentoResponseDTO.de(documento(null, EstadoDocumento.PENDIENTE)).cargadoPor()).isNull();
    }

    @Test
    void copiaLosDemasCampos() {
        Documento d = documento("ACUDIENTE", EstadoDocumento.RECHAZADO);
        DocumentoResponseDTO dto = DocumentoResponseDTO.de(d);
        assertThat(dto.id()).isEqualTo(d.id());
        assertThat(dto.idDeportista()).isEqualTo(d.idDeportista());
        assertThat(dto.tipo()).isEqualTo("PAZ_Y_SALVO");
        assertThat(dto.nombreOriginal()).isEqualTo("paz.pdf");
        assertThat(dto.tipoContenido()).isEqualTo("application/pdf");
        assertThat(dto.tamanoBytes()).isEqualTo(1234);
        assertThat(dto.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(dto.comentario()).isEqualTo("Borroso");
        assertThat(dto.fechaVencimiento()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(dto.cargadoPorCorreo()).isEqualTo("carga@ejemplo.com");
        assertThat(dto.cargadoEn()).isEqualTo(Instant.parse("2026-11-05T15:00:00Z"));
        assertThat(dto.revisadoPor()).isEqualTo("admin@ejemplo.com");
        assertThat(dto.revisadoEn()).isEqualTo(Instant.parse("2026-11-05T16:00:00Z"));
    }

    @Test
    void unPendienteNoTieneRevisionNiComentario() {
        DocumentoResponseDTO dto = DocumentoResponseDTO.de(documento("ADMINISTRADOR", EstadoDocumento.PENDIENTE));
        assertThat(dto.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(dto.comentario()).isNull();
        assertThat(dto.revisadoPor()).isNull();
        assertThat(dto.revisadoEn()).isNull();
    }
}
