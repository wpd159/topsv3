package br.com.topsdojob.v3.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.admin.creditos.AdminPlanoCreditoException;
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

    @Test
    void midiaStoryIncompativelPreservaMensagemSegura() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/public/minha-conta/stories");
        String mensagem = "Vídeo incompatível. Use MP4 com vídeo H.264 e áudio AAC-LC.";

        var response = new GlobalExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, mensagem),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().message()).isEqualTo(mensagem);
    }

    @Test
    void pacoteCreditoPreservaMensagemAdministrativaSegura() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/admin/creditos/pacotes/1/ativacao");
        String mensagem = "Defina um pre\u00e7o maior que zero antes de ativar este pacote.";

        var response = new GlobalExceptionHandler().handleAdminPlanoCredito(
                new AdminPlanoCreditoException(HttpStatus.BAD_REQUEST, mensagem),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo(mensagem);
    }

    @Test
    void conflitoDePacotePreservaMensagemDeConcorrencia() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/admin/creditos/pacotes/1/ativacao");
        String mensagem = "Este pacote foi alterado por outro administrador. "
                + "Os dados foram atualizados; revise e tente novamente.";

        var response = new GlobalExceptionHandler().handleAdminPlanoCredito(
                new AdminPlanoCreditoException(HttpStatus.CONFLICT, mensagem),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo(mensagem);
    }
}
