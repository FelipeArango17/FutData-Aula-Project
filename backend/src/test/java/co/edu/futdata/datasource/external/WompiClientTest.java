package co.edu.futdata.datasource.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import co.edu.futdata.config.WompiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** El cliente de Wompi contra un servidor simulado: nunca se llama a Wompi. */
class WompiClientTest {

    private static final WompiProperties WOMPI = new WompiProperties("pub_test_a", "prv_test_LLAVE_SECRETA", "test_events_c",
            "test_integrity_d", WompiProperties.SANDBOX, null);

    @Test
    void consultaPorReferenciaConLaLlavePrivadaYLeeLasTransacciones() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://sandbox.wompi.co/v1/transactions?reference=FD-abc"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer prv_test_LLAVE_SECRETA"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"tx-1","status":"DECLINED","amount_in_cents":9000000,"reference":"FD-abc","payment_method_type":"CARD"},
                                 {"id":"tx-2","status":"APPROVED","amount_in_cents":9000000,"reference":"FD-abc","payment_method_type":"NEQUI"}],
                         "meta":{}}""", MediaType.APPLICATION_JSON));

        List<TransaccionWompi> r = new WompiClient(builder.build(), WOMPI).transaccionesDe("FD-abc");

        assertThat(r).containsExactly(new TransaccionWompi("tx-1", "DECLINED", 9_000_000, "FD-abc", "CARD"),
                new TransaccionWompi("tx-2", "APPROVED", 9_000_000, "FD-abc", "NEQUI"));
        servidor.verify();
    }

    @Test
    void sinTransaccionesDevuelveUnaListaVacia() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo("https://sandbox.wompi.co/v1/transactions?reference=FD-x"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThat(new WompiClient(builder.build(), WOMPI).transaccionesDe("FD-x")).isEmpty();
    }

    @Test
    void unErrorDeWompiSeVuelveWompiNoDisponibleSinLaLlave() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo("https://sandbox.wompi.co/v1/transactions?reference=FD-x"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> new WompiClient(builder.build(), WOMPI).transaccionesDe("FD-x"))
                .isInstanceOf(WompiNoDisponibleException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("LLAVE_SECRETA", "prv_"));
    }
}
