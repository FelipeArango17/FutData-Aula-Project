package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Las firmas de Wompi. Los hashes esperados están calculados a mano con {@code shasum -a 256} sobre el texto concatenado
 * (no con este código), así que la prueba no repite la implementación.
 */
class WompiFirmasTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRETO = "test_events_secreto";

    // printf '%s' 'FD-sandbox-abc4990000COPtest_integrity_secreto' | shasum -a 256
    @Test
    void firmaDeIntegridadSinExpiracion() {
        assertThat(WompiFirmas.firmaIntegridad("FD-sandbox-abc", 4990000, "COP", "test_integrity_secreto"))
                .isEqualTo("5e6bc69aaf0d3815b0ab2abe7782df69d9d17b4a4ce689cc355832c9ab079ed3");
    }

    @Test
    void firmaDeIntegridadConExpiracion() {
        assertThat(WompiFirmas.firmaIntegridad("FD-sandbox-abc", 4990000, "COP", "2026-12-31T23:59:59.000Z", "test_integrity_secreto"))
                .isEqualTo("baaa6c87511a96bfa6c877a47b602e9f83701eb04d3357526214e60c9d20382d");
    }

    @Test
    void cambiarElMontoCambiaLaFirma() {
        assertThat(WompiFirmas.firmaIntegridad("FD-sandbox-abc", 4990001, "COP", "test_integrity_secreto"))
                .isNotEqualTo("5e6bc69aaf0d3815b0ab2abe7782df69d9d17b4a4ce689cc355832c9ab079ed3");
    }

    private static JsonNode evento(String propiedades, String checksum, String timestampEnFirma) throws Exception {
        return JSON.readTree("""
                {"event":"transaction.updated",
                 "data":{"transaction":{"id":"1234-1610641025-49201","status":"APPROVED","amount_in_cents":4490000,
                                         "reference":"FD-sandbox-abc","payment_method_type":"CARD"}},
                 "environment":"test",
                 "signature":{"properties":%s,"checksum":"%s"%s},
                 "timestamp":1530291411,"sent_at":"2018-07-20T16:45:05.000Z"}
                """.formatted(propiedades, checksum, timestampEnFirma));
    }

    // printf '%s' '1234-1610641025-49201APPROVED44900001530291411test_events_secreto' | shasum -a 256
    private static final String CHECKSUM = "177a142e25beca66d59f857f08b24ca96fb01b5cb39911a05940ea0cb8144a5d";
    private static final String PROPIEDADES = "[\"transaction.id\",\"transaction.status\",\"transaction.amount_in_cents\"]";

    @Test
    void checksumValido() throws Exception {
        JsonNode e = evento(PROPIEDADES, CHECKSUM, "");
        assertThat(WompiFirmas.checksumDelEvento(e, SECRETO)).isEqualTo(CHECKSUM);
        assertThat(WompiFirmas.eventoValido(e, CHECKSUM, SECRETO)).isTrue();
    }

    @Test
    void elEncabezadoSeCompararSinImportarMayusculas() throws Exception {
        assertThat(WompiFirmas.eventoValido(evento(PROPIEDADES, CHECKSUM, ""), CHECKSUM.toUpperCase(), SECRETO)).isTrue();
    }

    @Test
    void sinEncabezadoOEncabezadoDistintoEsInvalido() throws Exception {
        JsonNode e = evento(PROPIEDADES, CHECKSUM, "");
        assertThat(WompiFirmas.eventoValido(e, null, SECRETO)).isFalse();
        assertThat(WompiFirmas.eventoValido(e, "", SECRETO)).isFalse();
        assertThat(WompiFirmas.eventoValido(e, "0".repeat(64), SECRETO)).isFalse();
    }

    @Test
    void elChecksumDelCuerpoTambienDebeCoincidir() throws Exception {
        assertThat(WompiFirmas.eventoValido(evento(PROPIEDADES, "0".repeat(64), ""), CHECKSUM, SECRETO)).isFalse();
    }

    @Test
    void unEventoAlteradoOUnSecretoDistintoEsInvalido() throws Exception {
        JsonNode alterado = evento(PROPIEDADES, CHECKSUM, "");
        ((com.fasterxml.jackson.databind.node.ObjectNode) alterado.path("data").path("transaction")).put("amount_in_cents", 100);
        assertThat(WompiFirmas.eventoValido(alterado, CHECKSUM, SECRETO)).isFalse();
        assertThat(WompiFirmas.eventoValido(evento(PROPIEDADES, CHECKSUM, ""), CHECKSUM, "test_events_otro")).isFalse();
    }

    // printf '%s' '1234-1610641025-49201FD-sandbox-abc1530291411test_events_secreto' | shasum -a 256
    @Test
    void siempreSeLeeLaListaDePropiedadesDelEvento() throws Exception {
        String otra = "3d5a2e32b10232e4308e61ac7e581c6f576ee6b14317b99fe83fd340dc811a59";
        JsonNode conOtraLista = evento("[\"transaction.id\",\"transaction.reference\"]", otra, "");
        assertThat(WompiFirmas.eventoValido(conOtraLista, otra, SECRETO)).isTrue();
        // la misma firma de la lista de siempre no vale con una lista distinta
        assertThat(WompiFirmas.eventoValido(evento("[\"transaction.id\",\"transaction.reference\"]", CHECKSUM, ""), CHECKSUM, SECRETO)).isFalse();
    }

    @Test
    void sinListaOConUnaPropiedadInexistenteNoSePuedeValidar() throws Exception {
        assertThat(WompiFirmas.checksumDelEvento(evento("[]", CHECKSUM, ""), SECRETO)).isNull();
        assertThat(WompiFirmas.checksumDelEvento(evento("[\"transaction.no_existe\"]", CHECKSUM, ""), SECRETO)).isNull();
        assertThat(WompiFirmas.eventoValido(JSON.readTree("{\"event\":\"x\",\"data\":{}}"), CHECKSUM, SECRETO)).isFalse();
    }

    @Test
    void sinSecretoNuncaEsValido() throws Exception {
        assertThat(WompiFirmas.eventoValido(evento(PROPIEDADES, CHECKSUM, ""), CHECKSUM, "")).isFalse();
        assertThat(WompiFirmas.eventoValido(evento(PROPIEDADES, CHECKSUM, ""), CHECKSUM, null)).isFalse();
    }

    @Test
    void usaSignatureTimestampSiElEventoNoTraeTimestamp() throws Exception {
        JsonNode e = JSON.readTree("""
                {"event":"transaction.updated","data":{"transaction":{"id":"1234-1610641025-49201","status":"APPROVED","amount_in_cents":4490000}},
                 "signature":{"properties":%s,"checksum":"%s","timestamp":1530291411}}""".formatted(PROPIEDADES, CHECKSUM));
        assertThat(WompiFirmas.eventoValido(e, CHECKSUM, SECRETO)).isTrue();
    }
}
