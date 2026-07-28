package br.com.topsdojob.v3.application.admin.dashboard;

import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.Analises;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.AnuncioDesempenho;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.CidadeDesempenho;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.ClassificacaoDesempenho;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.DesempenhoDiario;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.Insight;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.PontoDiario;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.ResumoDia;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.TopWhatsappHoje;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.TopWhatsappItem;
import br.com.topsdojob.v3.application.admin.premium.PremiumExpiracaoPolicyService;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminDashboardAnalyticsJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminDashboardAnalyticsJdbcRepository.AnuncioDesempenhoRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardAnalyticsService {

    static final Set<Integer> PERIODOS_PERMITIDOS = Set.of(7, 15, 30);
    static final int LIMITE_TOP_WHATSAPP_MAXIMO = 48;
    static final int MINIMO_VIEWS_PIOR_CONVERSAO = 100;
    static final int MINIMO_VIEWS_ALERTA_SEM_CLIQUE = 200;
    static final int MINIMO_VIEWS_ALTO_TRAFEGO = 2_000;
    static final int MINIMO_ANUNCIOS_CIDADE_ALERTA = 3;
    static final BigDecimal FATOR_CIDADE_ABAIXO_MEDIA = new BigDecimal("0.85");

    private final AdminDashboardAnalyticsJdbcRepository repository;
    private final AnuncioMidiaRepository midiaRepository;
    private final ArquivoMidiaRepository arquivoRepository;
    private final MidiaPublicaUrlService midiaUrlService;
    private final Clock clock;

    @Autowired
    public AdminDashboardAnalyticsService(
            AdminDashboardAnalyticsJdbcRepository repository,
            AnuncioMidiaRepository midiaRepository,
            ArquivoMidiaRepository arquivoRepository,
            MidiaPublicaUrlService midiaUrlService) {
        this(repository, midiaRepository, arquivoRepository, midiaUrlService, Clock.systemUTC());
    }

    AdminDashboardAnalyticsService(
            AdminDashboardAnalyticsJdbcRepository repository,
            AnuncioMidiaRepository midiaRepository,
            ArquivoMidiaRepository arquivoRepository,
            MidiaPublicaUrlService midiaUrlService,
            Clock clock) {
        this.repository = repository;
        this.midiaRepository = midiaRepository;
        this.arquivoRepository = arquivoRepository;
        this.midiaUrlService = midiaUrlService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DesempenhoDiario desempenhoDiario(int dias) {
        if (!PERIODOS_PERMITIDOS.contains(dias)) {
            throw new IllegalArgumentException("periodo deve ser 7, 15 ou 30 dias");
        }
        OffsetDateTime agora = agora();
        LocalDate hoje = agora.atZoneSameInstant(AdminDashboardHojeService.FUSO_CANONICO).toLocalDate();
        LocalDate inicio = hoje.minusDays(dias - 1L);
        Map<LocalDate, AdminDashboardAnalyticsJdbcRepository.SerieDiariaRow> existentes =
                repository.serieDiaria(inicio, hoje).stream()
                        .collect(Collectors.toMap(
                                AdminDashboardAnalyticsJdbcRepository.SerieDiariaRow::data,
                                Function.identity()));
        List<PontoDiario> serie = inicio.datesUntil(hoje.plusDays(1))
                .map(data -> {
                    var row = existentes.get(data);
                    long visualizacoes = row == null ? 0 : row.visualizacoes();
                    long cliques = row == null ? 0 : row.cliques();
                    return new PontoDiario(data, visualizacoes, cliques, conversao(visualizacoes, cliques));
                })
                .toList();
        PontoDiario pontoHoje = serie.get(serie.size() - 1);
        LocalDate ontemData = hoje.minusDays(1);
        PontoDiario pontoOntem = serie.stream()
                .filter(item -> item.data().equals(ontemData))
                .findFirst()
                .orElseGet(() -> new PontoDiario(ontemData, 0, 0, BigDecimal.ZERO.setScale(2)));
        long totalVisualizacoes = serie.stream().mapToLong(PontoDiario::visualizacoes).sum();
        long totalCliques = serie.stream().mapToLong(PontoDiario::cliquesWhatsapp).sum();
        return new DesempenhoDiario(
                dias,
                inicio,
                hoje,
                AdminDashboardHojeService.FUSO_CANONICO.getId(),
                serie,
                resumo(pontoHoje),
                resumo(pontoOntem),
                variacao(pontoHoje.visualizacoes(), pontoOntem.visualizacoes()),
                variacao(pontoHoje.cliquesWhatsapp(), pontoOntem.cliquesWhatsapp()),
                totalVisualizacoes,
                totalCliques,
                agora);
    }

    @Transactional(readOnly = true)
    public TopWhatsappHoje topWhatsappHoje(int limiteSolicitado) {
        int limite = Math.max(1, Math.min(limiteSolicitado, LIMITE_TOP_WHATSAPP_MAXIMO));
        OffsetDateTime agora = agora();
        LocalDate hoje = agora.atZoneSameInstant(AdminDashboardHojeService.FUSO_CANONICO).toLocalDate();
        var rows = repository.topWhatsappHoje(hoje, limite + 1);
        boolean temMais = rows.size() > limite;
        var exibidas = rows.stream().limit(limite).toList();
        Map<UUID, String> miniaturas = resolverMiniaturas(exibidas.stream()
                .map(AdminDashboardAnalyticsJdbcRepository.TopWhatsappRow::midiaId)
                .filter(java.util.Objects::nonNull)
                .toList());
        List<TopWhatsappItem> itens = exibidas.stream()
                .map(item -> new TopWhatsappItem(
                        item.anuncioId(),
                        item.titulo(),
                        item.slug(),
                        item.cidade(),
                        item.uf(),
                        item.cliques(),
                        item.midiaId() == null ? null : miniaturas.get(item.midiaId()),
                        item.publicado()))
                .toList();
        return new TopWhatsappHoje(
                hoje,
                AdminDashboardHojeService.FUSO_CANONICO.getId(),
                limite,
                temMais,
                itens,
                agora);
    }

    @Transactional(readOnly = true)
    public Analises analises(boolean incluirComercial) {
        OffsetDateTime agora = agora();
        List<AnuncioDesempenhoRow> base = repository.desempenhoPublicados(agora);
        List<CidadeDesempenho> cidadesCompletas = cidades(base);
        List<CidadeDesempenho> cidades = cidadesCompletas.stream().limit(12).toList();
        List<ClassificacaoDesempenho> classificacoes = classificacoes(base);
        List<Insight> alertas = alertas(base, cidadesCompletas, incluirComercial);
        List<Insight> oportunidades = incluirComercial ? oportunidades(base, agora) : List.of();
        List<AnuncioDesempenho> top = base.stream()
                .filter(item -> item.visualizacoes() != null && item.visualizacoes() > 0)
                .sorted(comparadorTopConversao())
                .limit(5)
                .map(this::toDto)
                .toList();
        List<AnuncioDesempenho> pior = base.stream()
                .filter(item -> item.visualizacoes() != null
                        && item.visualizacoes() >= MINIMO_VIEWS_PIOR_CONVERSAO)
                .sorted(comparadorPiorConversao())
                .limit(5)
                .map(this::toDto)
                .toList();
        return new Analises(
                alertas,
                oportunidades,
                top,
                pior,
                cidades,
                classificacoes,
                MINIMO_VIEWS_PIOR_CONVERSAO,
                agora);
    }

    private List<Insight> alertas(
            List<AnuncioDesempenhoRow> base,
            List<CidadeDesempenho> cidades,
            boolean incluirComercial) {
        List<Insight> itens = new ArrayList<>();
        List<AnuncioDesempenhoRow> comViews = base.stream()
                .filter(item -> item.visualizacoes() != null && item.visualizacoes() > 0)
                .toList();
        long semCliques = comViews.stream().filter(item -> item.cliques() == 0).count();
        if (!comViews.isEmpty() && semCliques > 0) {
            BigDecimal percentual = BigDecimal.valueOf(semCliques)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(comViews.size()), 1, RoundingMode.HALF_UP);
            itens.add(new Insight(
                    "ANUNCIOS_COM_VIEWS_SEM_CLIQUE",
                    percentual.toPlainString() + "% dos anúncios com views não têm clique",
                    "Base com exposição, mas sem conversão em WhatsApp.",
                    "/admin/anuncios?ordenacao=MAIS_VISUALIZACOES"));
        }
        long altoTrafegoSemClique = comViews.stream()
                .filter(item -> item.visualizacoes() >= MINIMO_VIEWS_ALERTA_SEM_CLIQUE)
                .filter(item -> item.cliques() == 0)
                .count();
        if (altoTrafegoSemClique > 0) {
            itens.add(new Insight(
                    "ALTO_TRAFEGO_SEM_CLIQUE",
                    altoTrafegoSemClique + " anúncio"
                            + (altoTrafegoSemClique == 1 ? "" : "s")
                            + " com muitas views e zero clique",
                    "Candidatos a revisão de criativo, preço ou canal.",
                    "/admin/anuncios?ordenacao=MAIS_VISUALIZACOES"));
        }
        cidadeAbaixoDaMedia(cidades).ifPresent(cidade -> itens.add(new Insight(
                "CIDADE_ABAIXO_MEDIA",
                "Cidade em destaque: " + cidade.cidade(),
                "Conversão agregada " + percentualTexto(cidade.conversaoPct())
                        + " contra média " + percentualTexto(mediaConversao(cidades)) + ".",
                "/admin/anuncios?uf=" + cidade.uf() + "&cidade=" + cidade.cidadeSlug())));
        if (incluirComercial) {
            long semPremium = base.stream().filter(item -> item.beneficiosPremium() == 0).count();
            long beneficiosAtivos = base.stream().mapToLong(AnuncioDesempenhoRow::beneficiosPremium).sum();
            if (beneficiosAtivos > 0 && semPremium > 20 && semPremium > beneficiosAtivos * 3) {
                itens.add(new Insight(
                        "DESPROPORCAO_PREMIUM",
                        "Possível desproporção entre Premium e base comercial",
                        "Há muitos anúncios publicados sem benefício vigente em relação às ativações atuais.",
                        "/admin/beneficios-premium"));
            }
        }
        return List.copyOf(itens);
    }

    private List<Insight> oportunidades(
            List<AnuncioDesempenhoRow> base,
            OffsetDateTime agora) {
        List<Insight> itens = new ArrayList<>();
        long semPremium = base.stream().filter(item -> item.beneficiosPremium() == 0).count();
        if (semPremium > 0) {
            itens.add(new Insight(
                    "SEM_PREMIUM",
                    semPremium + " anúncio" + (semPremium == 1 ? "" : "s") + " sem Premium vigente",
                    "Base publicada elegível para análise comercial, sem ativação automática.",
                    "/admin/beneficios-premium"));
        }
        long vencendo = repository.countBeneficiosVencendo(
                agora,
                agora.plusDays(PremiumExpiracaoPolicyService.JANELA_VENCENDO_DIAS));
        if (vencendo > 0) {
            itens.add(new Insight(
                    "PREMIUM_VENCENDO",
                    vencendo + " benefício" + (vencendo == 1 ? "" : "s") + " vencendo em breve",
                    "Janela canônica de "
                            + PremiumExpiracaoPolicyService.JANELA_VENCENDO_DIAS
                            + " dias para renovação.",
                    "/admin/beneficios-premium"));
        }
        long altoTrafegoSemPremium = base.stream()
                .filter(item -> item.visualizacoes() != null
                        && item.visualizacoes() >= MINIMO_VIEWS_ALTO_TRAFEGO)
                .filter(item -> item.beneficiosPremium() == 0)
                .count();
        if (altoTrafegoSemPremium > 0) {
            itens.add(new Insight(
                    "ALTO_TRAFEGO_SEM_PREMIUM",
                    altoTrafegoSemPremium + " anúncio"
                            + (altoTrafegoSemPremium == 1 ? "" : "s")
                            + " com alto tráfego e sem Premium",
                    "Recorte de monetização comprovado, sem ativar benefício ou consumir saldo.",
                    "/admin/anuncios?ordenacao=MAIS_VISUALIZACOES"));
        }
        return List.copyOf(itens);
    }

    private List<CidadeDesempenho> cidades(List<AnuncioDesempenhoRow> base) {
        Map<String, Acumulador> grupos = new LinkedHashMap<>();
        for (AnuncioDesempenhoRow item : base) {
            String chave = texto(item.cidade(), "Não informada") + "|" + texto(item.uf(), "--");
            Acumulador atual = grupos.computeIfAbsent(
                    chave,
                    ignorada -> new Acumulador(
                            texto(item.cidade(), "Não informada"),
                            item.cidadeSlug(),
                            texto(item.uf(), "--")));
            atual.adicionar(item);
        }
        return grupos.values().stream()
                .map(Acumulador::cidadeDto)
                .sorted(Comparator.comparingLong(CidadeDesempenho::visualizacoes)
                        .reversed()
                        .thenComparing(CidadeDesempenho::cidade))
                .toList();
    }

    private List<ClassificacaoDesempenho> classificacoes(List<AnuncioDesempenhoRow> base) {
        Map<String, Acumulador> grupos = new LinkedHashMap<>();
        for (AnuncioDesempenhoRow item : base) {
            String classificacao = "RESTRITA_18".equals(item.classificacao())
                    ? "RESTRITA_18"
                    : "LIVRE";
            grupos.computeIfAbsent(classificacao, ignorada -> new Acumulador(classificacao, null, null))
                    .adicionar(item);
        }
        return grupos.values().stream()
                .map(Acumulador::classificacaoDto)
                .sorted(Comparator.comparing(ClassificacaoDesempenho::classificacao))
                .toList();
    }

    private java.util.Optional<CidadeDesempenho> cidadeAbaixoDaMedia(
            List<CidadeDesempenho> cidades) {
        BigDecimal media = mediaConversao(cidades);
        BigDecimal limite = media.multiply(FATOR_CIDADE_ABAIXO_MEDIA);
        return cidades.stream()
                .filter(item -> item.anunciosPublicadosAtivos() >= MINIMO_ANUNCIOS_CIDADE_ALERTA)
                .filter(item -> item.visualizacoes() > 0)
                .filter(item -> item.conversaoPct().compareTo(limite) < 0)
                .min(Comparator.comparing(CidadeDesempenho::conversaoPct));
    }

    private BigDecimal mediaConversao(List<CidadeDesempenho> cidades) {
        long visualizacoes = cidades.stream().mapToLong(CidadeDesempenho::visualizacoes).sum();
        long cliques = cidades.stream().mapToLong(CidadeDesempenho::cliquesWhatsapp).sum();
        return conversao(visualizacoes, cliques);
    }

    private Comparator<AnuncioDesempenhoRow> comparadorTopConversao() {
        return Comparator.comparing(this::conversaoRow)
                .reversed()
                .thenComparing(Comparator.comparingLong(AnuncioDesempenhoRow::cliques).reversed())
                .thenComparing(Comparator.comparingLong(
                                (AnuncioDesempenhoRow item) -> item.visualizacoes())
                        .reversed())
                .thenComparing(AnuncioDesempenhoRow::anuncioId);
    }

    private Comparator<AnuncioDesempenhoRow> comparadorPiorConversao() {
        return Comparator.comparing(this::conversaoRow)
                .thenComparing(Comparator.comparingLong((AnuncioDesempenhoRow item) -> item.visualizacoes()).reversed())
                .thenComparing(AnuncioDesempenhoRow::anuncioId);
    }

    private BigDecimal conversaoRow(AnuncioDesempenhoRow item) {
        return conversao(item.visualizacoes() == null ? 0 : item.visualizacoes(), item.cliques());
    }

    private AnuncioDesempenho toDto(AnuncioDesempenhoRow item) {
        long visualizacoes = item.visualizacoes() == null ? 0 : item.visualizacoes();
        return new AnuncioDesempenho(
                item.anuncioId(),
                item.titulo(),
                item.slug(),
                item.cidade(),
                item.uf(),
                visualizacoes,
                item.cliques(),
                conversao(visualizacoes, item.cliques()));
    }

    private Map<UUID, String> resolverMiniaturas(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AnuncioMidiaEntity> midias = midiaRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoRepository.findByIdIn(midias.values().stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, String> urls = new LinkedHashMap<>();
        for (var entry : midias.entrySet()) {
            ArquivoMidiaEntity arquivo = arquivos.get(entry.getValue().getArquivoMidiaId());
            String url = "RESTRITA_18".equals(String.valueOf(entry.getValue().getVisibilidadeMidia()))
                    ? midiaUrlService.resolverPreviewRestrita(arquivo).urlPublica()
                    : midiaUrlService.resolver(entry.getValue(), arquivo).urlPublica();
            if (url != null && !url.isBlank()) {
                urls.put(entry.getKey(), url);
            }
        }
        return Map.copyOf(urls);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static ResumoDia resumo(PontoDiario ponto) {
        return new ResumoDia(
                ponto.data(),
                ponto.visualizacoes(),
                ponto.cliquesWhatsapp(),
                ponto.conversaoPct());
    }

    private static BigDecimal conversao(long visualizacoes, long cliques) {
        if (visualizacoes <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(cliques)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(visualizacoes), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal variacao(long atual, long anterior) {
        if (anterior == 0) {
            return BigDecimal.valueOf(atual > 0 ? 100 : 0).setScale(2);
        }
        return BigDecimal.valueOf(atual - anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(anterior), 2, RoundingMode.HALF_UP);
    }

    private static String texto(String valor, String fallback) {
        return valor == null || valor.isBlank() ? fallback : valor;
    }

    private static String percentualTexto(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString() + "%";
    }

    private static final class Acumulador {
        private final String nome;
        private final String slug;
        private final String uf;
        private long anuncios;
        private long visualizacoes;
        private long cliques;

        private Acumulador(String nome, String slug, String uf) {
            this.nome = nome;
            this.slug = slug;
            this.uf = uf;
        }

        private void adicionar(AnuncioDesempenhoRow item) {
            anuncios++;
            if (item.visualizacoes() != null) {
                visualizacoes += item.visualizacoes();
                cliques += item.cliques();
            }
        }

        private CidadeDesempenho cidadeDto() {
            return new CidadeDesempenho(
                    nome,
                    slug,
                    uf,
                    anuncios,
                    visualizacoes,
                    cliques,
                    conversao(visualizacoes, cliques));
        }

        private ClassificacaoDesempenho classificacaoDto() {
            return new ClassificacaoDesempenho(
                    nome,
                    anuncios,
                    visualizacoes,
                    cliques,
                    conversao(visualizacoes, cliques));
        }
    }
}
