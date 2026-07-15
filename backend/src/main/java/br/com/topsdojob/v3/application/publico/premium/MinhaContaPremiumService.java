package br.com.topsdojob.v3.application.publico.premium;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaAtivacaoPremiumDto;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumItemRequest;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumRequest;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumResultadoDto;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCreditoMovimentoDto;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaMonetizacaoDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MinhaContaPremiumService {

    private static final int HISTORICO_LIMITE = 50;

    private final MeusAnunciosConsultaService meusAnunciosService;
    private final CreditoLedgerOperacaoService ledgerService;
    private final MovimentoCreditoRepository movimentoRepository;
    private final PremiumCatalogoService catalogoService;
    private final BeneficioPremiumRepository beneficioRepository;
    private final BeneficioPremiumOpcaoRepository opcaoRepository;
    private final GrupoAtivacaoBeneficioRepository grupoRepository;
    private final AtivacaoBeneficioRepository ativacaoRepository;
    private final BeneficioAnuncioConsultaService beneficioConsultaService;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public MinhaContaPremiumService(
            MeusAnunciosConsultaService meusAnunciosService,
            CreditoLedgerOperacaoService ledgerService,
            MovimentoCreditoRepository movimentoRepository,
            PremiumCatalogoService catalogoService,
            BeneficioPremiumRepository beneficioRepository,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            GrupoAtivacaoBeneficioRepository grupoRepository,
            AtivacaoBeneficioRepository ativacaoRepository,
            BeneficioAnuncioConsultaService beneficioConsultaService,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper) {
        this.meusAnunciosService = meusAnunciosService;
        this.ledgerService = ledgerService;
        this.movimentoRepository = movimentoRepository;
        this.catalogoService = catalogoService;
        this.beneficioRepository = beneficioRepository;
        this.opcaoRepository = opcaoRepository;
        this.grupoRepository = grupoRepository;
        this.ativacaoRepository = ativacaoRepository;
        this.beneficioConsultaService = beneficioConsultaService;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MinhaMonetizacaoDto consultar(String anuncioSlug, Authentication authentication) {
        UUID usuarioId = meusAnunciosService.usuarioAutenticado(authentication).getId();
        AnuncioEntity anuncio = anuncioSlug == null || anuncioSlug.isBlank()
                ? null
                : meusAnunciosService.anuncioDoUsuario(anuncioSlug, authentication);
        int saldo = ledgerService.consultarSaldo(usuarioId);
        List<MinhaCreditoMovimentoDto> historico = movimentoRepository
                .findByUsuarioIdOrderByCriadoEmDesc(usuarioId, org.springframework.data.domain.PageRequest.of(0, HISTORICO_LIMITE))
                .stream()
                .map(item -> new MinhaCreditoMovimentoDto(
                        item.getId(),
                        natureza(item.getTipo(), item.getDirecao()),
                        item.getQuantidade(),
                        item.getSaldoAntes(),
                        item.getSaldoDepois(),
                        item.getObservacao(),
                        item.getCriadoEm()))
                .toList();
        return new MinhaMonetizacaoDto(
                saldo,
                historico,
                catalogoService.catalogoAtivo(),
                catalogoService.pacotesAtivos(),
                anuncio == null ? ativacoesAtivasDoUsuario(usuarioId) : ativacoesAtivas(anuncio.getId()));
    }

    @Transactional
    public MinhaCompraPremiumResultadoDto comprar(
            MinhaCompraPremiumRequest request,
            String idempotencyKey,
            Authentication authentication,
            String requestId) {
        if (request == null || request.itens() == null || request.itens().isEmpty() || request.itens().size() > 10) {
            throw badRequest("itens da compra obrigatorios");
        }
        AnuncioEntity anuncio = meusAnunciosService.anuncioDoUsuario(request.anuncioSlug(), authentication);
        UUID usuarioId = meusAnunciosService.usuarioAutenticado(authentication).getId();
        if (anuncio.getStatus() != StatusAnuncio.PUBLICADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio precisa estar publicado");
        }
        String chaveGrupo = "premium-compra:" + usuarioId + ":"
                + CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
        int saldoAtual = ledgerService.bloquearEConsultarSaldo(usuarioId);
        var grupoExistente = grupoRepository.findByIdempotencyKey(chaveGrupo);
        if (grupoExistente.isPresent()) {
            return resultadoExistente(grupoExistente.get(), saldoAtual);
        }

        List<ItemCompra> itens = resolverItens(request.itens());
        validarSemDuplicidadeAtiva(anuncio.getId(), itens);
        int total = itens.stream().mapToInt(item -> item.opcao().getCustoCreditos()).sum();
        if (total <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "catalogo sem custo operacional valido");
        }
        if (saldoAtual < total) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "saldo de creditos insuficiente");
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime fimGrupo = itens.stream()
                .map(item -> agora.plusDays(item.opcao().getDuracaoDias()))
                .max(OffsetDateTime::compareTo)
                .orElseThrow();
        GrupoAtivacaoBeneficioEntity grupo = grupoRepository.save(
                GrupoAtivacaoBeneficioEntity.criarCompraComCreditos(
                        UUID.randomUUID(),
                        usuarioId,
                        anuncio.getId(),
                        agora,
                        fimGrupo,
                        chaveGrupo,
                        agora));

        int saldoCorrente = saldoAtual;
        List<AtivacaoBeneficioEntity> ativacoes = new ArrayList<>();
        for (int indice = 0; indice < itens.size(); indice++) {
            ItemCompra item = itens.get(indice);
            AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(
                    AtivacaoBeneficioEntity.criarCompraComCreditos(
                            UUID.randomUUID(),
                            item.beneficio().getId(),
                            item.opcao().getId(),
                            usuarioId,
                            anuncio.getId(),
                            grupo.getId(),
                            agora,
                            agora.plusDays(item.opcao().getDuracaoDias()),
                            item.opcao().getCustoCreditos(),
                            chaveGrupo + ":ativacao:" + indice,
                            agora));
            var lancamento = ledgerService.registrar(
                    usuarioId,
                    TipoMovimentoCredito.SAIDA,
                    DirecaoMovimentoCredito.DEBITO,
                    item.opcao().getCustoCreditos(),
                    saldoCorrente,
                    OrigemMovimentoCredito.BENEFICIO,
                    "ATIVACAO_BENEFICIO",
                    ativacao.getId(),
                    chaveGrupo + ":debito:" + indice,
                    usuarioId,
                    "Compra de " + item.beneficio().getNome() + " por " + item.opcao().getDuracaoDias() + " dias",
                    requestId);
            saldoCorrente = lancamento.movimento().getSaldoDepois();
            ativacoes.add(ativacao);
        }
        auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
                UUID.randomUUID(),
                usuarioId,
                "PREMIUM_COMPRA_CREDITOS",
                "GRUPO_ATIVACAO_BENEFICIO",
                grupo.getId(),
                json(Map.of("saldo", saldoAtual)),
                json(Map.of("saldo", saldoCorrente, "totalDebitado", total, "quantidadeBeneficios", ativacoes.size())),
                requestId,
                agora));
        return new MinhaCompraPremiumResultadoDto(
                grupo.getId(),
                saldoAtual,
                saldoCorrente,
                total,
                ativacoesDto(ativacoes),
                false);
    }

    private List<ItemCompra> resolverItens(List<MinhaCompraPremiumItemRequest> requests) {
        Set<String> codigos = new HashSet<>();
        List<ItemCompra> itens = new ArrayList<>();
        for (MinhaCompraPremiumItemRequest request : requests) {
            String codigo = request == null || request.beneficioCodigo() == null
                    ? ""
                    : request.beneficioCodigo().trim().toUpperCase();
            if (!codigo.matches("[A-Z0-9_]{3,80}") || !codigos.add(codigo) || request.duracaoDias() == null) {
                throw badRequest("beneficio da compra invalido ou duplicado");
            }
            BeneficioPremiumEntity beneficio = beneficioRepository.findByCodigo(codigo)
                    .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "beneficio nao encontrado"));
            BeneficioPremiumOpcaoEntity opcao = opcaoRepository
                    .findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(
                            beneficio.getId(),
                            request.duracaoDias())
                    .filter(item -> item.vigente(OffsetDateTime.now(ZoneOffset.UTC)))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "duracao nao encontrada"));
            itens.add(new ItemCompra(beneficio, opcao));
        }
        return itens;
    }

    private void validarSemDuplicidadeAtiva(UUID anuncioId, List<ItemCompra> itens) {
        Set<UUID> idsAtivos = beneficioConsultaService.consultarCalculados(anuncioId).stream()
                .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO)
                .filter(item -> item.beneficio() != null)
                .map(item -> item.beneficio().getId())
                .collect(Collectors.toSet());
        if (itens.stream().anyMatch(item -> idsAtivos.contains(item.beneficio().getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "beneficio ja ativo no anuncio");
        }
    }

    private MinhaCompraPremiumResultadoDto resultadoExistente(
            GrupoAtivacaoBeneficioEntity grupo,
            int saldoAtual) {
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
        List<br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity> debitos = movimentoRepository
                .findByReferenciaTipoAndReferenciaIdIn(
                        "ATIVACAO_BENEFICIO",
                        ativacoes.stream().map(AtivacaoBeneficioEntity::getId).toList())
                .stream()
                .filter(item -> item.getDirecao() == DirecaoMovimentoCredito.DEBITO)
                .sorted(java.util.Comparator.comparing(
                        br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity::getCriadoEm))
                .toList();
        int total = debitos.stream().mapToInt(item -> valor(item.getQuantidade())).sum();
        int saldoAnterior = debitos.isEmpty() ? saldoAtual : valor(debitos.get(0).getSaldoAntes());
        int saldoPosterior = debitos.isEmpty()
                ? saldoAtual
                : valor(debitos.get(debitos.size() - 1).getSaldoDepois());
        return new MinhaCompraPremiumResultadoDto(
                grupo.getId(),
                saldoAnterior,
                saldoPosterior,
                total,
                ativacoesDto(ativacoes),
                true);
    }

    private List<MinhaAtivacaoPremiumDto> ativacoesAtivasDoUsuario(UUID usuarioId) {
        return ativacoesDto(ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .filter(item -> item.getStatus() == StatusAtivacaoBeneficio.ATIVA)
                .filter(item -> item.getFimEm() != null && item.getFimEm().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                .toList());
    }

    private List<MinhaAtivacaoPremiumDto> ativacoesAtivas(UUID anuncioId) {
        List<AtivacaoBeneficioEntity> ativas = ativacaoRepository.findByAnuncioId(anuncioId).stream()
                .filter(item -> item.getStatus() == StatusAtivacaoBeneficio.ATIVA)
                .filter(item -> item.getFimEm() != null && item.getFimEm().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                .toList();
        return ativacoesDto(ativas);
    }

    private List<MinhaAtivacaoPremiumDto> ativacoesDto(List<AtivacaoBeneficioEntity> ativacoes) {
        if (ativacoes.isEmpty()) return List.of();
        Map<UUID, BeneficioPremiumEntity> beneficios = beneficioRepository.findByIdIn(ativacoes.stream()
                        .map(AtivacaoBeneficioEntity::getBeneficioId)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(BeneficioPremiumEntity::getId, Function.identity()));
        return ativacoes.stream()
                .map(item -> {
                    BeneficioPremiumEntity beneficio = beneficios.get(item.getBeneficioId());
                    return new MinhaAtivacaoPremiumDto(
                            item.getId(),
                            beneficio == null ? null : beneficio.getCodigo(),
                            beneficio == null ? null : beneficio.getNome(),
                            item.getStatus() == null ? null : item.getStatus().name(),
                            valor(item.getCustoCreditosSnapshot()),
                            item.getInicioEm(),
                            item.getFimEm());
                })
                .toList();
    }

    private String natureza(
            br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito tipo,
            DirecaoMovimentoCredito direcao) {
        if (tipo == TipoMovimentoCredito.MIGRACAO_SALDO_INICIAL) return "MIGRACAO_SALDO_INICIAL";
        if (tipo == TipoMovimentoCredito.ESTORNO) return "ESTORNO";
        if (tipo == TipoMovimentoCredito.AJUSTE) {
            return direcao == DirecaoMovimentoCredito.CREDITO
                    ? "AJUSTE_ADMIN_POSITIVO"
                    : "AJUSTE_ADMIN_NEGATIVO";
        }
        return direcao == DirecaoMovimentoCredito.CREDITO ? "CREDITO" : "DEBITO";
    }

    private int valor(Integer value) {
        return value == null ? 0 : value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria", exception);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ItemCompra(
            BeneficioPremiumEntity beneficio,
            BeneficioPremiumOpcaoEntity opcao) {
    }
}
