package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioLocalizacaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnunciantePerformanceDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnunciantePerformanceDto.AnuncioPerformanceDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnunciantePerformanceDto.ComparativoCliquesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnunciantePerformanceDto.SerieCliquesDto;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PainelAnunciantePerformanceService {

    private static final int DIAS_SERIE = 14;
    private static final int DIAS_PERIODO = 7;

    private final MeusAnunciosConsultaService meusAnunciosService;
    private final CliqueWhatsappRepository cliqueRepository;
    private final AtivacaoBeneficioRepository ativacaoRepository;
    private final Clock clock;

    @Autowired
    public PainelAnunciantePerformanceService(
            MeusAnunciosConsultaService meusAnunciosService,
            CliqueWhatsappRepository cliqueRepository,
            AtivacaoBeneficioRepository ativacaoRepository) {
        this(meusAnunciosService, cliqueRepository, ativacaoRepository, Clock.systemUTC());
    }

    PainelAnunciantePerformanceService(
            MeusAnunciosConsultaService meusAnunciosService,
            CliqueWhatsappRepository cliqueRepository,
            AtivacaoBeneficioRepository ativacaoRepository,
            Clock clock) {
        this.meusAnunciosService = meusAnunciosService;
        this.cliqueRepository = cliqueRepository;
        this.ativacaoRepository = ativacaoRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PainelAnunciantePerformanceDto consultar(Authentication authentication) {
        UUID usuarioId = meusAnunciosService.usuarioAutenticado(authentication).getId();
        List<MeuAnuncioDto> anuncios = meusAnunciosService.listarDoUsuario(usuarioId);
        OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        LocalDate hoje = agora.toLocalDate();
        OffsetDateTime inicioSerie = hoje.minusDays(DIAS_SERIE - 1L).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime fimExclusivo = hoje.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<UUID, Long> cliquesPorAnuncio = cliqueRepository.countPermitidosPorUsuario(usuarioId).stream()
                .collect(Collectors.toMap(
                        CliqueWhatsappRepository.ContagemPorAnuncioProjection::getAnuncioId,
                        CliqueWhatsappRepository.ContagemPorAnuncioProjection::getTotalCliques,
                        Math::addExact));
        Map<UUID, Long> premiumPorAnuncio = ativacaoRepository.countVigentesPorUsuario(usuarioId, agora).stream()
                .collect(Collectors.toMap(
                        AtivacaoBeneficioRepository.ContagemPremiumVigenteProjection::getAnuncioId,
                        AtivacaoBeneficioRepository.ContagemPremiumVigenteProjection::getTotalBeneficios,
                        Math::addExact));
        Map<LocalDate, Long> cliquesPorDia = cliqueRepository.countPermitidosDiariosPorUsuario(
                        usuarioId, inicioSerie, fimExclusivo).stream()
                .collect(Collectors.toMap(
                        CliqueWhatsappRepository.ContagemDiariaProjection::getDataReferencia,
                        CliqueWhatsappRepository.ContagemDiariaProjection::getTotalCliques,
                        Math::addExact));

        VisualizacoesCanonicasDto visualizacoes = totalVisualizacoes(anuncios);
        long totalCliques = somaCliques(anuncios, cliquesPorAnuncio);
        List<SerieCliquesDto> serie = serie(hoje, cliquesPorDia);
        long cliquesPeriodoAtual = somaPeriodo(serie, hoje.minusDays(DIAS_PERIODO - 1L), hoje);
        long cliquesPeriodoAnterior = somaPeriodo(
                serie,
                hoje.minusDays(DIAS_SERIE - 1L),
                hoje.minusDays(DIAS_PERIODO));
        List<AnuncioPerformanceDto> ranking = ranking(anuncios, cliquesPorAnuncio, premiumPorAnuncio);

        return new PainelAnunciantePerformanceDto(
                visualizacoes,
                totalCliques,
                ctr(visualizacoes, totalCliques),
                premiumPorAnuncio.entrySet().stream()
                        .filter(entry -> contemAnuncio(anuncios, entry.getKey()) && entry.getValue() > 0)
                        .count(),
                new ComparativoCliquesDto(
                        cliquesPeriodoAtual,
                        cliquesPeriodoAnterior,
                        variacao(cliquesPeriodoAtual, cliquesPeriodoAnterior)),
                serie,
                ranking);
    }

    private VisualizacoesCanonicasDto totalVisualizacoes(List<MeuAnuncioDto> anuncios) {
        if (anuncios.stream().map(MeuAnuncioDto::visualizacoes)
                .anyMatch(item -> item.situacao() == VisualizacoesCanonicasDto.Situacao.HISTORICO_PENDENTE)) {
            return VisualizacoesCanonicasDto.historicoPendente();
        }
        long total = anuncios.stream()
                .map(MeuAnuncioDto::visualizacoes)
                .map(VisualizacoesCanonicasDto::total)
                .filter(Objects::nonNull)
                .reduce(0L, Math::addExact);
        return VisualizacoesCanonicasDto.total(total);
    }

    private long somaCliques(List<MeuAnuncioDto> anuncios, Map<UUID, Long> cliquesPorAnuncio) {
        return anuncios.stream()
                .map(MeuAnuncioDto::id)
                .map(id -> cliquesPorAnuncio.getOrDefault(id, 0L))
                .reduce(0L, Math::addExact);
    }

    private List<SerieCliquesDto> serie(LocalDate hoje, Map<LocalDate, Long> cliquesPorDia) {
        LocalDate inicio = hoje.minusDays(DIAS_SERIE - 1L);
        return Stream.iterate(inicio, data -> data.plusDays(1))
                .limit(DIAS_SERIE)
                .map(data -> new SerieCliquesDto(data, cliquesPorDia.getOrDefault(data, 0L)))
                .toList();
    }

    private long somaPeriodo(List<SerieCliquesDto> serie, LocalDate inicio, LocalDate fim) {
        return serie.stream()
                .filter(item -> !item.data().isBefore(inicio) && !item.data().isAfter(fim))
                .map(SerieCliquesDto::cliques)
                .reduce(0L, Math::addExact);
    }

    private List<AnuncioPerformanceDto> ranking(
            List<MeuAnuncioDto> anuncios,
            Map<UUID, Long> cliquesPorAnuncio,
            Map<UUID, Long> premiumPorAnuncio) {
        Comparator<AnuncioPerformanceDto> comparador = Comparator
                .comparingLong(AnuncioPerformanceDto::cliquesWhatsapp).reversed()
                .thenComparing(Comparator.comparingLong(this::totalOrdenacao).reversed())
                .thenComparing(AnuncioPerformanceDto::anuncioTitulo, String.CASE_INSENSITIVE_ORDER);
        return anuncios.stream()
                .map(anuncio -> {
                    long cliques = cliquesPorAnuncio.getOrDefault(anuncio.id(), 0L);
                    return new AnuncioPerformanceDto(
                            anuncio.id(),
                            anuncio.slug(),
                            anuncio.titulo(),
                            localizacao(anuncio.localizacao()),
                            anuncio.capa() == null ? null : anuncio.capa().urlPublica(),
                            anuncio.visualizacoes(),
                            cliques,
                            ctr(anuncio.visualizacoes(), cliques),
                            premiumPorAnuncio.getOrDefault(anuncio.id(), 0L));
                })
                .sorted(comparador)
                .toList();
    }

    private long totalOrdenacao(AnuncioPerformanceDto item) {
        Long total = item.visualizacoes().total();
        return total == null ? -1L : total;
    }

    private String localizacao(MeuAnuncioLocalizacaoDto localizacao) {
        if (localizacao == null) {
            return null;
        }
        String valor = Stream.of(localizacao.bairro(), localizacao.cidade(), localizacao.uf())
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.joining(", "));
        return valor.isBlank() ? null : valor;
    }

    private boolean contemAnuncio(List<MeuAnuncioDto> anuncios, UUID anuncioId) {
        return anuncios.stream().anyMatch(item -> item.id().equals(anuncioId));
    }

    static BigDecimal ctr(VisualizacoesCanonicasDto visualizacoes, long cliques) {
        if (visualizacoes.situacao() == VisualizacoesCanonicasDto.Situacao.HISTORICO_PENDENTE) {
            return null;
        }
        long total = Objects.requireNonNull(visualizacoes.total());
        if (total == 0) {
            return new BigDecimal("0.00");
        }
        return BigDecimal.valueOf(cliques)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal variacao(long atual, long anterior) {
        if (anterior == 0) {
            return atual == 0 ? new BigDecimal("0.00") : new BigDecimal("100.00");
        }
        return BigDecimal.valueOf(Math.subtractExact(atual, anterior))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(anterior), 2, RoundingMode.HALF_UP);
    }
}
