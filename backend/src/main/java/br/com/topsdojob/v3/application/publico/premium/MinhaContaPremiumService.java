package br.com.topsdojob.v3.application.publico.premium;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
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
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final AnuncioRepository anuncioRepository;
    private final BeneficioAnuncioConsultaService beneficioConsultaService;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;
    private final ArquivoPublicidadeRegistroService arquivoPublicidade;

    public MinhaContaPremiumService(
            MeusAnunciosConsultaService meusAnunciosService,
            CreditoLedgerOperacaoService ledgerService,
            MovimentoCreditoRepository movimentoRepository,
            PremiumCatalogoService catalogoService,
            BeneficioPremiumRepository beneficioRepository,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            GrupoAtivacaoBeneficioRepository grupoRepository,
            AtivacaoBeneficioRepository ativacaoRepository,
            AnuncioRepository anuncioRepository,
            BeneficioAnuncioConsultaService beneficioConsultaService,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper,
            ArquivoPublicidadeRegistroService arquivoPublicidade) {
        this.meusAnunciosService = meusAnunciosService;
        this.ledgerService = ledgerService;
        this.movimentoRepository = movimentoRepository;
        this.catalogoService = catalogoService;
        this.beneficioRepository = beneficioRepository;
        this.opcaoRepository = opcaoRepository;
        this.grupoRepository = grupoRepository;
        this.ativacaoRepository = ativacaoRepository;
        this.anuncioRepository = anuncioRepository;
        this.beneficioConsultaService = beneficioConsultaService;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
        this.arquivoPublicidade = arquivoPublicidade;
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
                catalogoService.catalogoAtivo().stream()
                        .filter(item -> PremiumBeneficioCodigo.TODOS.contains(item.codigo()))
                        .toList(),
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
        List<ItemSolicitado> solicitados = normalizarItens(request.itens());
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
            validarRetry(grupoExistente.get(), anuncio, solicitados);
            return resultadoExistente(grupoExistente.get(), saldoAtual);
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<ItemCompra> itens = resolverItens(solicitados, agora);
        validarSemDuplicidadeAtiva(anuncio.getId(), itens);
        int total = itens.stream().mapToInt(item -> item.opcao().getCustoCreditos()).sum();
        if (saldoAtual < total) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "saldo de creditos insuficiente");
        }

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
            boolean aguardaUso = PremiumBeneficioCodigo.FOTOS_EXTRA_5.equals(
                    item.beneficio().getCodigo());
            AtivacaoBeneficioEntity novaAtivacao = aguardaUso
                    ? AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
                            UUID.randomUUID(),
                            item.beneficio().getId(),
                            item.opcao().getId(),
                            usuarioId,
                            anuncio.getId(),
                            grupo.getId(),
                            item.opcao().getCustoCreditos(),
                            chaveGrupo + ":ativacao:" + indice,
                            agora)
                    : AtivacaoBeneficioEntity.criarCompraComCreditos(
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
                            agora);
            AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(novaAtivacao);
            if (item.opcao().getCustoCreditos() > 0) {
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
                        "Compra de " + item.beneficio().getNome()
                                + " por " + item.opcao().getDuracaoDias()
                                + " dias no anuncio " + anuncio.getTitulo(),
                        requestId);
                saldoCorrente = lancamento.movimento().getSaldoDepois();
            }
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
        arquivoPublicidade.registrarEstado(anuncio.getId(), "PREMIUM_COMPRA_CREDITOS", requestId, agora);
        return new MinhaCompraPremiumResultadoDto(
                grupo.getId(),
                saldoAtual,
                saldoCorrente,
                total,
                ativacoesDto(ativacoes),
                false);
    }

    private List<ItemSolicitado> normalizarItens(List<MinhaCompraPremiumItemRequest> requests) {
        Set<String> codigos = new HashSet<>();
        List<ItemSolicitado> itens = new ArrayList<>();
        for (MinhaCompraPremiumItemRequest request : requests) {
            String codigo = request == null || request.beneficioCodigo() == null
                    ? ""
                    : request.beneficioCodigo().trim().toUpperCase();
            if (PremiumBeneficioCodigo.STORIES.equals(codigo)) {
                throw badRequest("Stories utiliza o fluxo proprio de publicacao");
            }
            Integer duracaoDias = request == null ? null : request.duracaoDias();

            if (!codigo.matches("[A-Z0-9_]{3,80}")
                    || !PremiumBeneficioCodigo.TODOS.contains(codigo)
                    || !codigos.add(codigo)
                    || duracaoDias == null
                    || duracaoDias <= 0) {
                throw badRequest("beneficio da compra invalido ou duplicado");
            }
            itens.add(new ItemSolicitado(codigo, duracaoDias));
        }
        return itens;
    }

    private List<ItemCompra> resolverItens(List<ItemSolicitado> requests, OffsetDateTime agora) {
        List<ItemCompra> itens = new ArrayList<>();
        for (ItemSolicitado request : requests) {
            BeneficioPremiumEntity beneficio = beneficioRepository.findByCodigo(request.codigo())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "beneficio nao encontrado"));
            if (!Boolean.TRUE.equals(beneficio.getAtivo())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "beneficio nao encontrado");
            }
            BeneficioPremiumOpcaoEntity opcao = opcaoRepository
                    .findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(
                            beneficio.getId(),
                            request.duracaoDias())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "duracao nao encontrada"));
            if (!opcao.vigente(agora)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "duracao nao encontrada");
            }
            Integer custoCreditos = opcao.getCustoCreditos();
            if (custoCreditos == null || custoCreditos <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "catalogo sem custo operacional valido");
            }
            itens.add(new ItemCompra(beneficio, opcao));
        }
        return itens;
    }

    private void validarRetry(
            GrupoAtivacaoBeneficioEntity grupo,
            AnuncioEntity anuncio,
            List<ItemSolicitado> solicitados) {
        if (!Objects.equals(grupo.getAnuncioId(), anuncio.getId())) {
            throw idempotenciaDivergente();
        }
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
        if (ativacoes.size() != solicitados.size()) {
            throw idempotenciaDivergente();
        }
        Map<UUID, BeneficioPremiumEntity> beneficios = beneficioRepository
                .findByIdIn(ativacoes.stream()
                        .map(AtivacaoBeneficioEntity::getBeneficioId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(BeneficioPremiumEntity::getId, Function.identity()));
        Map<UUID, BeneficioPremiumOpcaoEntity> opcoes = opcaoRepository
                .findAllById(ativacoes.stream()
                        .map(AtivacaoBeneficioEntity::getOpcaoId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(BeneficioPremiumOpcaoEntity::getId, Function.identity()));
        Map<String, Integer> existentes = new LinkedHashMap<>();
        Map<String, ItemSolicitado> solicitadosPorCodigo = solicitados.stream().collect(Collectors.toMap(
                ItemSolicitado::codigo,
                Function.identity()));
        for (AtivacaoBeneficioEntity ativacao : ativacoes) {
            BeneficioPremiumEntity beneficio = beneficios.get(ativacao.getBeneficioId());
            BeneficioPremiumOpcaoEntity opcao = opcoes.get(ativacao.getOpcaoId());
            if (beneficio == null
                    || opcao == null
                    || existentes.put(beneficio.getCodigo(), opcao.getDuracaoDias()) != null) {
                throw idempotenciaDivergente();
            }
            ItemSolicitado recebido = solicitadosPorCodigo.get(beneficio.getCodigo());
            if (recebido == null) {
                throw idempotenciaDivergente();
            }
        }
        Map<String, Integer> recebidos = solicitados.stream().collect(Collectors.toMap(
                ItemSolicitado::codigo,
                ItemSolicitado::duracaoDias,
                (primeiro, ignorado) -> primeiro,
                LinkedHashMap::new));
        if (!existentes.equals(recebidos)) {
            throw idempotenciaDivergente();
        }
    }

    private void validarSemDuplicidadeAtiva(UUID anuncioId, List<ItemCompra> itens) {
        Set<UUID> idsAtivos = beneficioConsultaService.consultarCalculados(anuncioId).stream()
                .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO
                        || item.status() == PremiumBeneficioStatusCalculado.PENDENTE)
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
        Map<UUID, Integer> custosCobraveis = ativacoes.stream()
                .filter(item -> valor(item.getCustoCreditosSnapshot()) > 0)
                .collect(Collectors.toMap(
                        AtivacaoBeneficioEntity::getId,
                        item -> valor(item.getCustoCreditosSnapshot())));
        List<br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity> debitos = custosCobraveis.isEmpty()
                ? List.of()
                : movimentoRepository
                        .findByReferenciaTipoAndReferenciaIdIn(
                                "ATIVACAO_BENEFICIO",
                                custosCobraveis.keySet().stream().toList())
                        .stream()
                        .filter(item -> item.getDirecao() == DirecaoMovimentoCredito.DEBITO)
                        .sorted(java.util.Comparator.comparing(
                                br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity::getCriadoEm))
                        .toList();
        Set<UUID> referenciasDebito = debitos.stream()
                .map(br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity::getReferenciaId)
                .collect(Collectors.toSet());
        boolean debitoInconsistente = debitos.stream().anyMatch(item ->
                !Objects.equals(custosCobraveis.get(item.getReferenciaId()), item.getQuantidade()));
        if (ativacoes.isEmpty()
                || debitos.size() != custosCobraveis.size()
                || referenciasDebito.size() != custosCobraveis.size()
                || debitoInconsistente) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "operacao idempotente possui ledger ou ativacoes inconsistentes");
        }
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
        return ativacoesAtivas(ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId));
    }

    private List<MinhaAtivacaoPremiumDto> ativacoesAtivas(UUID anuncioId) {
        return ativacoesAtivas(ativacaoRepository.findByAnuncioId(anuncioId));
    }

    private List<MinhaAtivacaoPremiumDto> ativacoesAtivas(List<AtivacaoBeneficioEntity> ativacoes) {
        List<PremiumBeneficioCalculado> ativas = beneficioConsultaService
                .calcular(ativacoes, OffsetDateTime.now(ZoneOffset.UTC)).stream()
                .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO
                        || item.status() == PremiumBeneficioStatusCalculado.PENDENTE)
                .toList();
        Map<UUID, String> statusCalculado = ativas.stream().collect(Collectors.toMap(
                item -> item.ativacao().getId(),
                item -> item.status().name()));
        return ativacoesDto(ativas.stream().map(PremiumBeneficioCalculado::ativacao).toList(), statusCalculado);
    }

    private List<MinhaAtivacaoPremiumDto> ativacoesDto(List<AtivacaoBeneficioEntity> ativacoes) {
        return ativacoesDto(ativacoes, Map.of());
    }

    private List<MinhaAtivacaoPremiumDto> ativacoesDto(
            List<AtivacaoBeneficioEntity> ativacoes,
            Map<UUID, String> statusCalculado) {
        if (ativacoes.isEmpty()) return List.of();
        Map<UUID, BeneficioPremiumEntity> beneficios = beneficioRepository.findByIdIn(ativacoes.stream()
                        .map(AtivacaoBeneficioEntity::getBeneficioId)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(BeneficioPremiumEntity::getId, Function.identity()));
        Map<UUID, BeneficioPremiumOpcaoEntity> opcoes = opcaoRepository.findAllById(ativacoes.stream()
                        .map(AtivacaoBeneficioEntity::getOpcaoId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(BeneficioPremiumOpcaoEntity::getId, Function.identity()));
        Map<UUID, AnuncioEntity> anuncios = anuncioRepository.findAllById(ativacoes.stream()
                        .map(AtivacaoBeneficioEntity::getAnuncioId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        return ativacoes.stream()
                .sorted(Comparator.comparing(AtivacaoBeneficioEntity::getCriadoEm).reversed())
                .map(item -> {
                    BeneficioPremiumEntity beneficio = beneficios.get(item.getBeneficioId());
                    BeneficioPremiumOpcaoEntity opcao = opcoes.get(item.getOpcaoId());
                    AnuncioEntity anuncio = anuncios.get(item.getAnuncioId());
                    String statusInterno = statusCalculado.getOrDefault(
                            item.getId(),
                            item.getStatus() == null ? null : item.getStatus().name());
                    String status = statusPublico(item, beneficio, statusInterno);
                    return new MinhaAtivacaoPremiumDto(
                            item.getId(),
                            item.getAnuncioId(),
                            anuncio == null ? null : anuncio.getSlug(),
                            anuncio == null ? null : anuncio.getTitulo(),
                            beneficio == null ? null : beneficio.getCodigo(),
                            beneficio == null ? null : beneficio.getNome(),
                            status,
                            valor(item.getCustoCreditosSnapshot()),
                            duracaoDias(item, opcao),
                            item.getInicioEm(),
                            item.getFimEm(),
                            beneficio == null ? null : efeitoPublico(beneficio.getCodigo()),
                            motivoIneficacia(anuncio, item, status,
                                    beneficio == null ? null : beneficio.getCodigo()));
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

    private int duracaoDias(
            AtivacaoBeneficioEntity ativacao,
            BeneficioPremiumOpcaoEntity opcao) {
        if (opcao != null && opcao.getDuracaoDias() != null) {
            return opcao.getDuracaoDias();
        }
        if (ativacao.getInicioEm() == null || ativacao.getFimEm() == null) {
            return 0;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(
                ativacao.getInicioEm(),
                ativacao.getFimEm()));
    }

    private String efeitoPublico(String codigo) {
        return switch (codigo) {
            case PremiumBeneficioCodigo.ANUNCIO_TOPO -> "Prioridade nas listagens publicas";
            case PremiumBeneficioCodigo.WHATSAPP_CARD -> "WhatsApp no card sujeito a verificacao etaria";
            case PremiumBeneficioCodigo.OCULTAR_IDADE -> "Idade ocultada nas superficies publicas";
            case PremiumBeneficioCodigo.FOTOS_EXTRA_5 -> "Limite ampliado para ate dez fotos";
            case PremiumBeneficioCodigo.CARROSSEL_FOTOS -> "Carrossel habilitado nas fotos publicas";
            case PremiumBeneficioCodigo.VIDEO_1 -> "Video aprovado habilitado no anuncio";
            case PremiumBeneficioCodigo.STORIES -> "Publicacao de um Story vinculada ao anuncio";
            default -> "Efeito definido pelo catalogo Premium";
        };
    }

    private String statusPublico(
            AtivacaoBeneficioEntity ativacao,
            BeneficioPremiumEntity beneficio,
            String status) {
        return beneficio != null
                && PremiumBeneficioCodigo.STORIES.equals(beneficio.getCodigo())
                && ativacao.getStatus() == StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO
                ? "DISPONIVEL_PARA_PUBLICAR" : status;
    }

    private String motivoIneficacia(
            AnuncioEntity anuncio,
            AtivacaoBeneficioEntity ativacao,
            String status,
            String beneficioCodigo) {
        if (anuncio == null) {
            return "ANUNCIO_NAO_ENCONTRADO";
        }
        if (ativacao.getStatus() == StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO) {
            return PremiumBeneficioCodigo.STORIES.equals(beneficioCodigo)
                    ? "AGUARDANDO_PUBLICACAO_STORY"
                    : "AGUARDANDO_APROVACAO_MODERACAO";
        }
        if (!"ATIVO".equals(status) && !"ATIVA".equals(status) && !"VENCENDO".equals(status)) {
            return "ATIVACAO_NAO_VIGENTE";
        }
        return anuncio.getStatus() == StatusAnuncio.PUBLICADO
                ? null
                : "ANUNCIO_NAO_PUBLICADO";
    }

    private ResponseStatusException idempotenciaDivergente() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "chave de idempotencia reutilizada com compra diferente");
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

    private record ItemSolicitado(
            String codigo,
            int duracaoDias) {
    }
}
