package br.com.topsdojob.v3.application.admin.creditos;

import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoConsistenciaItemDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoConsistenciaResumoDto;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.credito.SaldoCreditoUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoConciliacaoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoConciliacaoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.SaldoCreditoUsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditoConsistenciaService {

    private static final String REFERENCIA_PAGAMENTO = "PAGAMENTO";

    private final MovimentoCreditoRepository movimentoRepository;
    private final SaldoCreditoUsuarioRepository saldoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PagamentoConciliacaoRepository conciliacaoRepository;

    public CreditoConsistenciaService(
            MovimentoCreditoRepository movimentoRepository,
            SaldoCreditoUsuarioRepository saldoRepository,
            PagamentoRepository pagamentoRepository,
            PagamentoConciliacaoRepository conciliacaoRepository) {
        this.movimentoRepository = movimentoRepository;
        this.saldoRepository = saldoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.conciliacaoRepository = conciliacaoRepository;
    }

    @Transactional(readOnly = true)
    public AdminCreditoConsistenciaResumoDto consultarConsistencia() {
        OffsetDateTime agora = OffsetDateTime.now();
        return consultar(
                movimentoRepository.findAll(),
                saldoRepository.findAll(),
                pagamentoRepository.findAll(),
                conciliacaoRepository.findAll(),
                agora);
    }

    @Transactional(readOnly = true)
    public AdminCreditoConsistenciaResumoDto consultarInconsistencias() {
        return consultarConsistencia();
    }

    AdminCreditoConsistenciaResumoDto consultar(
            Collection<MovimentoCreditoEntity> movimentos,
            Collection<SaldoCreditoUsuarioEntity> saldos,
            Collection<PagamentoEntity> pagamentos,
            Collection<PagamentoConciliacaoEntity> conciliacoes,
            OffsetDateTime agora) {
        Map<UUID, PagamentoEntity> pagamentosPorId = pagamentos.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(PagamentoEntity::getId, Function.identity(), (left, right) -> left));
        Map<UUID, PagamentoConciliacaoEntity> conciliacaoPorMovimento = conciliacoes.stream()
                .filter(item -> item.getMovimentoCreditoId() != null)
                .collect(Collectors.toMap(
                        PagamentoConciliacaoEntity::getMovimentoCreditoId,
                        Function.identity(),
                        (left, right) -> left));
        Map<UUID, List<MovimentoCreditoEntity>> movimentosPorUsuario = movimentos.stream()
                .filter(item -> item.getUsuarioId() != null)
                .collect(Collectors.groupingBy(MovimentoCreditoEntity::getUsuarioId));
        List<AdminCreditoConsistenciaItemDto> itens = new ArrayList<>();

        movimentos.forEach(movimento -> avaliarMovimento(
                movimento,
                pagamentosPorId,
                conciliacaoPorMovimento,
                itens,
                agora));
        avaliarChavesOperacionaisDuplicadas(movimentos, itens, agora);
        avaliarSaldos(saldos, movimentosPorUsuario, itens, agora);
        avaliarPagamentosAprovadosSemCredito(pagamentos, movimentos, conciliacoes, itens, agora);

        List<AdminCreditoConsistenciaItemDto> ordenados = itens.stream()
                .sorted(Comparator.comparing(AdminCreditoConsistenciaItemDto::codigo)
                        .thenComparing(item -> Objects.toString(item.usuarioId(), ""))
                        .thenComparing(item -> Objects.toString(item.movimentoId(), "")))
                .toList();
        return new AdminCreditoConsistenciaResumoDto(ordenados, ordenados.size(), agora, true);
    }

    private void avaliarMovimento(
            MovimentoCreditoEntity movimento,
            Map<UUID, PagamentoEntity> pagamentosPorId,
            Map<UUID, PagamentoConciliacaoEntity> conciliacaoPorMovimento,
            List<AdminCreditoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        if (movimento.getQuantidade() == null || movimento.getQuantidade() <= 0) {
            adicionar(itens, movimento, null, CreditoConsistenciaCodigo.QUANTIDADE_INVALIDA, agora);
        }
        if (movimento.getOrigem() == null) {
            adicionar(itens, movimento, null, CreditoConsistenciaCodigo.MOVIMENTO_SEM_ORIGEM, agora);
        }
        if (saldoNegativo(movimento)) {
            adicionar(itens, movimento, null, CreditoConsistenciaCodigo.SALDO_NEGATIVO, agora);
        }
        if (!saldoCoerente(movimento)) {
            adicionar(itens, movimento, null, CreditoConsistenciaCodigo.SALDO_INCONSISTENTE, agora);
        }
        if (movimento.getTipo() == TipoMovimentoCredito.AJUSTE
                && (movimento.getAtorUsuarioId() == null
                        || movimento.getObservacao() == null
                        || movimento.getObservacao().isBlank()
                        || movimento.getRequestId() == null
                        || movimento.getRequestId().isBlank())) {
            adicionar(itens, movimento, null, CreditoConsistenciaCodigo.REGRA_AJUSTE_CREDITO_PENDENTE, agora);
        }
        if (movimento.getOrigem() == OrigemMovimentoCredito.PAGAMENTO) {
            PagamentoEntity pagamento = pagamentoDoMovimento(movimento, pagamentosPorId, conciliacaoPorMovimento);
            if (pagamento == null) {
                adicionar(itens, movimento, null, CreditoConsistenciaCodigo.CREDITO_SEM_PAGAMENTO, agora);
            } else if (pagamento.getStatusInterno() != StatusInternoPagamento.APROVADO) {
                adicionar(itens, movimento, pagamento.getId(), CreditoConsistenciaCodigo.PAGAMENTO_NAO_CONFIRMADO, agora);
            }
        }
    }

    private void avaliarChavesOperacionaisDuplicadas(
            Collection<MovimentoCreditoEntity> movimentos,
            List<AdminCreditoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        movimentos.stream()
                .filter(item -> item.getIdempotencyKey() != null && !item.getIdempotencyKey().isBlank())
                .collect(Collectors.groupingBy(MovimentoCreditoEntity::getIdempotencyKey))
                .values()
                .stream()
                .filter(grupo -> grupo.size() > 1)
                .map(grupo -> grupo.get(0))
                .forEach(movimento ->
                        adicionar(itens, movimento, null, CreditoConsistenciaCodigo.IDEMPOTENCY_KEY_DUPLICADA, agora));
    }

    private void avaliarSaldos(
            Collection<SaldoCreditoUsuarioEntity> saldos,
            Map<UUID, List<MovimentoCreditoEntity>> movimentosPorUsuario,
            List<AdminCreditoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        for (SaldoCreditoUsuarioEntity saldo : saldos) {
            List<MovimentoCreditoEntity> movimentos = movimentosPorUsuario
                    .getOrDefault(saldo.getUsuarioId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(
                            MovimentoCreditoEntity::getCriadoEm,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            Integer saldoUltimoMovimento = movimentos.isEmpty()
                    ? 0
                    : movimentos.get(movimentos.size() - 1).getSaldoDepois();
            if (saldo.getSaldoAtual() == null || saldoUltimoMovimento == null
                    || !saldo.getSaldoAtual().equals(saldoUltimoMovimento)) {
                MovimentoCreditoEntity ultimo = movimentos.isEmpty() ? null : movimentos.get(movimentos.size() - 1);
                itens.add(new AdminCreditoConsistenciaItemDto(
                        saldo.getUsuarioId(),
                        ultimo == null ? null : ultimo.getId(),
                        null,
                        CreditoConsistenciaCodigo.SALDO_INCONSISTENTE.name(),
                        "ALERTA",
                        mensagem(CreditoConsistenciaCodigo.SALDO_INCONSISTENTE),
                        agora,
                        true));
            }
        }
    }

    private void avaliarPagamentosAprovadosSemCredito(
            Collection<PagamentoEntity> pagamentos,
            Collection<MovimentoCreditoEntity> movimentos,
            Collection<PagamentoConciliacaoEntity> conciliacoes,
            List<AdminCreditoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        List<UUID> pagamentosComMovimento = movimentos.stream()
                .filter(item -> REFERENCIA_PAGAMENTO.equalsIgnoreCase(Objects.toString(item.getReferenciaTipo(), "")))
                .map(MovimentoCreditoEntity::getReferenciaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<UUID> pagamentosConciliados = conciliacoes.stream()
                .map(PagamentoConciliacaoEntity::getPagamentoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        pagamentos.stream()
                .filter(item -> item.getStatusInterno() == StatusInternoPagamento.APROVADO)
                .filter(item -> !pagamentosComMovimento.contains(item.getId()))
                .filter(item -> !pagamentosConciliados.contains(item.getId()))
                .forEach(pagamento -> itens.add(new AdminCreditoConsistenciaItemDto(
                        pagamento.getUsuarioId(),
                        null,
                        pagamento.getId(),
                        CreditoConsistenciaCodigo.PAGAMENTO_APROVADO_SEM_CREDITO.name(),
                        "ALERTA",
                        mensagem(CreditoConsistenciaCodigo.PAGAMENTO_APROVADO_SEM_CREDITO),
                        agora,
                        true)));
    }

    private PagamentoEntity pagamentoDoMovimento(
            MovimentoCreditoEntity movimento,
            Map<UUID, PagamentoEntity> pagamentosPorId,
            Map<UUID, PagamentoConciliacaoEntity> conciliacaoPorMovimento) {
        if (REFERENCIA_PAGAMENTO.equalsIgnoreCase(Objects.toString(movimento.getReferenciaTipo(), ""))
                && movimento.getReferenciaId() != null) {
            return pagamentosPorId.get(movimento.getReferenciaId());
        }
        PagamentoConciliacaoEntity conciliacao = conciliacaoPorMovimento.get(movimento.getId());
        return conciliacao == null ? null : pagamentosPorId.get(conciliacao.getPagamentoId());
    }

    private boolean saldoNegativo(MovimentoCreditoEntity movimento) {
        return menorQueZero(movimento.getSaldoAntes()) || menorQueZero(movimento.getSaldoDepois());
    }

    private boolean menorQueZero(Integer valor) {
        return valor != null && valor < 0;
    }

    private boolean saldoCoerente(MovimentoCreditoEntity movimento) {
        if (movimento.getQuantidade() == null || movimento.getSaldoAntes() == null || movimento.getSaldoDepois() == null
                || movimento.getDirecao() == null) {
            return false;
        }
        if (movimento.getDirecao() == DirecaoMovimentoCredito.CREDITO) {
            return movimento.getSaldoDepois().equals(movimento.getSaldoAntes() + movimento.getQuantidade());
        }
        if (movimento.getDirecao() == DirecaoMovimentoCredito.DEBITO) {
            return movimento.getSaldoDepois().equals(movimento.getSaldoAntes() - movimento.getQuantidade());
        }
        return false;
    }

    private void adicionar(
            List<AdminCreditoConsistenciaItemDto> itens,
            MovimentoCreditoEntity movimento,
            UUID pagamentoId,
            CreditoConsistenciaCodigo codigo,
            OffsetDateTime agora) {
        itens.add(new AdminCreditoConsistenciaItemDto(
                movimento.getUsuarioId(),
                movimento.getId(),
                pagamentoId,
                codigo.name(),
                "ALERTA",
                mensagem(codigo),
                agora,
                true));
    }

    private String mensagem(CreditoConsistenciaCodigo codigo) {
        return switch (codigo) {
            case SALDO_INCONSISTENTE ->
                    "Saldo projetado difere do ultimo movimento ou do calculo local.";
            case MOVIMENTO_SEM_ORIGEM ->
                    "Movimento de credito nao possui origem operacional definida.";
            case IDEMPOTENCY_KEY_DUPLICADA ->
                    "Chave operacional duplicada no ledger local.";
            case CREDITO_SEM_PAGAMENTO ->
                    "Credito de origem pagamento nao possui pagamento conciliado localmente.";
            case PAGAMENTO_APROVADO_SEM_CREDITO ->
                    "Pagamento aprovado localmente sem credito vinculado ao ledger.";
            case REGRA_AJUSTE_CREDITO_PENDENTE ->
                    "Ajuste administrativo sem ator, motivo ou requestId auditavel.";
            case QUANTIDADE_INVALIDA ->
                    "Quantidade do movimento de credito e nula ou menor que um.";
            case SALDO_NEGATIVO ->
                    "Movimento indica saldo negativo antes ou depois da operacao.";
            case PAGAMENTO_NAO_CONFIRMADO ->
                    "Movimento referencia pagamento que ainda nao esta aprovado.";
            default -> "Consistencia de credito local sem alerta.";
        };
    }
}
