package br.com.topsdojob.v3.web.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.auth.PublicAuthenticationService;
import br.com.topsdojob.v3.application.publico.auth.PublicAccountLifecycleService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthSecurityService;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAuthStatusDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicAuthControllerTest {

    @Test
    void logoutExpiraCookieDeSessaoComAtributosSeguros() {
        PublicAuthenticationService service = mock(PublicAuthenticationService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        PublicAuthStatusDto status = new PublicAuthStatusDto(false, "LOGOUT_OK");
        when(service.logout(request)).thenReturn(status);

        var response = controller(service).logout(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("JSESSIONID=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        verify(service).logout(request);
    }

    @Test
    void rateLimitExplicitaRetryAfter() {
        var response = controller(mock(PublicAuthenticationService.class)).handlePublicAuth(
                new PublicAuthException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas.", 37L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("37");
    }

    @Test
    void endpointDeDuplicidadeRetornaMensagemGenericaENoStore() {
        PublicAuthenticationService service = mock(PublicAuthenticationService.class);
        PublicAuthSecurityService security = mock(PublicAuthSecurityService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(service.duplicidade()).thenReturn(new br.com.topsdojob.v3.application.publico.auth.dto.PublicDuplicidadeDto(
                "A disponibilidade sera confirmada no cadastro."));
        PublicAuthController controller = new PublicAuthController(
                service,
                mock(PublicAccountLifecycleService.class),
                security,
                true);

        var response = controller.duplicidade(request);

        assertThat(response.getBody()).extracting("mensagem")
                .isEqualTo("A disponibilidade sera confirmada no cadastro.");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        verify(security).requireDuplicateLookup(request);
    }

    private PublicAuthController controller(PublicAuthenticationService service) {
        return new PublicAuthController(
                service,
                mock(PublicAccountLifecycleService.class),
                mock(PublicAuthSecurityService.class),
                true);
    }
}
