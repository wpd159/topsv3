package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoOperacaoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoDto;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
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

    public AdminPremiumOperacaoService(
            AtivacaoBeneficioRepository ativacaoRepository,
            MovimentoCreditoRepository movimentoRepository,
            AdminCreditoOperacaoService creditoService,
            BeneficioPremiumRepository beneficioRepository) {
        this.ativacaoRepository = ativacaoRepository;
        this.movimentoRepository = movimentoRepository;
        this.creditoService = creditoService;
        this.beneficioRepository = beneficioRepository;
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
        if (ativacao.getStatus() != StatusAtivacaoBeneficio.ATIVA) {
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
