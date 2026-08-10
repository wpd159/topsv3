package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
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
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiPagamentoConciliacaoService {

    public static final String STATUS_ERRO_TRANSITORIO = "EFI_INDISPONIVEL_TRANSITORIO";

    private final PagamentoRepository pagamentoRepository;
    private final PagamentoEventoRepository eventoRepository;
    private final PagamentoConciliacaoRepository conciliacaoRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final CreditoLedgerOperacaoService ledgerService;
    private final EfiPixGateway gateway;
    private final TransactionTemplate transactions;

    public EfiPagamentoConciliacaoService(
            PagamentoRepository pagamentoRepository,
            PagamentoEventoRepository eventoRepository,
            PagamentoConciliacaoRepository conciliacaoRepository,
            AuditoriaEventoRepository auditoriaRepository,
            CreditoLedgerOperacaoService ledgerService,
            EfiPixGateway gateway,
            PlatformTransactionManager transactionManager) {
        this.pagamentoRepository = pagamentoRepository;
        this.eventoRepository = eventoRepository;
        this.conciliacaoRepository = conciliacaoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.ledgerService = ledgerService;
        this.gateway = gateway;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ConciliacaoResultado conciliar(
            String txid,
            String eventoId,
            String payloadHash,
            OrigemConciliacaoPagamento origem,
            String requestId) {
        return conciliarConsultandoProvedor(txid, eventoId, payloadHash, origem, requestId);
    }

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

    public ConciliacaoResultado aplicarCobrancaConsultada(
            EfiPixGateway.CobrancaPix cobranca,
            String eventoId,
            String payloadHash,
            OrigemConciliacaoPagamento origem,
            String requestId) {
        if (cobranca == null || cobranca.txid() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cobranca Efi divergente");
        }
        AmbientePagamento ambienteGateway = gateway.ambiente();
        return Objects.requireNonNull(transactions.execute(ignored -> aplicarCobrancaLocal(
                cobranca,
                ambienteGateway,
                eventoId,
                payloadHash,
                origem,
                requestId)));
    }

    public void registrarFalhaTransitoria(String txid, String requestId) {
        transactions.executeWithoutResult(ignored -> {
            PagamentoEntity pagamento = pagamentoRepository.findByTxidForUpdate(txid).orElse(null);
            if (pagamento == null || !podeRetentar(pagamento)) {
                return;
            }
            pagamento.atualizarStatusProvedor(
                    StatusInternoPagamento.ERRO,
                    STATUS_ERRO_TRANSITORIO,
                    OffsetDateTime.now(ZoneOffset.UTC));
        });
    }

    private ConciliacaoResultado conciliarConsultandoProvedor(
            String txid,
            String eventoId,
            String payloadHash,
            OrigemConciliacaoPagamento origem,
            String requestId) {
        PagamentoEntity pagamento = pagamentoRepository.findByTxid(txid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento Efi nao encontrado"));
        if (!podeRetentar(pagamento)) {
            return new ConciliacaoResultado(pagamento, null, true, null);
        }
        if (pagamento.getAmbiente() == null) {
            return registrarErroDeContexto(
                    txid,
                    eventoId,
                    payloadHash,
                    origem,
                    "AMBIENTE_LEGADO_INDEFINIDO");
        }
        AmbientePagamento ambienteGateway = gateway.ambiente();
        if (ambienteGateway == null || pagamento.getAmbiente() != ambienteGateway) {
            return registrarErroDeContexto(
                    txid,
                    eventoId,
                    payloadHash,
                    origem,
                    "AMBIENTE_DIVERGENTE");
        }
        try {
            EfiPixGateway.CobrancaPix cobranca = gateway.consultarCobranca(txid);
            if (cobranca == null || !txid.equals(cobranca.txid())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "cobranca Efi divergente");
            }
            return aplicarCobrancaConsultada(cobranca, eventoId, payloadHash, origem, requestId);
        } catch (EfiPixGatewayException exception) {
            if (!exception.isConfiguracao() && !exception.isCobrancaNaoEncontrada()) {
                registrarFalhaTransitoria(txid, requestId);
            }
            throw exception;
        }
    }

    private ConciliacaoResultado aplicarCobrancaLocal(
            EfiPixGateway.CobrancaPix cobranca,
            AmbientePagamento ambienteGateway,
            String eventoId,
            String payloadHash,
            OrigemConciliacaoPagamento origem,
            String requestId) {
        PagamentoEntity pagamento = pagamentoRepository.findByTxidForUpdate(cobranca.txid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento Efi nao encontrado"));
        if (!podeRetentar(pagamento)) {
            registrarEvento(
                    pagamento,
                    eventoId,
                    payloadHash,
                    pagamento.getStatusInterno().name(),
                    origem,
                    OffsetDateTime.now(ZoneOffset.UTC));
            return new ConciliacaoResultado(pagamento, cobranca, true, null);
        }
        validarIdentidade(pagamento, cobranca);

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String erroAmbiente = erroAmbiente(pagamento, cobranca, ambienteGateway);
        if (erroAmbiente != null) {
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.ERRO, erroAmbiente, agora);
            registrarEvento(pagamento, eventoId, payloadHash, erroAmbiente, origem, agora);
            return new ConciliacaoResultado(pagamento, cobranca, false, erroAmbiente);
        }

        boolean idempotente = pagamento.getCreditadoEm() != null;
        String erroResumido = null;
        String status = statusNormalizado(cobranca.status());
        if ("CONCLUIDA".equals(status)) {
            idempotente = conciliarConfirmada(pagamento, cobranca, origem, requestId, agora, agora);
            if (pagamento.getStatusInterno() == StatusInternoPagamento.ERRO) {
                erroResumido = pagamento.getStatusProvedor();
            }
        } else if ("ATIVA".equals(status)) {
            pagamento.aguardarPagamento(
                    cobranca.identificadorLocalizacao(),
                    status,
                    cobranca.expiracaoEm(),
                    agora);
        } else if ("EXPIRADA".equals(status)) {
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.EXPIRADO, status, agora);
        } else if (status.startsWith("REMOVIDA")) {
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.CANCELADO, status, agora);
        } else {
            status = "STATUS_NAO_RECONHECIDO";
            erroResumido = status;
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.ERRO, status, agora);
        }
        registrarEvento(pagamento, eventoId, payloadHash, status, origem, agora);
        return new ConciliacaoResultado(pagamento, cobranca, idempotente, erroResumido);
    }

    private ConciliacaoResultado registrarErroDeContexto(
            String txid,
            String eventoId,
            String payloadHash,
            OrigemConciliacaoPagamento origem,
            String erro) {
        return Objects.requireNonNull(transactions.execute(ignored -> {
            PagamentoEntity pagamento = pagamentoRepository.findByTxidForUpdate(txid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento Efi nao encontrado"));
            if (!podeRetentar(pagamento)) {
                return new ConciliacaoResultado(pagamento, null, true, null);
            }
            OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
            pagamento.atualizarStatusProvedor(StatusInternoPagamento.ERRO, erro, agora);
            registrarEvento(pagamento, eventoId, payloadHash, erro, origem, agora);
            return new ConciliacaoResultado(pagamento, null, false, erro);
        }));
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
        if (!podeRetentar(pagamento)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "pagamento em estado incompativel com credito");
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
                "Confirmacao Pix conciliada",
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
                "PAGAMENTO_PIX_CONCILIADO",
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
        if (!pagamento.getTxid().equals(cobranca.txid())
                || cobranca.valorOriginal() == null
                || pagamento.getValor().compareTo(cobranca.valorOriginal()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cobranca Efi divergente");
        }
    }

    private String erroAmbiente(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca,
            AmbientePagamento ambienteGateway) {
        if (pagamento.getAmbiente() == null) {
            return "AMBIENTE_LEGADO_INDEFINIDO";
        }
        if (ambienteGateway == null
                || cobranca.ambiente() == null
                || pagamento.getAmbiente() != ambienteGateway
                || pagamento.getAmbiente() != cobranca.ambiente()) {
            return "AMBIENTE_DIVERGENTE";
        }
        return null;
    }

    private boolean podeRetentar(PagamentoEntity pagamento) {
        if (pagamento == null || pagamento.getCreditadoEm() != null) {
            return false;
        }
        return pagamento.getStatusInterno() == StatusInternoPagamento.CRIADO
                || pagamento.getStatusInterno() == StatusInternoPagamento.AGUARDANDO_PAGAMENTO
                || (pagamento.getStatusInterno() == StatusInternoPagamento.ERRO
                && STATUS_ERRO_TRANSITORIO.equals(pagamento.getStatusProvedor()));
    }

    private String statusNormalizado(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    public record ConciliacaoResultado(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca,
            boolean idempotente,
            String erroResumido) {
    }
}
