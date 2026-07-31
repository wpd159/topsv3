package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioBeneficioDto;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeuAnuncioBeneficioConsultaService {

    private static final String AGUARDANDO_APROVACAO_MODERACAO =
            "AGUARDANDO_APROVACAO_MODERACAO";

    private final BeneficioAnuncioConsultaService beneficioConsultaService;
    private final BeneficioPremiumOpcaoRepository opcaoRepository;
    private final Clock clock;

    @Autowired
    public MeuAnuncioBeneficioConsultaService(
            BeneficioAnuncioConsultaService beneficioConsultaService,
            BeneficioPremiumOpcaoRepository opcaoRepository) {
        this(beneficioConsultaService, opcaoRepository, Clock.systemUTC());
    }

    MeuAnuncioBeneficioConsultaService(
            BeneficioAnuncioConsultaService beneficioConsultaService,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            Clock clock) {
        this.beneficioConsultaService = beneficioConsultaService;
        this.opcaoRepository = opcaoRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<MeuAnuncioBeneficioDto>> consultarEmLote(Collection<UUID> anuncioIds) {
        if (anuncioIds == null || anuncioIds.isEmpty()) {
            return Map.of();
        }
        OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        Map<UUID, List<PremiumBeneficioCalculado>> calculados =
                beneficioConsultaService.consultarCalculadosPorAnuncio(anuncioIds, agora);
        List<UUID> opcaoIds = calculados.values().stream()
                .flatMap(Collection::stream)
                .map(PremiumBeneficioCalculado::ativacao)
                .filter(Objects::nonNull)
                .map(item -> item.getOpcaoId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, BeneficioPremiumOpcaoEntity> opcoes = opcaoRepository.findAllById(opcaoIds).stream()
                .collect(Collectors.toMap(BeneficioPremiumOpcaoEntity::getId, Function.identity()));

        Map<UUID, List<MeuAnuncioBeneficioDto>> resposta = new LinkedHashMap<>();
        anuncioIds.forEach(anuncioId -> resposta.put(
                anuncioId,
                efetivos(calculados.getOrDefault(anuncioId, List.of())).stream()
                        .sorted(Comparator
                                .comparingInt((PremiumBeneficioCalculado item) -> ordem(item))
                                .thenComparing(item -> item.beneficio().getCodigo()))
                        .map(item -> dto(item, opcoes.get(item.ativacao().getOpcaoId()), agora))
                        .toList()));
        return Map.copyOf(resposta);
    }

    private List<PremiumBeneficioCalculado> efetivos(List<PremiumBeneficioCalculado> calculados) {
        return calculados.stream()
                .filter(item -> item.ativacao() != null && item.beneficio() != null)
                .collect(Collectors.toMap(
                        item -> item.beneficio().getCodigo(),
                        Function.identity(),
                        this::maisRelevante,
                        LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    private PremiumBeneficioCalculado maisRelevante(
            PremiumBeneficioCalculado atual,
            PremiumBeneficioCalculado candidato) {
        int prioridadeAtual = prioridade(atual);
        int prioridadeCandidato = prioridade(candidato);
        if (prioridadeAtual != prioridadeCandidato) {
            return prioridadeCandidato > prioridadeAtual ? candidato : atual;
        }
        OffsetDateTime criadoAtual = atual.ativacao().getCriadoEm();
        OffsetDateTime criadoCandidato = candidato.ativacao().getCriadoEm();
        if (criadoAtual == null) return candidato;
        if (criadoCandidato == null) return atual;
        return criadoCandidato.isAfter(criadoAtual) ? candidato : atual;
    }

    private int prioridade(PremiumBeneficioCalculado item) {
        return switch (item.status()) {
            case PENDENTE -> 5;
            case ATIVO, VENCENDO -> 4;
            case EXPIRADO -> 3;
            case INATIVO -> 2;
            case INCONSISTENTE -> 1;
        };
    }

    private int ordem(PremiumBeneficioCalculado item) {
        Integer ordem = item.beneficio().getOrdemExibicao();
        return ordem == null ? Integer.MAX_VALUE : ordem;
    }

    private MeuAnuncioBeneficioDto dto(
            PremiumBeneficioCalculado item,
            BeneficioPremiumOpcaoEntity opcao,
            OffsetDateTime agora) {
        boolean aguardando = item.ativacao().getStatus()
                == StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO;
        String status = aguardando
                ? "AGUARDANDO_MODERACAO"
                : switch (item.status()) {
                    case ATIVO, VENCENDO -> "ATIVO";
                    case EXPIRADO -> "EXPIRADO";
                    case PENDENTE -> "PENDENTE";
                    case INATIVO -> "INATIVO";
                    case INCONSISTENTE -> "ERRO";
                };
        return new MeuAnuncioBeneficioDto(
                item.beneficio().getCodigo(),
                nomeExibicao(item),
                status,
                item.ativacao().getInicioEm(),
                item.ativacao().getFimEm(),
                duracaoDias(item, opcao),
                "ATIVO".equals(status) ? diasRestantes(item.ativacao().getFimEm(), agora) : null,
                aguardando ? AGUARDANDO_APROVACAO_MODERACAO : null,
                item.ativacao().getOrigem() == null ? null : item.ativacao().getOrigem().name());
    }

    private String nomeExibicao(PremiumBeneficioCalculado item) {
        return FOTOS_EXTRA_5.equals(item.beneficio().getCodigo())
                ? "Mais fotos"
                : item.beneficio().getNome();
    }

    private Integer duracaoDias(
            PremiumBeneficioCalculado item,
            BeneficioPremiumOpcaoEntity opcao) {
        if (opcao != null && opcao.getDuracaoDias() != null) {
            return opcao.getDuracaoDias();
        }
        if (item.ativacao().getInicioEm() == null || item.ativacao().getFimEm() == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(
                item.ativacao().getInicioEm(),
                item.ativacao().getFimEm()));
    }

    private Integer diasRestantes(OffsetDateTime fimEm, OffsetDateTime agora) {
        if (fimEm == null || !fimEm.isAfter(agora)) {
            return null;
        }
        long segundos = Duration.between(agora, fimEm).getSeconds();
        return Math.toIntExact(Math.max(1, (segundos + 86_399) / 86_400));
    }
}
