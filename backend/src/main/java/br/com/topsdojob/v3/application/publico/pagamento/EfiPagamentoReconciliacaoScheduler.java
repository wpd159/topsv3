package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixProperties;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import br.com.topsdojob.v3.platform.scheduling.BackgroundJobsModeCondition;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Conditional(BackgroundJobsModeCondition.class)
public class EfiPagamentoReconciliacaoScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EfiPagamentoReconciliacaoScheduler.class);
    private static final int LOTE_MAXIMO_SEGURO = 100;
    private static final int CICLO_MAXIMO_SEGURO = 1_000;

    private final EfiPixProperties properties;
    private final PagamentoRepository pagamentoRepository;
    private final EfiPagamentoConciliacaoService conciliacaoService;
    private final EfiPixGateway gateway;

    public EfiPagamentoReconciliacaoScheduler(
            EfiPixProperties properties,
            PagamentoRepository pagamentoRepository,
            EfiPagamentoConciliacaoService conciliacaoService,
            EfiPixGateway gateway) {
        this.properties = properties;
        this.pagamentoRepository = pagamentoRepository;
        this.conciliacaoService = conciliacaoService;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${efi.pix.reconciliation-interval-ms:60000}")
    public void processar() {
        if (!properties.isEnabled() || !properties.isReconciliationEnabled()) {
            return;
        }
        processarCiclo();
    }

    int processarCiclo() {
        if (!properties.isEnabled() || !properties.isReconciliationEnabled()) {
            return 0;
        }
        String requestId = "efi-reconciliacao-" + UUID.randomUUID();
        AmbientePagamento ambiente;
        try {
            ambiente = gateway.ambiente();
        } catch (EfiPixGatewayException exception) {
            LOGGER.warn(
                    "efi_reconciliacao_indisponivel requestId={} tipo={}",
                    requestId,
                    exception.getClass().getSimpleName());
            return 0;
        }
        if (ambiente == null) {
            return 0;
        }

        int lote = Math.max(1, Math.min(properties.getReconciliationBatchSize(), LOTE_MAXIMO_SEGURO));
        int maximo = Math.max(1, Math.min(properties.getReconciliationMaxPerCycle(), CICLO_MAXIMO_SEGURO));
        long backoff = Math.max(1L, properties.getReconciliationRetryBackoffSeconds());
        OffsetDateTime elegivelAntesDe = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(backoff);
        Set<String> processados = new HashSet<>();

        while (processados.size() < maximo) {
            int limite = Math.min(lote, maximo - processados.size());
            List<String> txids = pagamentoRepository.findTxidsConciliaveisEfi(
                    ProvedorPagamento.EFI,
                    MetodoPagamento.PIX,
                    ambiente,
                    List.of(StatusInternoPagamento.CRIADO, StatusInternoPagamento.AGUARDANDO_PAGAMENTO),
                    StatusInternoPagamento.ERRO,
                    EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO,
                    elegivelAntesDe,
                    PageRequest.of(0, limite));
            List<String> novos = txids.stream().filter(processados::add).toList();
            if (novos.isEmpty()) {
                break;
            }
            for (String txid : novos) {
                try {
                    conciliacaoService.conciliar(
                            txid,
                            null,
                            null,
                            OrigemConciliacaoPagamento.CONSULTA_PROVEDOR,
                            requestId);
                } catch (EfiPixGatewayException exception) {
                    LOGGER.warn(
                            "efi_reconciliacao_falhou pagamento={} requestId={} tipo={}",
                            referenciaSegura(txid),
                            requestId,
                            exception.getClass().getSimpleName());
                    if (exception.isConfiguracao()) {
                        return processados.size();
                    }
                } catch (RuntimeException exception) {
                    LOGGER.error(
                            "efi_reconciliacao_falhou pagamento={} requestId={} tipo={}",
                            referenciaSegura(txid),
                            requestId,
                            exception.getClass().getSimpleName());
                }
            }
            if (txids.size() < limite) {
                break;
            }
        }
        LOGGER.info(
                "efi_reconciliacao_ciclo_concluido requestId={} processados={}",
                requestId,
                processados.size());
        return processados.size();
    }

    private String referenciaSegura(String txid) {
        String valor = txid == null ? "" : txid.trim();
        return "***" + valor.substring(Math.max(0, valor.length() - 4));
    }
}
