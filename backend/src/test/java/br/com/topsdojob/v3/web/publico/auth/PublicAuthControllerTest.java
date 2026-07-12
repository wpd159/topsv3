package br.com.topsdojob.v3.web.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.auth.PublicAuthenticationService;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAuthStatusDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicAuthControllerTest {

    @Test
    void logoutExpiraCookieDeSessaoComAtributosSeguros() {
        PublicAuthenticationService service = mock(PublicAuthenticationService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        PublicAuthStatusDto status = new PublicAuthStatusDto(false, "LOGOUT_OK");
        when(service.logout(request)).thenReturn(status);

        var response = new PublicAuthController(service, true).logout(request);

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
}
