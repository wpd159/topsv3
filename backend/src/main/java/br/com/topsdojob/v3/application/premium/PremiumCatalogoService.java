package br.com.topsdojob.v3.application.premium;

import br.com.topsdojob.v3.application.premium.dto.PlanoCreditoDto;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import br.com.topsdojob.v3.application.premium.dto.PremiumOpcaoDto;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumCatalogoService {

    private final BeneficioPremiumRepository beneficioRepository;
    private final BeneficioPremiumOpcaoRepository opcaoRepository;
    private final PlanoCreditoRepository planoRepository;

    public PremiumCatalogoService(
            BeneficioPremiumRepository beneficioRepository,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            PlanoCreditoRepository planoRepository) {
        this.beneficioRepository = beneficioRepository;
        this.opcaoRepository = opcaoRepository;
        this.planoRepository = planoRepository;
    }

    @Transactional(readOnly = true)
    public List<PremiumCatalogoDto> catalogoAtivo() {
        return catalogo(true);
    }

    @Transactional(readOnly = true)
    public List<PremiumCatalogoDto> catalogoAdministrativo() {
        return catalogo(false);
    }

    @Transactional(readOnly = true)
    public List<PlanoCreditoDto> pacotesAtivos() {
        return planoRepository.findByAtivoTrueOrderByOrdemExibicaoAscCodigoAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanoCreditoDto> pacotesAdministrativos() {
        return planoRepository.findAllByOrderByOrdemExibicaoAscCodigoAsc().stream()
                .map(this::toDto)
                .toList();
    }

    private List<PremiumCatalogoDto> catalogo(boolean somenteAtivos) {
        var beneficios = beneficioRepository.findAllByOrderByOrdemExibicaoAscCodigoAsc();
        var ids = beneficios.stream().map(item -> item.getId()).toList();
        Map<UUID, List<BeneficioPremiumOpcaoEntity>> porBeneficio = ids.isEmpty()
                ? Map.of()
                : opcaoRepository.findByBeneficioIdInOrderByOrdemExibicaoAscDuracaoDiasAsc(ids).stream()
                        .collect(Collectors.groupingBy(BeneficioPremiumOpcaoEntity::getBeneficioId));
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        return beneficios.stream()
                .filter(item -> !somenteAtivos || Boolean.TRUE.equals(item.getAtivo()))
                .map(item -> new PremiumCatalogoDto(
                        item.getId(),
                        item.getCodigo(),
                        item.getNome(),
                        item.getDescricao(),
                        item.getEscopo() == null ? null : item.getEscopo().name(),
                        Boolean.TRUE.equals(item.getAfetaRanking()),
                        Boolean.TRUE.equals(item.getAtivo()),
                        valor(item.getOrdemExibicao()),
                        opcoesAtuais(porBeneficio.getOrDefault(item.getId(), List.of()), agora, somenteAtivos)))
                .toList();
    }

    private List<PremiumOpcaoDto> opcoesAtuais(
            List<BeneficioPremiumOpcaoEntity> opcoes,
            OffsetDateTime agora,
            boolean somenteAtivas) {
        Map<Integer, BeneficioPremiumOpcaoEntity> atualPorDuracao = opcoes.stream()
                .collect(Collectors.toMap(
                        BeneficioPremiumOpcaoEntity::getDuracaoDias,
                        Function.identity(),
                        (a, b) -> valor(a.getVersaoRegra()) >= valor(b.getVersaoRegra()) ? a : b));
        return atualPorDuracao.values().stream()
                .filter(item -> !somenteAtivas || item.vigente(agora))
                .sorted(Comparator.comparing(BeneficioPremiumOpcaoEntity::getOrdemExibicao)
                        .thenComparing(BeneficioPremiumOpcaoEntity::getDuracaoDias))
                .map(item -> new PremiumOpcaoDto(
                        item.getId(),
                        valor(item.getDuracaoDias()),
                        valor(item.getCustoCreditos()),
                        item.vigente(agora),
                        valor(item.getOrdemExibicao())))
                .toList();
    }

    private PlanoCreditoDto toDto(br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity item) {
        return new PlanoCreditoDto(
                item.getId(),
                item.getCodigo(),
                item.getNome(),
                item.getDescricao(),
                valor(item.getQuantidadeCreditos()),
                item.getValor(),
                item.getMoeda(),
                Boolean.TRUE.equals(item.getAtivo()),
                valor(item.getOrdemExibicao()));
    }

    private int valor(Integer value) {
        return value == null ? 0 : value;
    }
}
