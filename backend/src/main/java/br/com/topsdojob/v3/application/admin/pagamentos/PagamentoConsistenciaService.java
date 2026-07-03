package br.com.topsdojob.v3.application.admin.pagamentos;

import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoConsistenciaItemDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoConsistenciaResumoDto;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoConciliacaoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEventoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoConciliacaoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoWebhookRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
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
public class PagamentoConsistenciaService {

    private static final String REFERENCIA_PAGAMENTO = "PAGAMENTO";

    private final PagamentoRepository pagamentoRepository;
    private final MovimentoCreditoRepository movimentoRepository;
    private final PagamentoConciliacaoRepository conciliacaoRepository;
    private final PagamentoEventoRepository eventoRepository;
    private final PagamentoWebhookRepository webhookRepository;
    private final PagamentoEvidenciaProvedorService evidenciaService;
    private final PagamentoSanitizer sanitizer;

    public PagamentoConsistenciaService(
            PagamentoRepository pagamentoRepository,
            MovimentoCreditoRepository movimentoRepository,
            PagamentoConciliacaoRepository conciliacaoRepository,
            PagamentoEventoRepository eventoRepository,
            PagamentoWebhookRepository webhookRepository,
            PagamentoEvidenciaProvedorService evidenciaService,
            PagamentoSanitizer sanitizer) {
        this.pagamentoRepository = pagamentoRepository;
        this.movimentoRepository = movimentoRepository;
        this.conciliacaoRepository = conciliacaoRepository;
        this.eventoRepository = eventoRepository;
        this.webhookRepository = webhookRepository;
        this.evidenciaService = evidenciaService;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public AdminPagamentoConsistenciaResumoDto consultarConsistencia() {
        OffsetDateTime agora = OffsetDateTime.now();
        return consultar(
                pagamentoRepository.findAll(),
                movimentoRepository.findAll(),
                conciliacaoRepository.findAll(),
                eventoRepository.findAll(),
                webhookRepository.findAll(),
                agora);
    }

    @Transactional(readOnly = true)
    public AdminPagamentoConsistenciaResumoDto consultarInconsistencias() {
        return consultarConsistencia();
    }

    AdminPagamentoConsistenciaResumoDto consultar(
            Collection<PagamentoEntity> pagamentos,
            Collection<MovimentoCreditoEntity> movimentos,
            Collection<PagamentoConciliacaoEntity> conciliacoes,
            Collection<PagamentoEventoEntity> eventos,
            Collection<PagamentoWebhookEntity> webhooks,
            OffsetDateTime agora) {
        Map<UUID, PagamentoEntity> pagamentosPorId = pagamentos.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(PagamentoEntity::getId, Function.identity(), (left, right) -> left));
        Map<UUID, List<PagamentoEventoEntity>> eventosPorPagamento = eventos.stream()
                .filter(item -> item.getPagamentoId() != null)
                .collect(Collectors.groupingBy(PagamentoEventoEntity::getPagamentoId));
        Map<UUID, List<PagamentoConciliacaoEntity>> conciliacoesPorPagamento = conciliacoes.stream()
                .filter(item -> item.getPagamentoId() != null)
                .collect(Collectors.groupingBy(PagamentoConciliacaoEntity::getPagamentoId));
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

        List<AdminPagamentoConsistenciaItemDto> itens = new ArrayList<>();
        pagamentos.forEach(pagamento -> avaliarPagamento(
                pagamento,
                eventosPorPagamento.getOrDefault(pagamento.getId(), List.of()),
                webhooksRelacionados(pagamento, webhooks),
                pagamentosComMovimento,
                pagamentosConciliados,
                conciliacoesPorPagamento.getOrDefault(pagamento.getId(), List.of()),
                itens,
                agora));
        avaliarPagamentosDuplicados(pagamentos, itens, agora);
        avaliarCreditosSemPagamento(movimentos, pagamentosPorId, conciliacoes, itens, agora);
        avaliarWebhooksDuplicados(webhooks, itens, agora);

        List<AdminPagamentoConsistenciaItemDto> ordenados = itens.stream()
                .sorted(Comparator.comparing(AdminPagamentoConsistenciaItemDto::codigo)
                        .thenComparing(item -> Objects.toString(item.pagamentoId(), ""))
                        .thenComparing(item -> Objects.toString(item.movimentoCreditoId(), "")))
                .toList();
        return new AdminPagamentoConsistenciaResumoDto(ordenados, ordenados.size(), agora, true);
    }

