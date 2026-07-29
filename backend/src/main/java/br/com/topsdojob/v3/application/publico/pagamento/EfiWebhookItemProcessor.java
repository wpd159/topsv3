package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.repository.PagamentoWebhookRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ValidacaoWebhook;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiWebhookItemProcessor {

    private final PagamentoWebhookRepository webhookRepository;
    private final EfiPagamentoConciliacaoService conciliacaoService;

    public EfiWebhookItemProcessor(
            PagamentoWebhookRepository webhookRepository,
            EfiPagamentoConciliacaoService conciliacaoService) {
        this.webhookRepository = webhookRepository;
        this.conciliacaoService = conciliacaoService;
    }

    @Transactional
    public Resultado registrarInvalido(String eventoId, String payloadHash, String origemIpHash) {
        var existente = webhookRepository.findByProvedorAndEventoId(ProvedorPagamento.EFI, eventoId);
        PagamentoWebhookEntity webhook = existente.orElseGet(() -> PagamentoWebhookEntity.receber(
                        UUID.randomUUID(),
                        eventoId,
                        null,
                        payloadHash,
                        origemIpHash,
                        ValidacaoWebhook.INVALIDO,
                        OffsetDateTime.now(ZoneOffset.UTC)));
        if (existente.isPresent()) {
            webhook.registrarNovaTentativa();
        }
        webhook.concluir("REJEITADO", "ORIGEM_NAO_VALIDADA", OffsetDateTime.now(ZoneOffset.UTC));
        webhookRepository.save(webhook);
        return Resultado.IGNORADO;
    }

    @Transactional
    public Resultado processar(
            String eventoId,
            String txid,
            BigDecimal valorRecebido,
            OffsetDateTime recebidoEm,
            String payloadHash,
            String origemIpHash,
            String requestId) {
        var existente = webhookRepository.findByProvedorAndEventoId(ProvedorPagamento.EFI, eventoId);
        if (existente.isPresent() && "PROCESSADO".equals(existente.get().getResultado())) {
            existente.get().registrarNovaTentativa();
            webhookRepository.save(existente.get());
            return Resultado.REPETIDO;
        }
        PagamentoWebhookEntity webhook = existente.orElseGet(() -> PagamentoWebhookEntity.receber(
                UUID.randomUUID(),
                eventoId,
                txid,
                payloadHash,
                origemIpHash,
                ValidacaoWebhook.VALIDO,
                OffsetDateTime.now(ZoneOffset.UTC)));
        if (existente.isPresent()) {
            webhook.registrarNovaTentativa();
        }
        try {
            conciliacaoService.conciliarWebhook(
                    txid,
                    eventoId,
                    payloadHash,
                    valorRecebido,
                    recebidoEm,
                    requestId);
            webhook.concluir("PROCESSADO", null, OffsetDateTime.now(ZoneOffset.UTC));
            webhookRepository.save(webhook);
            return Resultado.PROCESSADO;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404) {
                webhook.concluir("IGNORADO", "TXID_DESCONHECIDO", OffsetDateTime.now(ZoneOffset.UTC));
                webhookRepository.save(webhook);
                return Resultado.IGNORADO;
            }
            webhook.concluir("ERRO", "CONCILIACAO_REJEITADA", OffsetDateTime.now(ZoneOffset.UTC));
            webhookRepository.save(webhook);
            return Resultado.FALHA;
        } catch (EfiPixGatewayException exception) {
            webhook.concluir("ERRO", "PROVEDOR_INDISPONIVEL", OffsetDateTime.now(ZoneOffset.UTC));
            webhookRepository.save(webhook);
            return Resultado.FALHA;
        }
    }

    public enum Resultado {
        PROCESSADO,
        REPETIDO,
        IGNORADO,
        FALHA
    }
}
