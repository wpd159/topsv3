package br.com.topsdojob.v3.infrastructure.payment.efi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.platform.health.HealthController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;

class EfiPixHttpGatewayConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void contextoSpringSelecionaConstrutorDeRuntimeComEfiHabilitada() throws Exception {
        EfiPixProperties properties = configuracaoValida();

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "efi-runtime-test",
                            Map.of("app.env", "homologacao", "efi.pix.enabled", "true")));
            context.registerBean(EfiPixProperties.class, () -> properties);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(EfiPixHttpGateway.class);
            context.register(HealthController.class);

            context.refresh();

            assertThat(context.getBean(EfiPixGateway.class))
                    .isInstanceOf(EfiPixHttpGateway.class);
            var health = context.getBean(HealthController.class)
                    .health(new MockHttpServletRequest("GET", "/api/health"));
            assertThat(health.status()).isEqualTo("UP");
        }
    }

    @Test
    void impedeEndpointDeProducaoForaDoAmbienteDeProducao() {
        EfiPixProperties properties = new EfiPixProperties();
        properties.setEnabled(true);
        properties.setEnvironment("producao");
        properties.setBaseUrl("https://pix.api.efipay.com.br");

        assertThatThrownBy(() -> new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao"))
                .isInstanceOf(EfiPixGatewayException.class)
                .hasMessageContaining("configuracao Efi incompleta ou insegura");
    }

    @Test
    void naoAceitaFallbackParaEndpointArbitrario() {
        EfiPixProperties properties = new EfiPixProperties();
        properties.setEnabled(true);
        properties.setEnvironment("homologacao");
        properties.setBaseUrl("https://exemplo.invalid");

        assertThatThrownBy(() -> new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao"))
                .isInstanceOf(EfiPixGatewayException.class)
                .hasMessageContaining("configuracao Efi incompleta ou insegura");
    }

    @Test
    void aceitaPkcs12SemSenhaEConstroiCallbackSanitizado() throws Exception {
        EfiPixProperties properties = configuracaoValida();
        properties.setWebhookVerifier("segredo-webhook-12345678901234567890");

        EfiPixHttpGateway gateway = new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao");
        assertThat(gateway.ambiente()).isEqualTo(AmbientePagamento.SANDBOX);

        assertThat(gateway.webhookCallbackUrl())
                .isEqualTo(
                        "https://v3.esle.cloud/api/public/webhooks/efi/pix"
                                + "?hmac=segredo-webhook-12345678901234567890&ignorar=");
        assertThat(gateway.operacaoSegura("/v2/webhook/chave-pix-homologacao"))
                .isEqualTo("/v2/webhook/{chave}")
                .doesNotContain("chave-pix-homologacao");
    }

    @Test
    void rejeitaWebhookForaDoContratoCanonico() throws Exception {
        EfiPixProperties properties = configuracaoValida();
        properties.setWebhookRegistrationEnabled(true);
        properties.setWebhookBaseUrl("https://v3.esle.cloud/api/public/outro-webhook");

        assertThatThrownBy(() -> new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao"))
                .isInstanceOf(EfiPixGatewayException.class)
                .hasMessageContaining("configuracao Efi incompleta ou insegura");
    }

    @Test
    void permiteCheckoutSandboxSemConfigurarWebhook() throws Exception {
        EfiPixProperties properties = configuracaoValida();
        properties.setWebhookBaseUrl("");
        properties.setWebhookVerifier("");
        properties.setWebhookRegistrationEnabled(false);

        EfiPixHttpGateway gateway = new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao");

        assertThat(gateway.ambiente()).isEqualTo(AmbientePagamento.SANDBOX);
    }

    @Test
    void exigeWebhookCompletoQuandoRegistroAutomaticoEstaHabilitado() throws Exception {
        EfiPixProperties properties = configuracaoValida();
        properties.setWebhookBaseUrl("");
        properties.setWebhookVerifier("");
        properties.setWebhookRegistrationEnabled(true);

        assertThatThrownBy(() -> new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao"))
                .isInstanceOf(EfiPixGatewayException.class)
                .hasMessageContaining("configuracao Efi incompleta ou insegura");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cancelamentoUsaPatchSemBuscarQrCode() throws Exception {
        EfiPixProperties properties = configuracaoValida();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        HttpResponse<String> cancelResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("""
                {"access_token":"EXEMPLO_NAO_REAL","expires_in":300}
                """.trim());
        when(cancelResponse.statusCode()).thenReturn(200);
        when(cancelResponse.body()).thenReturn("""
                {
                  "txid":"a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
                  "status":"REMOVIDA_PELO_USUARIO_RECEBEDOR",
                  "valor":{"original":"9.90"}
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse, cancelResponse);
        EfiPixHttpGateway gateway =
                new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao", httpClient);

        var resultado = gateway.cancelarCobranca("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6");

        assertThat(resultado.status()).isEqualTo("REMOVIDA_PELO_USUARIO_RECEBEDOR");
        assertThat(resultado.pixCopiaECola()).isNull();
        assertThat(resultado.imagemQrCode()).isNull();
        assertThat(gateway.payloadCancelamento())
                .isEqualTo("""
                        {"status":"REMOVIDA_PELO_USUARIO_RECEBEDOR"}
                        """.trim());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2))
                .send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest cancelRequest = requestCaptor.getAllValues().get(1);
        assertThat(cancelRequest.method()).isEqualTo("PATCH");
        assertThat(cancelRequest.uri().getPath())
                .isEqualTo("/v2/cob/a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6");
        assertThat(cancelRequest.headers().firstValue("Content-Type"))
                .hasValue("application/json");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preservaSomenteCodigoSanitizadoDaRejeicao() throws Exception {
        EfiPixProperties properties = configuracaoValida();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        HttpResponse<String> cancelResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("""
                {"access_token":"EXEMPLO_NAO_REAL","expires_in":300}
                """.trim());
        when(cancelResponse.statusCode()).thenReturn(400);
        when(cancelResponse.body()).thenReturn("""
                {
                  "nome":"status_cobranca_invalido",
                  "mensagem":"detalhe externo que nao deve ser propagado"
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse, cancelResponse);
        EfiPixHttpGateway gateway =
                new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao", httpClient);

        assertThatThrownBy(() -> gateway.cancelarCobranca(
                "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"))
                .isInstanceOfSatisfying(
                        EfiPixGatewayException.class,
                        exception -> {
                            assertThat(exception.getHttpStatus()).isEqualTo(400);
                            assertThat(exception.getProviderCode())
                                    .isEqualTo("status_cobranca_invalido");
                            assertThat(exception.isStatusCobrancaInvalido()).isTrue();
                            assertThat(exception.getMessage())
                                    .doesNotContain("detalhe externo");
                        });
    }

    private EfiPixProperties configuracaoValida() throws Exception {
        Path materialPath = tempDir.resolve("efi-homologacao.fixture");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, new char[0]);
        try (OutputStream output = Files.newOutputStream(materialPath)) {
            keyStore.store(output, new char[0]);
        }
        EfiPixProperties properties = new EfiPixProperties();
        properties.setEnabled(true);
        properties.setEnvironment("homologacao");
        properties.setBaseUrl("https://pix-h.api.efipay.com.br");
        properties.setClientId("cliente-homologacao");
        properties.setClientSecret("segredo-homologacao");
        properties.setCertificatePath(materialPath.toString());
        properties.setCertificateProtection("");
        properties.setPixKey("chave-pix-homologacao");
        properties.setWebhookBaseUrl("https://v3.esle.cloud/api/public/webhooks/efi");
        properties.setWebhookVerifier("segredo-webhook-12345678901234567890");
        return properties;
    }
}
