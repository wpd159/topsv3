package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoLoteDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoOperacaoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarLoteItemRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarLoteRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarRequest;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPremiumOperacaoService {

    private final AtivacaoBeneficioRepository ativacaoRepository;
    private final MovimentoCreditoRepository movimentoRepository;
    private final AdminCreditoOperacaoService creditoService;
    private final BeneficioPremiumRepository beneficioRepository;
    private final BeneficioPremiumOpcaoRepository opcaoRepository;
    private final GrupoAtivacaoBeneficioRepository grupoRepository;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository;
    private final BeneficioAnuncioConsultaService beneficioConsultaService;

    public AdminPremiumOperacaoService(
            AtivacaoBeneficioRepository ativacaoRepository,
            MovimentoCreditoRepository movimentoRepository,
            AdminCreditoOperacaoService creditoService,
            BeneficioPremiumRepository beneficioRepository,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            GrupoAtivacaoBeneficioRepository grupoRepository,
            AnuncioRepository anuncioRepository,
            AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository,
            BeneficioAnuncioConsultaService beneficioConsultaService) {
        this.ativacaoRepository = ativacaoRepository;
        this.movimentoRepository = movimentoRepository;
        this.creditoService = creditoService;
        this.beneficioRepository = beneficioRepository;
        this.opcaoRepository = opcaoRepository;
        this.grupoRepository = grupoRepository;
        this.anuncioRepository = anuncioRepository;
        this.bloqueioJuridicoRepository = bloqueioJuridicoRepository;
        this.beneficioConsultaService = beneficioConsultaService;
    }

    @Transactional(readOnly = true)
    public List<AdminPremiumAtivacaoDto> listar(UUID usuarioId) {
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
        if (ativacoes.isEmpty()) return List.of();
        Map<UUID, BeneficioPremiumEntity> beneficios = beneficioRepository.findByIdIn(ativacoes.stream()
                        .map(AtivacaoBeneficioEntity::getBeneficioId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(BeneficioPremiumEntity::getId, Function.identity()));
        return ativacoes.stream().map(item -> {
            BeneficioPremiumEntity beneficio = beneficios.get(item.getBeneficioId());
            return new AdminPremiumAtivacaoDto(
                    item.getId(),
                    item.getUsuarioId(),
                    item.getAnuncioId(),
                    beneficio == null ? null : beneficio.getCodigo(),
                    beneficio == null ? null : beneficio.getNome(),
                    item.getOrigem() == null ? null : item.getOrigem().name(),
                    item.getStatus() == null ? null : item.getStatus().name(),
                    valor(item.getCustoCreditosSnapshot()),
                    item.getInicioEm(),
                    item.getFimEm());
        }).toList();
    }

    @Transactional
    public AdminPremiumAtivacaoOperacaoDto ativarManual(
            UUID anuncioId,
            AdminPremiumAtivarRequest request,
            String idempotencyKey,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        if (request == null || request.beneficioId() == null || request.duracaoDias() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beneficio e duracao obrigatorios");
        }
        String observacao = observacaoOpcional(request.observacao());
        String chave = "premium-admin:" + administrador.usuarioId() + ":"
                + CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
        var repetido = grupoRepository.findByIdempotencyKey(chave);
        if (repetido.isPresent()) {
            return resultadoRepetido(repetido.get(), anuncioId, request, observacao);
        }

        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .filter(item -> item.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        validarElegibilidadeJuridica(anuncio);
        var repetidoAposLock = grupoRepository.findByIdempotencyKey(chave);
        if (repetidoAposLock.isPresent()) {
            return resultadoRepetido(repetidoAposLock.get(), anuncioId, request, observacao);
        }

        BeneficioPremiumEntity beneficio = beneficioAdministravel(request.beneficioId());
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        BeneficioPremiumOpcaoEntity opcao = opcaoRepository
                .findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(
                        beneficio.getId(), request.duracaoDias())
                .filter(item -> item.vigente(agora))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "duracao nao encontrada"));
        boolean duplicadoAtivo = beneficioConsultaService.consultarCalculados(anuncioId).stream()
                .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO
                        || item.status() == PremiumBeneficioStatusCalculado.PENDENTE)
                .anyMatch(item -> item.beneficio() != null && item.beneficio().getId().equals(beneficio.getId()));
        if (duplicadoAtivo) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "beneficio ja ativo no anuncio");
        }

        OffsetDateTime fim = agora.plusDays(opcao.getDuracaoDias());
        GrupoAtivacaoBeneficioEntity grupo = grupoRepository.save(
                GrupoAtivacaoBeneficioEntity.criarAdministrativa(
                        UUID.randomUUID(),
                        anuncio.getUsuarioId(),
                        anuncioId,
                        administrador.usuarioId(),
                        agora,
                        fim,
                        chave,
                        observacao,
                        agora));
        AtivacaoBeneficioEntity novaAtivacao = aguardaUso(beneficio.getCodigo())
                ? AtivacaoBeneficioEntity.criarAdministrativaAguardandoModeracao(
                        UUID.randomUUID(),
                        beneficio.getId(),
                        opcao.getId(),
                        anuncio.getUsuarioId(),
                        anuncioId,
                        grupo.getId(),
                        administrador.usuarioId(),
                        chave + ":ativacao",
                        agora)
                : AtivacaoBeneficioEntity.criarAdministrativa(
                        UUID.randomUUID(),
                        beneficio.getId(),
                        opcao.getId(),
                        anuncio.getUsuarioId(),
                        anuncioId,
                        grupo.getId(),
                        administrador.usuarioId(),
                        agora,
                        fim,
                        chave + ":ativacao",
                        agora);
        AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(novaAtivacao);
        creditoService.auditar(
                administrador.usuarioId(),
                "PREMIUM_ATIVACAO_ADMINISTRATIVA",
                "ATIVACAO_BENEFICIO",
                ativacao.getId(),
                Map.of("status", "INEXISTENTE"),
                Map.of(
                        "status", ativacao.getStatus().name(),
                        "anuncioId", anuncioId,
                        "beneficioCodigo", beneficio.getCodigo(),
                        "duracaoDias", opcao.getDuracaoDias(),
                        "creditosDebitados", 0,
                        "observacaoRegistrada", observacao != null),
                requestId);
        return toDto(ativacao, 0, false);
    }

    @Transactional
    public AdminPremiumAtivacaoLoteDto ativarManualLote(
            UUID anuncioId,
            AdminPremiumAtivarLoteRequest request,
            String idempotencyKey,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        List<AdminPremiumAtivarLoteItemRequest> itens = validarLote(request);
        String observacao = observacaoOpcional(request.observacao());
        String chaveRaiz = "premium-admin-lote:" + administrador.usuarioId() + ":"
                + CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
        Map<UUID, GrupoAtivacaoBeneficioEntity> repetidos = localizarGruposDoLote(itens, chaveRaiz);
        if (!repetidos.isEmpty()) {
            return resultadoLoteRepetido(repetidos, anuncioId, itens, observacao);
        }

        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .filter(item -> item.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        validarElegibilidadeJuridica(anuncio);
        Map<UUID, GrupoAtivacaoBeneficioEntity> repetidosAposLock = localizarGruposDoLote(itens, chaveRaiz);
        if (!repetidosAposLock.isEmpty()) {
            return resultadoLoteRepetido(repetidosAposLock, anuncioId, itens, observacao);
        }

        Map<UUID, BeneficioPremiumEntity> beneficios = new LinkedHashMap<>();
        for (AdminPremiumAtivarLoteItemRequest item : itens) {
            beneficios.computeIfAbsent(item.beneficioId(), this::beneficioAdministravel);
        }
        Set<UUID> ativos = beneficioConsultaService.consultarCalculados(anuncioId).stream()
                .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO
                        || item.status() == PremiumBeneficioStatusCalculado.PENDENTE)
                .map(PremiumBeneficioCalculado::beneficio)
                .filter(Objects::nonNull)
                .map(BeneficioPremiumEntity::getId)
                .collect(Collectors.toSet());
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<AdminPremiumAtivacaoOperacaoDto> resultado = new java.util.ArrayList<>();
        for (AdminPremiumAtivarLoteItemRequest item : itens) {
            BeneficioPremiumEntity beneficio = beneficios.get(item.beneficioId());

            if (ativos.contains(beneficio.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "beneficio ja ativo no anuncio");
            }
            BeneficioPremiumOpcaoEntity opcao = opcaoRepository
                    .findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(
                            beneficio.getId(), item.duracaoDias())
                    .filter(catalogo -> catalogo.vigente(agora))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "duracao nao encontrada"));
            String chaveItem = chaveRaiz + ":" + beneficio.getId();
            OffsetDateTime fim = agora.plusDays(opcao.getDuracaoDias());
            GrupoAtivacaoBeneficioEntity grupo = grupoRepository.save(
                    GrupoAtivacaoBeneficioEntity.criarAdministrativa(
                            UUID.randomUUID(),
                            anuncio.getUsuarioId(),
                            anuncioId,
                            administrador.usuarioId(),
                            agora,
                            fim,
                            chaveItem,
                            observacao,
                            agora));
            AtivacaoBeneficioEntity novaAtivacao = aguardaUso(beneficio.getCodigo())
                    ? AtivacaoBeneficioEntity.criarAdministrativaAguardandoModeracao(
                            UUID.randomUUID(),
                            beneficio.getId(),
                            opcao.getId(),
                            anuncio.getUsuarioId(),
                            anuncioId,
                            grupo.getId(),
                            administrador.usuarioId(),
                            chaveItem + ":ativacao",
                            agora)
                    : AtivacaoBeneficioEntity.criarAdministrativa(
                            UUID.randomUUID(),
                            beneficio.getId(),
                            opcao.getId(),
                            anuncio.getUsuarioId(),
                            anuncioId,
                            grupo.getId(),
                            administrador.usuarioId(),
                            agora,
                            fim,
                            chaveItem + ":ativacao",
                            agora);
            AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(novaAtivacao);
            creditoService.auditar(
                    administrador.usuarioId(),
                    "PREMIUM_ATIVACAO_ADMINISTRATIVA",
                    "ATIVACAO_BENEFICIO",
                    ativacao.getId(),
                    Map.of("status", "INEXISTENTE"),
                    Map.of(
                            "status", ativacao.getStatus().name(),
                            "anuncioId", anuncioId,
                            "beneficioCodigo", beneficio.getCodigo(),
                            "duracaoDias", opcao.getDuracaoDias(),
                            "creditosDebitados", 0,
                            "observacaoRegistrada", observacao != null),
                    requestId);
            resultado.add(toDto(ativacao, 0, false));
        }
        return new AdminPremiumAtivacaoLoteDto(List.copyOf(resultado), false);
    }

    private BeneficioPremiumEntity beneficioAdministravel(UUID beneficioId) {
        BeneficioPremiumEntity beneficio = beneficioRepository.findById(beneficioId)
                .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                .filter(item -> item.getEscopo() == EscopoBeneficioPremium.ANUNCIO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "beneficio nao encontrado"));
        if (PremiumBeneficioCodigo.STORIES.equals(beneficio.getCodigo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stories utiliza configuracao e ativacao proprias");
        }
        return beneficio;
    }

    private boolean aguardaUso(String codigo) {
        return PremiumBeneficioCodigo.FOTOS_EXTRA_5.equals(codigo);
    }

    @Transactional
    public AdminPremiumAtivacaoOperacaoDto cancelar(
            UUID ativacaoId,
            String motivo,
            String idempotencyKey,
            AdminUserPrincipal administrador,
            String requestId) {
        if (administrador == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
        String chaveOperacional = CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
        String motivoSeguro = motivo == null ? "" : motivo.trim();
        if (motivoSeguro.length() < 5 || motivoSeguro.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo obrigatorio");
        }
        AtivacaoBeneficioEntity ativacao = ativacaoRepository.findByIdForUpdate(ativacaoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ativacao nao encontrada"));
        if (ativacao.getStatus() == StatusAtivacaoBeneficio.REVOGADA
                || ativacao.getStatus() == StatusAtivacaoBeneficio.CANCELADA) {
            return toDto(ativacao, 0, true);
        }
        if (ativacao.getStatus() != StatusAtivacaoBeneficio.ATIVA
                && ativacao.getStatus() != StatusAtivacaoBeneficio.AGENDADA
                && ativacao.getStatus() != StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ativacao nao pode ser cancelada");
        }
        int estornado = 0;
        if (ativacao.getOrigem() == OrigemBeneficio.CREDITO && valor(ativacao.getCustoCreditosSnapshot()) > 0) {
            var debito = movimentoRepository
                    .findFirstByReferenciaTipoAndReferenciaIdAndDirecaoOrderByCriadoEmAsc(
                            "ATIVACAO_BENEFICIO",
                            ativacaoId,
                            DirecaoMovimentoCredito.DEBITO)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "ativacao sem debito de origem auditavel"));
            creditoService.estornar(
                    debito.getId(),
                    motivoSeguro,
                    chaveOperacional + ":credito",
                    administrador,
                    requestId);
            estornado = debito.getQuantidade();
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String statusAntes = ativacao.getStatus().name();
        ativacao.revogar(motivoSeguro, agora);
        ativacaoRepository.save(ativacao);
        creditoService.auditar(
                administrador.usuarioId(),
                "PREMIUM_ATIVACAO_CANCELAR",
                "ATIVACAO_BENEFICIO",
                ativacaoId,
                Map.of("status", statusAntes, "creditos", valor(ativacao.getCustoCreditosSnapshot())),
                Map.of("status", ativacao.getStatus().name(), "creditosEstornados", estornado),
                requestId);
        return toDto(ativacao, estornado, false);
    }

    private AdminPremiumAtivacaoOperacaoDto resultadoRepetido(
            GrupoAtivacaoBeneficioEntity grupo,
            UUID anuncioId,
            AdminPremiumAtivarRequest request,
            String observacao) {
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
        AtivacaoBeneficioEntity ativacao = ativacoes.size() == 1 ? ativacoes.get(0) : null;
        long duracao = grupo.getValidadeInicioEm() == null || grupo.getValidadeFimEm() == null
                ? -1
                : java.time.Duration.between(grupo.getValidadeInicioEm(), grupo.getValidadeFimEm()).toDays();
        if (ativacao == null
                || !anuncioId.equals(grupo.getAnuncioId())
                || !request.beneficioId().equals(ativacao.getBeneficioId())
                || duracao != request.duracaoDias()
                || !Objects.equals(observacao, grupo.getObservacao())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outra operacao");
        }
        return toDto(ativacao, 0, true);
    }

    private List<AdminPremiumAtivarLoteItemRequest> validarLote(AdminPremiumAtivarLoteRequest request) {
        if (request == null || request.beneficios() == null
                || request.beneficios().isEmpty() || request.beneficios().size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selecione de um a vinte beneficios");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (AdminPremiumAtivarLoteItemRequest item : request.beneficios()) {
            if (item == null || item.beneficioId() == null || item.duracaoDias() == null
                    || item.duracaoDias() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beneficio e duracao obrigatorios");
            }
            if (!ids.add(item.beneficioId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beneficio duplicado no lote");
            }
        }
        return List.copyOf(request.beneficios());
    }

    private String observacaoOpcional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String observacao = value.trim();
        if (observacao.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "observacao excede 500 caracteres");
        }
        return observacao;
    }

    private Map<UUID, GrupoAtivacaoBeneficioEntity> localizarGruposDoLote(
            List<AdminPremiumAtivarLoteItemRequest> itens,
            String chaveRaiz) {
        Map<UUID, GrupoAtivacaoBeneficioEntity> encontrados = new LinkedHashMap<>();
        for (AdminPremiumAtivarLoteItemRequest item : itens) {
            grupoRepository.findByIdempotencyKey(chaveRaiz + ":" + item.beneficioId())
                    .ifPresent(grupo -> encontrados.put(item.beneficioId(), grupo));
        }
        if (!encontrados.isEmpty() && encontrados.size() != itens.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "lote idempotente incompleto");
        }
        return encontrados;
    }

    private AdminPremiumAtivacaoLoteDto resultadoLoteRepetido(
            Map<UUID, GrupoAtivacaoBeneficioEntity> grupos,
            UUID anuncioId,
            List<AdminPremiumAtivarLoteItemRequest> itens,
            String observacao) {
        List<AdminPremiumAtivacaoOperacaoDto> resultado = itens.stream().map(item -> {
            GrupoAtivacaoBeneficioEntity grupo = grupos.get(item.beneficioId());
            List<AtivacaoBeneficioEntity> ativacoes = grupo == null
                    ? List.of()
                    : ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
            AtivacaoBeneficioEntity ativacao = ativacoes.size() == 1 ? ativacoes.get(0) : null;
            long duracao = grupo == null || grupo.getValidadeInicioEm() == null || grupo.getValidadeFimEm() == null
                    ? -1
                    : java.time.Duration.between(grupo.getValidadeInicioEm(), grupo.getValidadeFimEm()).toDays();
            if (ativacao == null
                    || !anuncioId.equals(grupo.getAnuncioId())
                    || !item.beneficioId().equals(ativacao.getBeneficioId())
                    || duracao != item.duracaoDias()
                    || !Objects.equals(observacao, grupo.getObservacao())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outro lote");
            }
            return toDto(ativacao, 0, true);
        }).toList();
        return new AdminPremiumAtivacaoLoteDto(resultado, true);
    }

    private void validarAdministrador(AdminUserPrincipal administrador) {
        if (administrador == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
    }

    private void validarElegibilidadeJuridica(AnuncioEntity anuncio) {
        boolean usuarioBloqueado = bloqueioJuridicoRepository
                .existsByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNull(
                        anuncio.getUsuarioId(),
                        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO);
        if (anuncio.getStatus() == StatusAnuncio.BLOQUEADO || usuarioBloqueado) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "bloqueio juridico impede ativacao Premium");
        }
    }

    private AdminPremiumAtivacaoOperacaoDto toDto(
            AtivacaoBeneficioEntity ativacao,
            int creditosEstornados,
            boolean idempotente) {
        return new AdminPremiumAtivacaoOperacaoDto(
                ativacao.getId(),
                ativacao.getUsuarioId(),
                ativacao.getAnuncioId(),
                ativacao.getBeneficioId(),
                ativacao.getStatus().name(),
                creditosEstornados,
                ativacao.getFimEm(),
                idempotente);
    }

    private int valor(Integer value) {
        return value == null ? 0 : value;
    }
}
