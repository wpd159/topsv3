package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ValidacaoWebhook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiWebhookItemProcessor {

    private final EfiWebhookRegistroService registroService;
    private final EfiPagamentoConciliacaoService conciliacaoService;

    public EfiWebhookItemProcessor(
            EfiWebhookRegistroService registroService,
            EfiPagamentoConciliacaoService conciliacaoService) {
        this.registroService = registroService;
        this.conciliacaoService = conciliacaoService;
    }


    public Resultado processar(
            String eventoId,
            String txid,
            String payloadHash,
            String origemIpHash,
            String requestId) {
        EfiWebhookRegistroService.Registro registro;
        try {
            registro = registrarComConflitoControlado(
                    eventoId,
                    txid,
                    payloadHash,
                    origemIpHash,
                    ValidacaoWebhook.VALIDO);
        } catch (ResponseStatusException exception) {
            return Resultado.FALHA;
        }
        if (registro.processado()) {
            return Resultado.REPETIDO;
        }
        try {
            EfiPagamentoConciliacaoService.ConciliacaoResultado conciliacao =
                    conciliacaoService.conciliarWebhook(
                    txid,
                    eventoId,
                    payloadHash,
                    requestId);
            if (conciliacao.erroResumido() != null) {
                registroService.concluir(registro.webhookId(), "ERRO", conciliacao.erroResumido());
                return Resultado.FALHA;
            }
            registroService.concluir(registro.webhookId(), "PROCESSADO", null);
            return Resultado.PROCESSADO;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404) {
                registroService.concluir(registro.webhookId(), "IGNORADO", "TXID_DESCONHECIDO");
                return Resultado.IGNORADO;
            }
            registroService.concluir(registro.webhookId(), "ERRO", "CONCILIACAO_REJEITADA");
            return Resultado.FALHA;
        } catch (EfiPixGatewayException exception) {
            registroService.concluir(registro.webhookId(), "ERRO", "PROVEDOR_INDISPONIVEL");
            return Resultado.FALHA;
        }
    }

    private EfiWebhookRegistroService.Registro registrarComConflitoControlado(
            String eventoId,
            String txid,
            String payloadHash,
            String origemIpHash,
            ValidacaoWebhook validacao) {
        try {
            return registroService.registrar(eventoId, txid, payloadHash, origemIpHash, validacao);
        } catch (DataIntegrityViolationException exception) {
            return registroService.registrar(eventoId, txid, payloadHash, origemIpHash, validacao);
        }
    }

    public enum Resultado {
        PROCESSADO,
        REPETIDO,
        IGNORADO,
        FALHA
    }
}
