package br.com.topsdojob.v3.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.creditos.AdminPlanoCreditoException;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoValidationException;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoValidationException.Campo;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoValidationException.Regra;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioCicloVidaService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhasMidiasService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.publico.pagamento.PagamentoPixException;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.web.publico.anunciante.MeusAnunciosController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    @Test
    void edicaoConhecidaExpoeSomenteCampoRegraMensagemControladaERequestId() throws Exception {
        String response = editorComErro("edicao-conhecida", new AnuncioAtualizacaoValidationException(
                Campo.DESCRICAO, Regra.TAMANHO_INVALIDO, "CPF-SEGREDO payload privado"))
                .perform(patch("/api/public/minha-conta/anuncios/edicao-conhecida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .requestAttr(RequestIdContext.ATTRIBUTE_NAME, "request-edit-123456"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("no-store"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.field").value("descricao"))
                .andExpect(jsonPath("$.ruleCode").value("TAMANHO_INVALIDO"))
                .andExpect(jsonPath("$.message").value("A descrição deve ter entre 20 e 500 caracteres."))
                .andExpect(jsonPath("$.requestId").value("request-edit-123456"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("CPF-SEGREDO", "payload privado");
    }

    @Test
    void edicaoDesconhecidaPermaneceGenerica() throws Exception {
        editorComErro("edicao-desconhecida",
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF-SEGREDO payload privado"))
                .perform(patch("/api/public/minha-conta/anuncios/edicao-desconhecida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .requestAttr(RequestIdContext.ATTRIBUTE_NAME, "request-edit-unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Requisição inválida."))
                .andExpect(jsonPath("$.field").doesNotExist())
                .andExpect(jsonPath("$.ruleCode").doesNotExist())
                .andExpect(jsonPath("$.requestId").value("request-edit-unknown"));
    }

    @Test
    void validacaoTipadaForaDoPatchPublicoNaoExpoeCampoOuRegra() {
        AnuncioAtualizacaoValidationException exception = new AnuncioAtualizacaoValidationException(
                Campo.DESCRICAO, Regra.TAMANHO_INVALIDO, "motivo legado seguro");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var outraRota = handler.handleAnuncioAtualizacaoValidation(exception,
                new MockHttpServletRequest("PATCH", "/api/public/outra-rota"));
        assertThat(outraRota.getBody()).isInstanceOf(ApiErrorResponse.class);
        assertThat(((ApiErrorResponse) outraRota.getBody()).message()).isEqualTo("Requisição inválida.");

        var admin = handler.handleAnuncioAtualizacaoValidation(exception,
                new MockHttpServletRequest("PUT", "/api/admin/anuncios/1"));
        assertThat(admin.getBody()).isInstanceOf(ApiErrorResponse.class);
        assertThat(((ApiErrorResponse) admin.getBody()).message()).isEqualTo("motivo legado seguro");
    }

    private MockMvc editorComErro(String slug, RuntimeException exception) {
        MeuAnuncioAtualizacaoService service = mock(MeuAnuncioAtualizacaoService.class);
        when(service.atualizar(eq(slug), any(MeuAnuncioAtualizacaoRequestDto.class),
                nullable(Authentication.class))).thenThrow(exception);
        return MockMvcBuilders.standaloneSetup(new MeusAnunciosController(
                        mock(MeusAnunciosConsultaService.class),
                        service,
                        mock(MeuAnuncioCicloVidaService.class),
                        mock(MinhasMidiasService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

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
    void conflitoAdministrativoPreservaMotivoSeguro() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/admin/anuncios/1/aprovar");
        String mensagem = "A documentação KYC ainda não foi aprovada.";

        var response = new GlobalExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, mensagem),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo(mensagem);
    }

    @Test
    void conflitoPublicoNaoExpoeMotivoInterno() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/public/minha-conta/anuncios/1");

        var response = new GlobalExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "detalhe interno"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Conflito de estado.");
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
    void erroPixSerializaSomenteCodigoEMensagemNeutros() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/public/minha-conta/pagamentos/1/cancelar");

        var response = new GlobalExceptionHandler().handlePagamentoPix(
                new PagamentoPixException(ApiErrorCode.PIX_CANCELAMENTO_INDISPONIVEL),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo(ApiErrorCode.PIX_CANCELAMENTO_INDISPONIVEL);
        assertThat(response.getBody().message())
                .isEqualTo("Não foi possível cancelar a cobrança agora. Tente novamente.")
                .doesNotContainIgnoringCase("gateway")
                .doesNotContainIgnoringCase("oauth")
                .doesNotContainIgnoringCase("certificado");
    }

    @Test
    void excecaoTecnicaInesperadaNaoSerializaDetalheExternoOuStack() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/public/minha-conta/pagamentos/1/cancelar");

        var response = new GlobalExceptionHandler().handleUnexpected(
                new EfiPixGatewayException(
                        "host externo oauth tls certificado e corpo privado",
                        false,
                        503),
                request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message())
                .isEqualTo("Não foi possível concluir a operação. Tente novamente.")
                .doesNotContainIgnoringCase("oauth")
                .doesNotContainIgnoringCase("tls")
                .doesNotContainIgnoringCase("certificado");
        assertThat(response.getBody().toString()).doesNotContain("stack");
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

    @Test
    void conflitoOtimistaDoAnuncioRetorna409ComRequestIdENoStore() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH",
                "/api/public/minha-conta/anuncios/anuncio-qa");
        request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, "request-ad-conflict-01");

        var response = new GlobalExceptionHandler().handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("AnuncioEntity", "anuncio-qa"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.CONFLICT);
        assertThat(response.getBody().message())
                .isEqualTo("O anúncio foi alterado por outra operação. Atualize os dados e tente novamente.");
        assertThat(response.getBody().requestId()).isEqualTo("request-ad-conflict-01");
    }
}
