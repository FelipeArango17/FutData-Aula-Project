package co.edu.futdata.util.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IpClienteTest {

    @Test
    void usaLaIpDeCloudflareSiEsValida() {
        MockHttpServletRequest p = new MockHttpServletRequest();
        p.setRemoteAddr("10.21.157.68");
        p.addHeader("CF-Connecting-IP", " 190.24.137.58 ");
        assertThat(IpCliente.de(p)).isEqualTo("190.24.137.58");

        p = new MockHttpServletRequest();
        p.addHeader("CF-Connecting-IP", "2800:e2:2780:9c4::1");
        assertThat(IpCliente.de(p)).isEqualTo("2800:e2:2780:9c4::1");
    }

    @Test
    void ignoraXForwardedForQueElClientePuedeFalsificar() {
        MockHttpServletRequest p = new MockHttpServletRequest();
        p.setRemoteAddr("10.21.157.68");
        p.addHeader("X-Forwarded-For", "1.2.3.4, 104.22.17.40");
        assertThat(IpCliente.de(p)).isEqualTo("10.21.157.68");
    }

    @Test
    void unValorDeCloudflareQueNoEsIpSeIgnora() {
        MockHttpServletRequest p = new MockHttpServletRequest();
        p.setRemoteAddr("127.0.0.1");
        p.addHeader("CF-Connecting-IP", "<script>");
        assertThat(IpCliente.de(p)).isEqualTo("127.0.0.1");
    }

    @Test
    void enmascaraParaLosLogs() {
        assertThat(IpCliente.enmascarada("190.24.137.58")).isEqualTo("190.24.x.x");
        assertThat(IpCliente.enmascarada("2800:e2:2780:9c4::1")).isEqualTo("2800:e2:x");
        assertThat(IpCliente.enmascarada(null)).isEqualTo("(sin IP)");
        assertThat(IpCliente.enmascarada("raro")).isEqualTo("x");
    }
}