    private void avaliarPagamento(
            PagamentoEntity pagamento,
            List<PagamentoEventoEntity> eventos,
            List<PagamentoWebhookEntity> webhooks,
            List<UUID> pagamentosComMovimento,
            List<UUID> pagamentosConciliados,
            List<PagamentoConciliacaoEntity> conciliacoes,
            List<AdminPagamentoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        ProvedorPagamento classificado = evidenciaService.classificar(pagamento, eventos, webhooks);
        if (pagamento.getProvedor() == null) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.PAGAMENTO_SEM_PROVEDOR, "ALERTA", agora);
        }
        if (pagamento.getProvedor() == ProvedorPagamento.DESCONHECIDO || classificado == ProvedorPagamento.DESCONHECIDO) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.PAGAMENTO_PROVEDOR_DESCONHECIDO, "ALERTA", agora);
        }
        if (!sanitizer.presente(pagamento.getTxid()) && !sanitizer.presente(pagamento.getIdentificadorProvedor())) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.PAGAMENTO_SEM_TXID, "ALERTA", agora);
        }
        if (classificado == ProvedorPagamento.MERCADO_PAGO_LEGADO) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.PAGAMENTO_MERCADO_PAGO_LEGADO, "INFO", agora);
        }
        if (pagamento.getStatusInterno() == StatusInternoPagamento.APROVADO
                && !pagamentosComMovimento.contains(pagamento.getId())
                && !pagamentosConciliados.contains(pagamento.getId())) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.PAGAMENTO_APROVADO_SEM_CREDITO, "ALERTA", agora);
        }
        if (classificado == ProvedorPagamento.EFI
                && pagamento.getStatusInterno() != StatusInternoPagamento.APROVADO) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.PAGAMENTO_EFI_NAO_CONFIRMADO, "ALERTA", agora);
        }
        if (statusInconsistente(pagamento, conciliacoes)) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.STATUS_PAGAMENTO_INCONSISTENTE, "ALERTA", agora);
        }
        if (eventos.stream().anyMatch(item -> sanitizer.presente(item.getPayloadHash()))
                || webhooks.stream().anyMatch(item -> sanitizer.presente(item.getPayloadHash()))) {
            adicionar(itens, pagamento, null, PagamentoConsistenciaCodigo.PAGAMENTO_PAYLOAD_SENSIVEL_OCULTO, "INFO", agora);
        }
    }

    private void avaliarPagamentosDuplicados(
            Collection<PagamentoEntity> pagamentos,
            List<AdminPagamentoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        adicionarDuplicados(pagamentos, itens, agora, PagamentoEntity::getTxid);
        adicionarDuplicados(pagamentos, itens, agora, PagamentoEntity::getIdentificadorProvedor);
    }

    private void adicionarDuplicados(
            Collection<PagamentoEntity> pagamentos,
            List<AdminPagamentoConsistenciaItemDto> itens,
            OffsetDateTime agora,
            Function<PagamentoEntity, String> chave) {
        pagamentos.stream()
                .filter(item -> sanitizer.presente(chave.apply(item)))
                .collect(Collectors.groupingBy(item -> chave.apply(item)))
                .values()
                .stream()
                .filter(grupo -> grupo.size() > 1)
                .map(grupo -> grupo.get(0))
                .forEach(pagamento -> adicionar(
                        itens,
                        pagamento,
                        null,
                        PagamentoConsistenciaCodigo.PAGAMENTO_DUPLICADO,
                        "ALERTA",
                        agora));
    }

    private void avaliarCreditosSemPagamento(
            Collection<MovimentoCreditoEntity> movimentos,
            Map<UUID, PagamentoEntity> pagamentosPorId,
            Collection<PagamentoConciliacaoEntity> conciliacoes,
            List<AdminPagamentoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        Map<UUID, PagamentoConciliacaoEntity> conciliacaoPorMovimento = conciliacoes.stream()
                .filter(item -> item.getMovimentoCreditoId() != null)
                .collect(Collectors.toMap(
                        PagamentoConciliacaoEntity::getMovimentoCreditoId,
                        Function.identity(),
                        (left, right) -> left));
        movimentos.stream()
                .filter(item -> item.getOrigem() == OrigemMovimentoCredito.PAGAMENTO)
                .filter(item -> !possuiPagamento(item, pagamentosPorId, conciliacaoPorMovimento))
                .forEach(movimento -> itens.add(new AdminPagamentoConsistenciaItemDto(
                        movimento.getUsuarioId(),
                        null,
                        movimento.getId(),
                        PagamentoConsistenciaCodigo.CREDITO_SEM_PAGAMENTO.name(),
                        "ALERTA",
                        mensagem(PagamentoConsistenciaCodigo.CREDITO_SEM_PAGAMENTO),
                        agora,
                        true)));
    }

    private void avaliarWebhooksDuplicados(
            Collection<PagamentoWebhookEntity> webhooks,
            List<AdminPagamentoConsistenciaItemDto> itens,
            OffsetDateTime agora) {
        webhooks.stream()
                .filter(item -> sanitizer.presente(item.getPayloadHash()))
                .collect(Collectors.groupingBy(PagamentoWebhookEntity::getPayloadHash))
                .values()
                .stream()
                .filter(grupo -> grupo.size() > 1)
                .map(grupo -> grupo.get(0))
                .forEach(webhook -> itens.add(new AdminPagamentoConsistenciaItemDto(
                        null,
                        null,
                        null,
                        PagamentoConsistenciaCodigo.EVENTO_WEBHOOK_DUPLICADO.name(),
                        "ALERTA",
                        mensagem(PagamentoConsistenciaCodigo.EVENTO_WEBHOOK_DUPLICADO),
                        agora,
                        true)));
    }

    private boolean possuiPagamento(
            MovimentoCreditoEntity movimento,
            Map<UUID, PagamentoEntity> pagamentosPorId,
            Map<UUID, PagamentoConciliacaoEntity> conciliacaoPorMovimento) {
        if (REFERENCIA_PAGAMENTO.equalsIgnoreCase(Objects.toString(movimento.getReferenciaTipo(), ""))
                && movimento.getReferenciaId() != null
                && pagamentosPorId.containsKey(movimento.getReferenciaId())) {
            return true;
        }
        PagamentoConciliacaoEntity conciliacao = conciliacaoPorMovimento.get(movimento.getId());
        return conciliacao != null && pagamentosPorId.containsKey(conciliacao.getPagamentoId());
    }

    private boolean statusInconsistente(PagamentoEntity pagamento, Collection<PagamentoConciliacaoEntity> conciliacoes) {
        if (pagamento.getStatusInterno() == StatusInternoPagamento.APROVADO
                && pagamento.getStatusProvedor() != null
                && pagamento.getStatusProvedor().toUpperCase(java.util.Locale.ROOT).contains("PENDENTE")) {
            return true;
        }
        return pagamento.getCreditadoEm() != null && conciliacoes.isEmpty();
    }

    private List<PagamentoWebhookEntity> webhooksRelacionados(
            PagamentoEntity pagamento,
            Collection<PagamentoWebhookEntity> webhooks) {
        return webhooks.stream()
                .filter(item -> item.getProvedor() == pagamento.getProvedor())
                .filter(item -> Objects.equals(item.getTxid(), pagamento.getTxid()))
                .toList();
    }

    private void adicionar(
            List<AdminPagamentoConsistenciaItemDto> itens,
            PagamentoEntity pagamento,
            UUID movimentoCreditoId,
            PagamentoConsistenciaCodigo codigo,
            String severidade,
            OffsetDateTime agora) {
        itens.add(new AdminPagamentoConsistenciaItemDto(
                pagamento.getUsuarioId(),
                pagamento.getId(),
                movimentoCreditoId,
                codigo.name(),
                severidade,
                mensagem(codigo),
                agora,
                true));
    }

    private String mensagem(PagamentoConsistenciaCodigo codigo) {
        return switch (codigo) {
            case PAGAMENTO_SEM_PROVEDOR ->
                    "Pagamento local nao possui provedor identificavel.";
            case PAGAMENTO_SEM_TXID ->
                    "Pagamento local nao possui evidencia de transacao do provedor.";
            case PAGAMENTO_DUPLICADO ->
                    "Evidencia operacional de pagamento aparece em mais de um registro local.";
            case PAGAMENTO_APROVADO_SEM_CREDITO ->
                    "Pagamento aprovado localmente sem credito vinculado ao ledger.";
            case CREDITO_SEM_PAGAMENTO ->
                    "Credito de origem pagamento nao possui pagamento conciliado localmente.";
            case STATUS_PAGAMENTO_INCONSISTENTE ->
                    "Status interno e evidencia do provedor divergem no registro local.";
            case EVENTO_WEBHOOK_DUPLICADO ->
                    "Eventos/webhooks sanitizados compartilham a mesma evidencia de payload.";
            case PAGAMENTO_EFI_NAO_CONFIRMADO ->
                    "Pagamento classificado como Efi ainda nao esta aprovado localmente.";
            case PAGAMENTO_MERCADO_PAGO_LEGADO ->
                    "Pagamento possui evidencia explicita de Mercado Pago legado.";
            case PAGAMENTO_PROVEDOR_DESCONHECIDO ->
                    "Pagamento permanece com provedor desconhecido por falta de evidencia segura.";
            case PAGAMENTO_PAYLOAD_SENSIVEL_OCULTO ->
                    "Payload financeiro bruto permanece oculto; apenas hash/evidencia sanitizada existe.";
            default -> "Pagamento local sem alerta.";
        };
    }
}
