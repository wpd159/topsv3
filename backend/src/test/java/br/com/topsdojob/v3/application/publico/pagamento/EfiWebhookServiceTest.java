package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EfiWebhookServiceTest {

    private final EfiPixProperties properties = properties();
    private final EfiPagamentoHashService hashService = new EfiPagamentoHashService("salt-sintetico");
    private final EfiWebhookItemProcessor processor = mock(EfiWebhookItemProcessor.class);
    private final EfiWebhookService service = new EfiWebhookService(
            properties,
            hashService,
            processor,
            new ObjectMapper());

    @Test
    void processaPayloadValidoSemPersistirPayloadBruto() {
        when(processor.processar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EfiWebhookItemProcessor.Resultado.PROCESSADO);
        String payload = "{\"pix\":[{\"endToEndId\":\"E12345678901234567890\",\"txid\":\"abcdef1234567890abcdef1234567890\"}]}";

        var resultado = service.receber("segredo-sintetico", payload, "127.0.0.1", "req-webhook");

        assertThat(resultado.recebidos()).isEqualTo(1);
        assertThat(resultado.processados()).isEqualTo(1);
        verify(processor).processar(
                "E12345678901234567890",
                "abcdef1234567890abcdef1234567890",
                hashService.hash(payload),
                hashService.hash("127.0.0.1"),
                "req-webhook");
    }

    @Test
    void repeticaoEhExpostaSemNovoProcessamentoFuncional() {
        when(processor.processar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EfiWebhookItemProcessor.Resultado.REPETIDO);
        String payload = "{\"pix\":[{\"endToEndId\":\"E12345678901234567891\",\"txid\":\"abcdef1234567890abcdef1234567891\"}]}";

        var resultado = service.receber("segredo-sintetico", payload, "127.0.0.1", "req-webhook");

        assertThat(resultado.repetidos()).isEqualTo(1);
        assertThat(resultado.processados()).isZero();
    }

    @Test
    void segredoInvalidoRetorna403ERegistraSomenteHashes() {
        String payload = "{\"pix\":[]}";

        assertThatThrownBy(() -> service.receber("invalido", payload, "127.0.0.1", "req-webhook"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        verify(processor).registrarInvalido(
                "invalido-" + hashService.hash(payload).substring(0, 32),
                hashService.hash(payload),
                hashService.hash("127.0.0.1"));
    }

    private EfiPixProperties properties() {
        EfiPixProperties result = new EfiPixProperties();
        result.setEnabled(true);
        result.setWebhookVerifier("segredo-sintetico");
        return result;
    }
}
