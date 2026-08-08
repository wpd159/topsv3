package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.repository.PagamentoWebhookRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ValidacaoWebhook;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EfiWebhookRegistroServiceTest {

    private final PagamentoWebhookRepository repository = mock(PagamentoWebhookRepository.class);
    private final EfiWebhookRegistroService service = new EfiWebhookRegistroService(repository);

    @Test
    void persisteNotificacaoAntesDoProcessamento() {
        when(repository.findByProvedorAndEventoId(ProvedorPagamento.EFI, "evento-1"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EfiWebhookRegistroService.Registro registro = service.registrar(
                "evento-1",
                "a".repeat(32),
                "payload-hash",
                "ip-hash",
                ValidacaoWebhook.VALIDO);

        assertThat(registro.webhookId()).isNotNull();
        assertThat(registro.processado()).isFalse();
        verify(repository).saveAndFlush(any(PagamentoWebhookEntity.class));
    }

    @Test
    void repeticaoProcessadaEhIdempotente() {
        PagamentoWebhookEntity existente = webhook("evento-2", "b".repeat(32));
        existente.concluir("PROCESSADO", null, OffsetDateTime.now(ZoneOffset.UTC));
        when(repository.findByProvedorAndEventoId(ProvedorPagamento.EFI, "evento-2"))
                .thenReturn(Optional.of(existente));

        EfiWebhookRegistroService.Registro registro = service.registrar(
                "evento-2",
                "b".repeat(32),
                "outro-payload-hash",
                "ip-hash",
                ValidacaoWebhook.VALIDO);

        assertThat(registro.processado()).isTrue();
        assertThat(existente.getTentativas()).isEqualTo(2);
        verify(repository).saveAndFlush(existente);
    }

    @Test
    void eventoExistenteNaoPodeApontarParaOutroPagamento() {
        PagamentoWebhookEntity existente = webhook("evento-3", "c".repeat(32));
        when(repository.findByProvedorAndEventoId(ProvedorPagamento.EFI, "evento-3"))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.registrar(
                "evento-3",
                "d".repeat(32),
                "payload-hash",
                "ip-hash",
                ValidacaoWebhook.VALIDO))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void conclusaoUsaLockPessimista() {
        PagamentoWebhookEntity existente = webhook("evento-4", "e".repeat(32));
        when(repository.findByIdForUpdate(existente.getId())).thenReturn(Optional.of(existente));

        service.concluir(existente.getId(), "ERRO", "PROVEDOR_INDISPONIVEL");

        assertThat(existente.getResultado()).isEqualTo("ERRO");
        assertThat(existente.getErroResumido()).isEqualTo("PROVEDOR_INDISPONIVEL");
        verify(repository).saveAndFlush(existente);
    }

    @Test
    void erroConcorrenteNaoRebaixaWebhookProcessado() {
        PagamentoWebhookEntity existente = webhook("evento-5", "f".repeat(32));
        existente.concluir("PROCESSADO", null, OffsetDateTime.now(ZoneOffset.UTC));
        when(repository.findByIdForUpdate(existente.getId())).thenReturn(Optional.of(existente));

        service.concluir(existente.getId(), "ERRO", "PROVEDOR_INDISPONIVEL");

        assertThat(existente.getResultado()).isEqualTo("PROCESSADO");
        assertThat(existente.getErroResumido()).isNull();
        verify(repository, never()).saveAndFlush(existente);
    }

    private PagamentoWebhookEntity webhook(String eventoId, String txid) {
        return PagamentoWebhookEntity.receber(
                UUID.randomUUID(),
                eventoId,
                txid,
                "payload-hash",
                "ip-hash",
                ValidacaoWebhook.VALIDO,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
