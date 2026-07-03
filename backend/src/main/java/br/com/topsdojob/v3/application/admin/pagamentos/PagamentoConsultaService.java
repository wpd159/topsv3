package br.com.topsdojob.v3.application.admin.pagamentos;

import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoDetalheDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoListaItemDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoPaginaDto;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoConciliacaoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEventoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoConciliacaoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoWebhookRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PagamentoConsultaService {

    private static final String REFERENCIA_PAGAMENTO = "PAGAMENTO";

    private final PagamentoRepository pagamentoRepository;
    private final MovimentoCreditoRepository movimentoRepository;
    private final PagamentoConciliacaoRepository conciliacaoRepository;
    private final PagamentoEventoRepository eventoRepository;
    private final PagamentoWebhookRepository webhookRepository;
    private final PagamentoConsistenciaService consistenciaService;
    private final PagamentoEvidenciaProvedorService evidenciaService;
    private final PagamentoSanitizer sanitizer;

    public PagamentoConsultaService(
            PagamentoRepository pagamentoRepository,
            MovimentoCreditoRepository movimentoRepository,
            PagamentoConciliacaoRepository conciliacaoRepository,
            PagamentoEventoRepository eventoRepository,
            PagamentoWebhookRepository webhookRepository,
            PagamentoConsistenciaService consistenciaService,
            PagamentoEvidenciaProvedorService evidenciaService,
            PagamentoSanitizer sanitizer) {
        this.pagamentoRepository = pagamentoRepository;
        this.movimentoRepository = movimentoRepository;
        this.conciliacaoRepository = conciliacaoRepository;
        this.eventoRepository = eventoRepository;
        this.webhookRepository = webhookRepository;
        this.consistenciaService = consistenciaService;
        this.evidenciaService = evidenciaService;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public AdminPagamentoPaginaDto<AdminPagamentoListaItemDto> listar(int page, int size) {
        int pagina = Math.max(0, page);
        int tamanho = Math.max(1, Math.min(size, 100));
        Page<PagamentoEntity> pagamentos = pagamentoRepository.findAll(PageRequest.of(
                pagina,
                tamanho,
                Sort.by(Sort.Direction.DESC, "criadoEm")));
        List<UUID> ids = pagamentos.getContent().stream()
                .map(PagamentoEntity::getId)
                .toList();
        List<PagamentoConciliacaoEntity> conciliacoes = ids.isEmpty()
                ? List.of()
                : conciliacaoRepository.findByPagamentoIdIn(ids);
        List<PagamentoEventoEntity> eventos = ids.isEmpty() ? List.of() : eventoRepository.findByPagamentoIdIn(ids);
        List<AdminPagamentoListaItemDto> itens = pagamentos.getContent().stream()
                .map(pagamento -> toListaItem(pagamento, eventosDoPagamento(pagamento, eventos), webhooksDoPagamento(pagamento), conciliacoes))
                .toList();
        return new AdminPagamentoPaginaDto<>(itens, pagamentos.getTotalElements(), pagina, tamanho, true);
    }

    @Transactional(readOnly = true)
    public AdminPagamentoDetalheDto detalhar(UUID id) {
        PagamentoEntity pagamento = pagamentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento nao encontrado"));
        List<PagamentoEventoEntity> eventos = eventoRepository.findByPagamentoId(id);
        List<PagamentoWebhookEntity> webhooks = webhooksDoPagamento(pagamento);
        List<PagamentoConciliacaoEntity> conciliacoes = conciliacaoRepository.findByPagamentoIdIn(List.of(id));
        List<String> codigos = consistenciaService.consultarConsistencia().itens().stream()
                .filter(item -> Objects.equals(id, item.pagamentoId()))
                .map(item -> item.codigo())
                .distinct()
                .toList();
        boolean creditoVinculado = creditoVinculado(pagamento, conciliacoes);
        return new AdminPagamentoDetalheDto(
                pagamento.getId(),
                pagamento.getUsuarioId(),
                sanitizer.enumName(pagamento.getProvedor()),
                sanitizer.enumName(evidenciaService.classificar(pagamento, eventos, webhooks)),
                sanitizer.enumName(pagamento.getMetodo()),
                sanitizer.enumName(pagamento.getStatusInterno()),
                pagamento.getStatusProvedor(),
                pagamento.getQuantidadeCreditos(),
                pagamento.getMoeda(),
                evidenciaTransacaoPresente(pagamento),
                sanitizer.mascararEvidenciaTransacao(primeiraEvidenciaTransacao(pagamento)),
                pagamento.getProvedor() != null,
                creditoVinculado,
                eventos.size(),
                webhooks.size(),
                codigos,
                pagamento.getCriadoEm(),
                pagamento.getAtualizadoEm(),
                true,
                false,
                false,
                false,
                true);
    }

    private AdminPagamentoListaItemDto toListaItem(
            PagamentoEntity pagamento,
            List<PagamentoEventoEntity> eventos,
            List<PagamentoWebhookEntity> webhooks,
            Collection<PagamentoConciliacaoEntity> conciliacoes) {
        return new AdminPagamentoListaItemDto(
                pagamento.getId(),
                pagamento.getUsuarioId(),
                sanitizer.enumName(pagamento.getProvedor()),
                sanitizer.enumName(evidenciaService.classificar(pagamento, eventos, webhooks)),
                sanitizer.enumName(pagamento.getMetodo()),
                sanitizer.enumName(pagamento.getStatusInterno()),
                pagamento.getStatusProvedor(),
                pagamento.getQuantidadeCreditos(),
                pagamento.getMoeda(),
                evidenciaTransacaoPresente(pagamento),
                sanitizer.mascararEvidenciaTransacao(primeiraEvidenciaTransacao(pagamento)),
                pagamento.getProvedor() != null,
                creditoVinculado(pagamento, conciliacoes),
                pagamento.getCriadoEm(),
                pagamento.getAtualizadoEm(),
                true);
    }

    private boolean creditoVinculado(PagamentoEntity pagamento, Collection<PagamentoConciliacaoEntity> conciliacoes) {
        boolean conciliado = conciliacoes.stream()
                .filter(item -> Objects.equals(item.getPagamentoId(), pagamento.getId()))
                .anyMatch(item -> item.getMovimentoCreditoId() != null);
        if (conciliado) {
            return true;
        }
        return !movimentoRepository
                .findByReferenciaTipoAndReferenciaIdIn(REFERENCIA_PAGAMENTO, List.of(pagamento.getId()))
                .isEmpty();
    }

    private List<PagamentoEventoEntity> eventosDoPagamento(
            PagamentoEntity pagamento,
            Collection<PagamentoEventoEntity> eventos) {
        return eventos.stream()
                .filter(item -> Objects.equals(item.getPagamentoId(), pagamento.getId()))
                .toList();
    }

    private List<PagamentoWebhookEntity> webhooksDoPagamento(PagamentoEntity pagamento) {
        if (pagamento.getProvedor() == null || !sanitizer.presente(pagamento.getTxid())) {
            return List.of();
        }
        return webhookRepository.findByProvedorAndTxidIn(pagamento.getProvedor(), List.of(pagamento.getTxid()));
    }

    private boolean evidenciaTransacaoPresente(PagamentoEntity pagamento) {
        return sanitizer.presente(pagamento.getTxid()) || sanitizer.presente(pagamento.getIdentificadorProvedor());
    }

    private String primeiraEvidenciaTransacao(PagamentoEntity pagamento) {
        if (sanitizer.presente(pagamento.getTxid())) {
            return pagamento.getTxid();
        }
        return pagamento.getIdentificadorProvedor();
    }
}
