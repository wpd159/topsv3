package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ValidacaoWebhook;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EfiWebhookItemProcessorTest {

    private final EfiWebhookRegistroService registroService = mock(EfiWebhookRegistroService.class);
    private final EfiPagamentoConciliacaoService conciliacaoService =
            mock(EfiPagamentoConciliacaoService.class);
    private final EfiWebhookItemProcessor processor =
            new EfiWebhookItemProcessor(registroService, conciliacaoService);

    @Test
    void conflitoFisicoConcorrenteEhRecarregadoSemErro500() {
        UUID webhookId = UUID.randomUUID();
        when(registroService.registrar(
                "evento-concorrente",
                "a".repeat(32),
                "payload-hash",
                "ip-hash",
                ValidacaoWebhook.VALIDO))
                .thenThrow(new DataIntegrityViolationException("unicidade concorrente"))
                .thenReturn(new EfiWebhookRegistroService.Registro(webhookId, false));
        when(conciliacaoService.conciliarWebhook(
                "a".repeat(32),
                "evento-concorrente",
                "payload-hash",
                "request-test-01"))
                .thenReturn(mock(EfiPagamentoConciliacaoService.ConciliacaoResultado.class));

        EfiWebhookItemProcessor.Resultado resultado = processor.processar(
                "evento-concorrente",
                "a".repeat(32),
                "payload-hash",
                "ip-hash",
                "request-test-01");

        assertThat(resultado).isEqualTo(EfiWebhookItemProcessor.Resultado.PROCESSADO);
        verify(registroService, times(2)).registrar(
                "evento-concorrente",
                "a".repeat(32),
                "payload-hash",
                "ip-hash",
                ValidacaoWebhook.VALIDO);
        verify(conciliacaoService).conciliarWebhook(
                "a".repeat(32),
                "evento-concorrente",
                "payload-hash",
                "request-test-01");
        verify(registroService).concluir(webhookId, "PROCESSADO", null);
    }

    @Test
    void eventoJaProcessadoNaoConsultaProviderNovamente() {
        UUID webhookId = UUID.randomUUID();
        when(registroService.registrar(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new EfiWebhookRegistroService.Registro(webhookId, true));

        EfiWebhookItemProcessor.Resultado resultado = processor.processar(
                "evento-repetido",
                "b".repeat(32),
                "payload-hash",
                "ip-hash",
                "request-test-02");

        assertThat(resultado).isEqualTo(EfiWebhookItemProcessor.Resultado.REPETIDO);
        verify(conciliacaoService, never()).conciliarWebhook(
                anyString(), anyString(), anyString(), anyString());
        verify(registroService, never()).concluir(any(), anyString(), any());
    }

    @Test
    void providerIndisponivelMantemWebhookEmErroRetentavel() {
        UUID webhookId = UUID.randomUUID();
        when(registroService.registrar(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new EfiWebhookRegistroService.Registro(webhookId, false));
        when(conciliacaoService.conciliarWebhook(
                anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new EfiPixGatewayException("falha transitoria", false));

        EfiWebhookItemProcessor.Resultado resultado = processor.processar(
                "evento-erro",
                "c".repeat(32),
                "payload-hash",
                "ip-hash",
                "request-test-03");

        assertThat(resultado).isEqualTo(EfiWebhookItemProcessor.Resultado.FALHA);
        verify(registroService).concluir(webhookId, "ERRO", "PROVEDOR_INDISPONIVEL");
    }

    @Test
    void txidDesconhecidoEhPersistidoComoIgnorado() {
        UUID webhookId = UUID.randomUUID();
        when(registroService.registrar(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new EfiWebhookRegistroService.Registro(webhookId, false));
        when(conciliacaoService.conciliarWebhook(
                anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento nao encontrado"));

        EfiWebhookItemProcessor.Resultado resultado = processor.processar(
                "evento-desconhecido",
                "d".repeat(32),
                "payload-hash",
                "ip-hash",
                "request-test-04");

        assertThat(resultado).isEqualTo(EfiWebhookItemProcessor.Resultado.IGNORADO);
        verify(registroService).concluir(webhookId, "IGNORADO", "TXID_DESCONHECIDO");
    }
    @Test
    void ambienteDivergenteMantemWebhookRetentavel() {
        UUID webhookId = UUID.randomUUID();
        when(registroService.registrar(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new EfiWebhookRegistroService.Registro(webhookId, false));
        when(conciliacaoService.conciliarWebhook(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new EfiPagamentoConciliacaoService.ConciliacaoResultado(
                        null, null, false, "AMBIENTE_DIVERGENTE"));

        EfiWebhookItemProcessor.Resultado resultado = processor.processar(
                "evento-ambiente-divergente",
                "e".repeat(32),
                "payload-hash",
                "ip-hash",
                "request-test-05");

        assertThat(resultado).isEqualTo(EfiWebhookItemProcessor.Resultado.FALHA);
        verify(registroService).concluir(webhookId, "ERRO", "AMBIENTE_DIVERGENTE");
    }

}
