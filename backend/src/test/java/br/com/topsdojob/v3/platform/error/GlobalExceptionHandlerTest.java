package br.com.topsdojob.v3.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    @Test
    void authorizationDeniedRetornaForbiddenSemVirarErroInterno() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/admin/midias/1/revisar");
        AuthorizationDeniedException exception = new AuthorizationDeniedException(
                "acesso negado",
                new AuthorizationDecision(false));

        var response = new GlobalExceptionHandler().handleAccessDenied(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.FORBIDDEN);
    }

    @Test
    void challengeExpiradoPreservaStatusGoneSemVirarErroInterno() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/public/compliance/visitor/verify");

        var response = new GlobalExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.GONE, "challenge expirado"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(410);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.GONE);
        assertThat(response.getBody().message()).isEqualTo(
                "Verificacao expirada. Inicie novamente.");
    }
}
