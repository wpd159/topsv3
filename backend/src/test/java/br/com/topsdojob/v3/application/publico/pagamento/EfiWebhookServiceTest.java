package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(processor.processar(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(EfiWebhookItemProcessor.Resultado.PROCESSADO);
        String payload = """
                {"pix":[{
                  "endToEndId":"E12345678901234567890",
                  "txid":"abcdef1234567890abcdef1234567890",
                  "valor":"5.00",
                  "horario":"2026-07-28T20:00:00Z"
                }]}
                """;

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
        when(processor.processar(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(EfiWebhookItemProcessor.Resultado.REPETIDO);
        String payload = """
                {"pix":[{
                  "endToEndId":"E12345678901234567891",
                  "txid":"abcdef1234567890abcdef1234567891",
                  "valor":"5.00",
                  "horario":"2026-07-28T20:00:00Z"
                }]}
                """;

        var resultado = service.receber("segredo-sintetico", payload, "127.0.0.1", "req-webhook");

        assertThat(resultado.repetidos()).isEqualTo(1);
        assertThat(resultado.processados()).isZero();
    }

    @Test
    void segredoAusenteRetorna403SemPersistencia() {
        String payload = "{\"pix\":[]}";

        assertThatThrownBy(() -> service.receber(null, payload, "127.0.0.1", "req-webhook"))
                .isInstanceOf(EfiWebhookAutenticacaoException.class)
                .hasMessage("N\u00e3o foi poss\u00edvel validar a notifica\u00e7\u00e3o.");
        verifyNoInteractions(processor);
    }

    @Test
    void segredoInvalidoRetorna403SemPersistencia() {
        String payload = "{\"pix\":[]}";

        assertThatThrownBy(() -> service.receber("invalido", payload, "127.0.0.1", "req-webhook"))
                .isInstanceOf(EfiWebhookAutenticacaoException.class)
                .hasMessage("N\u00e3o foi poss\u00edvel validar a notifica\u00e7\u00e3o.");
        verifyNoInteractions(processor);
    }

    @Test
    void valorOuHorarioInvalidosSaoRecusadosAntesDoProcessamento() {
        String valorInvalido = """
                {"pix":[{
                  "endToEndId":"E12345678901234567892",
                  "txid":"abcdef1234567890abcdef1234567892",
                  "valor":"0.001",
                  "horario":"2026-07-28T20:00:00Z"
                }]}
                """;
        String horarioInvalido = """
                {"pix":[{
                  "endToEndId":"E12345678901234567893",
                  "txid":"abcdef1234567890abcdef1234567893",
                  "valor":"5.00",
                  "horario":"nao-e-data"
                }]}
                """;

        assertThatThrownBy(() -> service.receber(
                "segredo-sintetico",
                valorInvalido,
                "127.0.0.1",
                "req-webhook"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> service.receber(
                "segredo-sintetico",
                horarioInvalido,
                "127.0.0.1",
                "req-webhook"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        verify(processor, org.mockito.Mockito.never()).processar(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString());
    }

    private EfiPixProperties properties() {
        EfiPixProperties result = new EfiPixProperties();
        result.setEnabled(true);
        result.setWebhookVerifier("segredo-sintetico");
        return result;
    }
}
