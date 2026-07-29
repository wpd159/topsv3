package br.com.topsdojob.v3.infrastructure.payment.efi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EfiPixHttpGatewayConfigurationTest {

    @TempDir
    Path tempDir;

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
        properties.setWebhookBaseUrl("https://v3.esle.cloud/api/public/outro-webhook");

        assertThatThrownBy(() -> new EfiPixHttpGateway(properties, new ObjectMapper(), "homologacao"))
                .isInstanceOf(EfiPixGatewayException.class)
                .hasMessageContaining("configuracao Efi incompleta ou insegura");
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
