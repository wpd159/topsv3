package br.com.topsdojob.v3.infrastructure.payment.efi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EfiPixHttpGatewayConfigurationTest {

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
}
