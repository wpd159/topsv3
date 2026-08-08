package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.repository.PagamentoWebhookRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ValidacaoWebhook;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiWebhookRegistroService {

    private final PagamentoWebhookRepository repository;

    public EfiWebhookRegistroService(PagamentoWebhookRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Registro registrar(
            String eventoId,
            String txid,
            String payloadHash,
            String origemIpHash,
            ValidacaoWebhook validacao) {
        var existente = repository.findByProvedorAndEventoId(ProvedorPagamento.EFI, eventoId);
        if (existente.isPresent()) {
            PagamentoWebhookEntity webhook = existente.get();
            if (!Objects.equals(webhook.getTxid(), txid)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "evento de webhook associado a outro pagamento");
            }
            webhook.registrarNovaTentativa();
            repository.saveAndFlush(webhook);
            return new Registro(webhook.getId(), "PROCESSADO".equals(webhook.getResultado()));
        }

        PagamentoWebhookEntity webhook = PagamentoWebhookEntity.receber(
                UUID.randomUUID(),
                eventoId,
                txid,
                payloadHash,
                origemIpHash,
                validacao,
                OffsetDateTime.now(ZoneOffset.UTC));
        repository.saveAndFlush(webhook);
        return new Registro(webhook.getId(), false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluir(UUID webhookId, String resultado, String erroResumido) {
        PagamentoWebhookEntity webhook = repository.findByIdForUpdate(webhookId)
                .orElseThrow(() -> new IllegalStateException("webhook financeiro nao encontrado"));
        if ("PROCESSADO".equals(webhook.getResultado())
                && !"PROCESSADO".equals(resultado)) {
            return;
        }
        webhook.concluir(resultado, erroResumido, OffsetDateTime.now(ZoneOffset.UTC));
        repository.saveAndFlush(webhook);
    }

    public record Registro(UUID webhookId, boolean processado) {
    }
}
