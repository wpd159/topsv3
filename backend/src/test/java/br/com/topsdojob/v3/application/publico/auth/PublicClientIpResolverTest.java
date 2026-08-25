package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicClientIpResolverTest {

    private static final String PRODUCTION_TRUSTED_PROXIES = "172.18.0.0/16,127.0.0.0/8,::1/128";
    private final PublicClientIpResolver resolver = new PublicClientIpResolver(PRODUCTION_TRUSTED_PROXIES);

    @Test
    void ignoraHeadersForjadosQuandoPeerNaoEProxyConfiavel() {
        MockHttpServletRequest request = request("198.51.100.30");
        request.addHeader("X-Forwarded-For", "203.0.113.90");
        request.addHeader("CF-Connecting-IP", "203.0.113.91");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.30");
    }

    @Test
    void escolhePrimeiroSaltoNaoConfiavelDaDireitaEIgnoraSpoofAEsquerda() {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.99, 198.51.100.40, 172.18.0.1");
        request.addHeader("CF-Connecting-IP", "203.0.113.98");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.40");
    }

    @Test
    void aceitaCfConnectingIpSomenteQuandoSaltoExternoECloudflare() {
        MockHttpServletRequest request = request("172.18.0.5");
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

    @Test
    void aceitaXffDoGatewayConfiguradoSemReduzirClientesAoIpDoGateway() {
        MockHttpServletRequest first = request("172.18.0.5");
        first.addHeader("X-Forwarded-For", "198.51.100.60, 172.18.0.1");
        MockHttpServletRequest second = request("172.18.0.5");
        second.addHeader("X-Forwarded-For", "198.51.100.61, 172.18.0.1");

        assertThat(resolver.resolve(first)).isEqualTo("198.51.100.60");
        assertThat(resolver.resolve(second)).isEqualTo("198.51.100.61");
        assertThat(PublicAuthSecurityService.auditReference(resolver.resolve(first)))
                .isNotEqualTo(PublicAuthSecurityService.auditReference(resolver.resolve(second)));
    }

    @Test
    void naoConfiaEmRedePrivadaForaDaAllowlistExplicita() {
        MockHttpServletRequest request = request("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.90");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void falhaFechadoQuandoCadeiaDoGatewayContemSaltoInvalido() {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.40, valor-invalido");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void resolveClienteIpv6PorGatewayIpv6ExplicitamenteConfiavel() {
        PublicClientIpResolver ipv6Resolver = new PublicClientIpResolver("fd00:18::/64,::1/128");
        MockHttpServletRequest request = request("fd00:18::5");
        request.addHeader("X-Forwarded-For", "2001:db8::20, fd00:18::1");

        assertThat(ipv6Resolver.resolve(request)).isEqualTo("2001:db8:0:0:0:0:0:20");
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
