package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicClientIpResolverTest {

    private final PublicClientIpResolver resolver = new PublicClientIpResolver();

    @Test
    void ignoraHeadersForjadosQuandoPeerNaoEProxyConfiavel() {
        MockHttpServletRequest request = request("198.51.100.30");
        request.addHeader("X-Forwarded-For", "203.0.113.90");
        request.addHeader("CF-Connecting-IP", "203.0.113.91");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.30");
    }

    @Test
    void escolhePrimeiroSaltoNaoConfiavelDaDireitaEIgnoraSpoofAEsquerda() {
        MockHttpServletRequest request = request("172.18.0.3");
        request.addHeader("X-Forwarded-For", "203.0.113.99, 198.51.100.40, 172.18.0.1");
        request.addHeader("CF-Connecting-IP", "203.0.113.98");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.40");
    }

    @Test
    void aceitaCfConnectingIpSomenteQuandoSaltoExternoECloudflare() {
        MockHttpServletRequest request = request("172.18.0.3");
        request.addHeader("X-Forwarded-For", "198.51.100.50, 173.245.48.10, 172.18.0.1");
        request.addHeader("CF-Connecting-IP", "198.51.100.50");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.50");
    }

    @Test
    void normalizaIpv6SemAceitarHostnameOuPorta() {
        MockHttpServletRequest ipv6 = request("2001:db8:0:0:0:0:0:10");
        assertThat(resolver.resolve(ipv6)).isEqualTo("2001:db8:0:0:0:0:0:10");

        MockHttpServletRequest invalid = request("host.example.invalid");
        invalid.addHeader("X-Forwarded-For", "198.51.100.1:443");
        assertThat(resolver.resolve(invalid)).isEqualTo("unknown");
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
