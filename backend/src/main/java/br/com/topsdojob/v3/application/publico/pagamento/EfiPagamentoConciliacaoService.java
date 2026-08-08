package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoConciliacaoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoConciliacaoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusConciliacaoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiPagamentoConciliacaoService {

    private final PagamentoRepository pagamentoRepository;
    private final PagamentoEventoRepository eventoRepository;
    private final PagamentoConciliacaoRepository conciliacaoRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final CreditoLedgerOperacaoService ledgerService;
    private final EfiPixGateway gateway;

    public EfiPagamentoConciliacaoService(
            PagamentoRepository pagamentoRepository,
            PagamentoEventoRepository eventoRepository,
            PagamentoConciliacaoRepository conciliacaoRepository,
            AuditoriaEventoRepository auditoriaRepository,
            CreditoLedgerOperacaoService ledgerService,
            EfiPixGateway gateway) {
        this.pagamentoRepository = pagamentoRepository;
        this.eventoRepository = eventoRepository;
        this.conciliacaoRepository = conciliacaoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.ledgerService = ledgerService;
        this.gateway = gateway;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConciliacaoResultado conciliar(
            String txid,
            String eventoId,
            String payloadHash,
            OrigemConciliacaoPagamento origem,
            String requestId) {
        return conciliarConsultandoProvedor(txid, eventoId, payloadHash, origem, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConciliacaoResultado conciliarWebhook(
            String txid,
            String eventoId,
            String payloadHash,
            String requestId) {
        return conciliarConsultandoProvedor(
                txid,
                eventoId,
                payloadHash,
                OrigemConciliacaoPagamento.WEBHOOK,
                requestId);
    }

    private ConciliacaoResultado conciliarConsultandoProvedor(
            String txid,
            String eventoId,
            String payloadHash,
            OrigemConciliacaoPagamento origem,
            String requestId) {
        PagamentoEntity pagamento = pagamentoRepository.findByTxidForUpdate(txid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento Efi nao encontrado"));
        EfiPixGateway.CobrancaPix cobranca = gateway.consultarCobranca(txid);
        validarIdentidade(pagamento, cobranca);

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String erroAmbiente = erroAmbiente(pagamento, cobranca);
        if (erroAmbiente != null) {
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.ERRO, erroAmbiente, agora);
            registrarEvento(pagamento, eventoId, payloadHash, erroAmbiente, origem, agora);
            return new ConciliacaoResultado(pagamento, cobranca, false, erroAmbiente);
        }

        boolean idempotente = pagamento.getCreditadoEm() != null;
        String status = cobranca.status() == null
                ? ""
                : cobranca.status().trim().toUpperCase(Locale.ROOT);

        if ("CONCLUIDA".equals(status)) {
            idempotente = conciliarConfirmada(pagamento, cobranca, origem, requestId, agora, agora);
        } else if ("ATIVA".equals(status)) {
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.AGUARDANDO_PAGAMENTO, status, agora);
        } else if ("EXPIRADA".equals(status)) {
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.EXPIRADO, status, agora);
        } else if (status.startsWith("REMOVIDA")) {
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.CANCELADO, status, agora);
        } else {
            status = "STATUS_NAO_RECONHECIDO";
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.ERRO, status, agora);
        }
        registrarEvento(pagamento, eventoId, payloadHash, status, origem, agora);
        return new ConciliacaoResultado(pagamento, cobranca, idempotente, null);
    }

    private boolean conciliarConfirmada(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca,
            OrigemConciliacaoPagamento origem,
            String requestId,
            OffsetDateTime aprovadoEm,
            OffsetDateTime agora) {
        BigDecimal recebido = cobranca.valorRecebido() == null ? BigDecimal.ZERO : cobranca.valorRecebido();
        if (pagamento.getValor().compareTo(recebido) != 0) {
            registrarConciliacao(
                    pagamento,
                    null,
                    origem,
                    StatusConciliacaoPagamento.DIVERGENTE,
                    recebido,
                    null,
                    null,
                    agora);
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.ERRO, "CONCLUIDA_DIVERGENTE", agora);
            return false;
        }
        if (pagamento.getCreditadoEm() != null) {
            return true;
        }
        if (pagamento.getStatusInterno() != StatusInternoPagamento.CRIADO
                && pagamento.getStatusInterno() != StatusInternoPagamento.AGUARDANDO_PAGAMENTO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "pagamento em estado incompatível com crédito");
        }

        int saldoAntes = ledgerService.bloquearEConsultarSaldo(pagamento.getUsuarioId());
        var lancamento = ledgerService.registrar(
                pagamento.getUsuarioId(),
                TipoMovimentoCredito.ENTRADA,
                DirecaoMovimentoCredito.CREDITO,
                pagamento.getQuantidadeCreditos(),
                saldoAntes,
                OrigemMovimentoCredito.PAGAMENTO,
                "PAGAMENTO",
                pagamento.getId(),
                "efi-pagamento:" + pagamento.getId(),
                null,
                "Confirmacao Pix Efi conciliada",
                requestId);
        registrarConciliacao(
                pagamento,
                lancamento.movimento().getId(),
                origem,
                StatusConciliacaoPagamento.CONCILIADO,
                recebido,
                aprovadoEm,
                agora,
                agora);
        pagamento.marcarAprovadoECreditado(aprovadoEm, agora);
        auditoriaRepository.save(AuditoriaEventoEntity.registrarWebhook(
                UUID.randomUUID(),
                null,
                "PAGAMENTO_EFI_CONCILIADO",
                "PAGAMENTO",
                pagamento.getId(),
                "{\"status\":\"AGUARDANDO_PAGAMENTO\"}",
                "{\"status\":\"APROVADO\",\"creditoUnico\":true}",
                requestId,
                agora));
        return lancamento.idempotente();
    }

    private void registrarConciliacao(
            PagamentoEntity pagamento,
            UUID movimentoId,
            OrigemConciliacaoPagamento origem,
            StatusConciliacaoPagamento status,
            BigDecimal valorConfirmado,
            OffsetDateTime aprovadoEm,
            OffsetDateTime creditadoEm,
            OffsetDateTime agora) {
        PagamentoConciliacaoEntity conciliacao = conciliacaoRepository.findByPagamentoId(pagamento.getId())
                .orElseGet(() -> PagamentoConciliacaoEntity.registrar(
                        UUID.randomUUID(),
                        pagamento.getId(),
                        movimentoId,
                        origem,
                        status,
                        valorConfirmado,
                        pagamento.getQuantidadeCreditos(),
                        aprovadoEm,
                        creditadoEm,
                        agora));
        conciliacao.atualizar(
                movimentoId,
                status,
                valorConfirmado,
                pagamento.getQuantidadeCreditos(),
                aprovadoEm,
                creditadoEm);
        conciliacaoRepository.save(conciliacao);
    }

    private void registrarEvento(
            PagamentoEntity pagamento,
            String eventoId,
            String payloadHash,
            String status,
            OrigemConciliacaoPagamento origem,
            OffsetDateTime agora) {
        if (eventoId != null && eventoRepository.findByProvedorAndProvedorEventoId(
                br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento.EFI,
                eventoId).isPresent()) {
            return;
        }
        eventoRepository.save(PagamentoEventoEntity.registrarEfi(
                UUID.randomUUID(),
                pagamento.getId(),
                eventoId,
                origem == OrigemConciliacaoPagamento.WEBHOOK ? "PIX_RECEBIDO" : "CONSULTA_COBRANCA",
                payloadHash,
                status,
                agora,
                agora,
                pagamento.getCreditadoEm() == null ? "SEM_CREDITO" : "CREDITO_CONCILIADO"));
    }

    private void validarIdentidade(PagamentoEntity pagamento, EfiPixGateway.CobrancaPix cobranca) {
        if (cobranca == null
                || !pagamento.getTxid().equals(cobranca.txid())
                || cobranca.valorOriginal() == null
                || pagamento.getValor().compareTo(cobranca.valorOriginal()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cobranca Efi divergente");
        }
    }

    private String erroAmbiente(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca) {
        if (pagamento.getAmbiente() == null) {
            return "AMBIENTE_LEGADO_INDEFINIDO";
        }
        AmbientePagamento ambienteGateway = gateway.ambiente();
        if (ambienteGateway == null
                || cobranca.ambiente() == null
                || pagamento.getAmbiente() != ambienteGateway
                || pagamento.getAmbiente() != cobranca.ambiente()) {
            return "AMBIENTE_DIVERGENTE";
        }
        return null;
    }

    public record ConciliacaoResultado(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca,
            boolean idempotente,
            String erroResumido) {
    }
}
