package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoOperacaoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
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
    private final BeneficioAnuncioConsultaService beneficioConsultaService;

    public AdminPremiumOperacaoService(
            AtivacaoBeneficioRepository ativacaoRepository,
            MovimentoCreditoRepository movimentoRepository,
            AdminCreditoOperacaoService creditoService,
            BeneficioPremiumRepository beneficioRepository,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            GrupoAtivacaoBeneficioRepository grupoRepository,
            AnuncioRepository anuncioRepository,
            BeneficioAnuncioConsultaService beneficioConsultaService) {
        this.ativacaoRepository = ativacaoRepository;
        this.movimentoRepository = movimentoRepository;
        this.creditoService = creditoService;
        this.beneficioRepository = beneficioRepository;
        this.opcaoRepository = opcaoRepository;
        this.grupoRepository = grupoRepository;
        this.anuncioRepository = anuncioRepository;
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
        String observacao = request.observacao() == null ? "" : request.observacao().trim();
        if (observacao.length() < 3 || observacao.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "observacao obrigatoria");
        }
        String chave = "premium-admin:" + administrador.usuarioId() + ":"
                + CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
        var repetido = grupoRepository.findByIdempotencyKey(chave);
        if (repetido.isPresent()) {
            return resultadoRepetido(repetido.get(), anuncioId, request);
        }

        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .filter(item -> item.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        var repetidoAposLock = grupoRepository.findByIdempotencyKey(chave);
        if (repetidoAposLock.isPresent()) {
            return resultadoRepetido(repetidoAposLock.get(), anuncioId, request);
        }
        BeneficioPremiumEntity beneficio = beneficioRepository.findById(request.beneficioId())
                .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                .filter(item -> item.getEscopo() == EscopoBeneficioPremium.ANUNCIO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "beneficio nao encontrado"));
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        BeneficioPremiumOpcaoEntity opcao = opcaoRepository
                .findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(
                        beneficio.getId(), request.duracaoDias())
                .filter(item -> item.vigente(agora))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "duracao nao encontrada"));
        boolean duplicadoAtivo = beneficioConsultaService.consultarCalculados(anuncioId).stream()
                .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO)
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
        AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(
                AtivacaoBeneficioEntity.criarAdministrativa(
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
                        agora));
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
                        "observacaoRegistrada", true),
                requestId);
        return toDto(ativacao, 0, false);
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
                && ativacao.getStatus() != StatusAtivacaoBeneficio.AGENDADA) {
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
            AdminPremiumAtivarRequest request) {
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
        AtivacaoBeneficioEntity ativacao = ativacoes.size() == 1 ? ativacoes.get(0) : null;
        long duracao = grupo.getValidadeInicioEm() == null || grupo.getValidadeFimEm() == null
                ? -1
                : java.time.Duration.between(grupo.getValidadeInicioEm(), grupo.getValidadeFimEm()).toDays();
        if (ativacao == null
                || !anuncioId.equals(grupo.getAnuncioId())
                || !request.beneficioId().equals(ativacao.getBeneficioId())
                || duracao != request.duracaoDias()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outra operacao");
        }
        return toDto(ativacao, 0, true);
    }

    private void validarAdministrador(AdminUserPrincipal administrador) {
        if (administrador == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
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
