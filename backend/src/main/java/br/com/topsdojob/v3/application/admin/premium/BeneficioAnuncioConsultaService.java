package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.application.admin.premium.dto.AdminBeneficioAnuncioDto;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficioAnuncioConsultaService {

    private final AtivacaoBeneficioRepository ativacaoRepository;
    private final BeneficioPremiumRepository beneficioRepository;
    private final GrupoAtivacaoBeneficioRepository grupoRepository;
    private final PremiumExpiracaoPolicyService policyService;

    public BeneficioAnuncioConsultaService(
            AtivacaoBeneficioRepository ativacaoRepository,
            BeneficioPremiumRepository beneficioRepository,
            GrupoAtivacaoBeneficioRepository grupoRepository,
            PremiumExpiracaoPolicyService policyService) {
        this.ativacaoRepository = ativacaoRepository;
        this.beneficioRepository = beneficioRepository;
        this.grupoRepository = grupoRepository;
        this.policyService = policyService;
    }

    @Transactional(readOnly = true)
    public List<AdminBeneficioAnuncioDto> consultar(UUID anuncioId) {
        return consultarCalculados(anuncioId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PremiumBeneficioCalculado> consultarCalculados(UUID anuncioId) {
        return calcular(ativacaoRepository.findByAnuncioId(anuncioId), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<PremiumBeneficioCalculado>> consultarCalculadosPorAnuncio(Collection<UUID> anuncioIds) {
        if (anuncioIds == null || anuncioIds.isEmpty()) {
            return Map.of();
        }
        return calcular(
                ativacaoRepository.findByAnuncioIdIn(anuncioIds),
                OffsetDateTime.now(ZoneOffset.UTC)).stream()
                .filter(item -> item.ativacao() != null && item.ativacao().getAnuncioId() != null)
                .collect(Collectors.groupingBy(item -> item.ativacao().getAnuncioId()));
    }

    @Transactional(readOnly = true)
    public List<PremiumBeneficioCalculado> calcular(Collection<AtivacaoBeneficioEntity> ativacoes, OffsetDateTime agora) {
        if (ativacoes == null || ativacoes.isEmpty()) {
            return List.of();
        }
        List<UUID> beneficioIds = ativacoes.stream()
                .map(AtivacaoBeneficioEntity::getBeneficioId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<UUID> grupoIds = ativacoes.stream()
                .map(AtivacaoBeneficioEntity::getGrupoAtivacaoId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, BeneficioPremiumEntity> beneficios = beneficioIds.isEmpty()
                ? Map.of()
                : beneficioRepository.findByIdIn(beneficioIds).stream()
                        .collect(Collectors.toMap(BeneficioPremiumEntity::getId, Function.identity()));
        Map<UUID, GrupoAtivacaoBeneficioEntity> grupos = grupoIds.isEmpty()
                ? Map.of()
                : grupoRepository.findByIdIn(grupoIds).stream()
                        .collect(Collectors.toMap(GrupoAtivacaoBeneficioEntity::getId, Function.identity()));
        return ativacoes.stream()
                .map(ativacao -> policyService.avaliar(
                        ativacao,
                        beneficios.get(ativacao.getBeneficioId()),
                        grupos.get(ativacao.getGrupoAtivacaoId()),
                        agora))
                .sorted(Comparator
                        .comparing((PremiumBeneficioCalculado item) -> item.ativacao().getFimEm(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(item -> codigoBeneficio(item.beneficio()), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public AdminBeneficioAnuncioDto toDto(PremiumBeneficioCalculado calculado) {
        AtivacaoBeneficioEntity ativacao = calculado.ativacao();
        BeneficioPremiumEntity beneficio = calculado.beneficio();
        GrupoAtivacaoBeneficioEntity grupo = calculado.grupo();
        return new AdminBeneficioAnuncioDto(
                ativacao.getId(),
                codigoBeneficio(beneficio),
                beneficio == null ? null : beneficio.getNome(),
                beneficio == null || beneficio.getEscopo() == null ? null : beneficio.getEscopo().name(),
                ativacao.getStatus() == null ? null : ativacao.getStatus().name(),
                calculado.status().name(),
                ativacao.getOrigem() == null ? null : ativacao.getOrigem().name(),
                ativacao.getInicioEm(),
                ativacao.getFimEm(),
                ativacao.getInicioEm() == null || ativacao.getFimEm() == null
                        ? null
                        : java.time.Duration.between(ativacao.getInicioEm(), ativacao.getFimEm()).toDays(),
                calculado.venceEmBreve(),
                grupo != null,
                grupo == null ? null : grupo.getId(),
                grupo == null || grupo.getTipo() == null ? null : grupo.getTipo().name(),
                grupo == null || grupo.getStatus() == null ? null : grupo.getStatus().name(),
                grupo == null ? null : grupo.getValidadeFimEm(),
                grupo == null ? null : grupo.getObservacao(),
                calculado.codigos().stream().map(Enum::name).toList(),
                calculado.inconsistente(),
                true);
    }

    private String codigoBeneficio(BeneficioPremiumEntity beneficio) {
        return beneficio == null ? null : beneficio.getCodigo();
    }
}
