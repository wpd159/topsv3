package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioBeneficioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnuncianteRecomendacoesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnuncianteRecomendacoesDto.RecomendacaoDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PainelAnuncianteRecomendacoesService {

    private static final Set<String> ESTADOS_INELEGIVEIS = Set.of("BLOQUEADO", "REMOVIDO", "REJEITADO");
    private static final long JANELA_RENOVACAO_HORAS = 48;

    private final MeusAnunciosConsultaService meusAnunciosService;

    public PainelAnuncianteRecomendacoesService(MeusAnunciosConsultaService meusAnunciosService) {
        this.meusAnunciosService = meusAnunciosService;
    }

    @Transactional(readOnly = true)
    public PainelAnuncianteRecomendacoesDto consultar(Authentication authentication) {
        UUID usuarioId = meusAnunciosService.usuarioAutenticado(authentication).getId();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<RecomendacaoComPrioridade> calculadas = meusAnunciosService.listarDoUsuario(usuarioId).stream()
                .filter(this::elegivel)
                .flatMap(anuncio -> recomendacoes(anuncio, agora).stream())
                .sorted(Comparator
                        .comparingInt(RecomendacaoComPrioridade::prioridade)
                        .thenComparing(item -> item.recomendacao().anuncioTitulo(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.recomendacao().tipo()))
                .limit(6)
                .toList();
        return new PainelAnuncianteRecomendacoesDto(
                true,
                calculadas.stream().map(RecomendacaoComPrioridade::recomendacao).toList());
    }

    private List<RecomendacaoComPrioridade> recomendacoes(MeuAnuncioDto anuncio, OffsetDateTime agora) {
        List<RecomendacaoComPrioridade> itens = new ArrayList<>();
        String editar = "/meus-anuncios/" + anuncio.slug() + "/editar";
        String monetizar = "/meus-anuncios/" + anuncio.slug() + "/monetizar";
        int descricao = anuncio.descricao() == null ? 0 : anuncio.descricao().trim().length();
        long fotos = anuncio.midias().stream()
                .filter(item -> "FOTO".equals(item.tipo()))
                .filter(this::disponivel)
                .count();
        boolean cidade = anuncio.localizacao() != null && preenchido(anuncio.localizacao().cidade());
        boolean bairro = anuncio.localizacao() != null && preenchido(anuncio.localizacao().bairro());

        if (descricao < 220 || !cidade || !bairro) {
            String detalhe = !cidade || !bairro
                    ? "Complete cidade, bairro e descricao para deixar o perfil mais informativo."
                    : "Amplie a descricao para apresentar melhor seus diferenciais e servicos.";
            itens.add(item(anuncio, "PERFIL", "Melhore as informacoes do anuncio", detalhe,
                    "Editar anuncio", editar, 10));
        }
        if (fotos < 8) {
            itens.add(item(anuncio, "FOTOS", "Complete a galeria de fotos",
                    "Este anuncio possui " + fotos + " foto(s). Uma galeria mais completa apresenta melhor o perfil.",
                    "Gerenciar fotos", editar, 20));
        }

        if ("PUBLICADO".equals(anuncio.status())) {
            anuncio.beneficiosPremium().stream()
                    .filter(item -> "ATIVO".equals(item.status()))
                    .filter(item -> item.fimEm() != null)
                    .filter(item -> item.fimEm().isAfter(agora))
                    .filter(item -> !item.fimEm().isAfter(agora.plusHours(JANELA_RENOVACAO_HORAS)))
                    .min(Comparator.comparing(MeuAnuncioBeneficioDto::fimEm))
                    .ifPresent(item -> itens.add(item(anuncio, "RENOVACAO", "Renove um beneficio em breve",
                            "O beneficio " + item.nome() + " esta proximo do fim da vigencia.",
                            "Ver opcoes", monetizar, 5)));
            Set<String> ativos = anuncio.beneficiosPremium().stream()
                    .filter(item -> Set.of("ATIVO", "PENDENTE", "AGUARDANDO_MODERACAO")
                            .contains(item.status()))
                    .map(MeuAnuncioBeneficioDto::codigo)
                    .collect(java.util.stream.Collectors.toSet());
            boolean possuiVideo = anuncio.midias().stream()
                    .anyMatch(item -> "VIDEO".equals(item.tipo()) && disponivel(item));
            if (!possuiVideo && !ativos.contains("VIDEO_1")) {
                itens.add(item(anuncio, "VIDEO", "Adicione video ao anuncio",
                        "O beneficio de video nao esta ativo neste anuncio.",
                        "Ver opcoes", monetizar, 30));
            }
            if (fotos > 1 && !ativos.contains("CARROSSEL_FOTOS")) {
                itens.add(item(anuncio, "CARROSSEL", "Habilite o carrossel de fotos",
                        "A galeria ja possui fotos suficientes, mas o carrossel nao esta ativo.",
                        "Ver opcoes", monetizar, 40));
            }
            if (!ativos.contains("ANUNCIO_TOPO")) {
                itens.add(item(anuncio, "IMPULSIONAMENTO", "Destaque o anuncio nas listagens",
                        "O beneficio de anuncio no topo nao esta ativo.",
                        "Ver opcoes", monetizar, 50));
            }
        }
        return itens;
    }

    private RecomendacaoComPrioridade item(
            MeuAnuncioDto anuncio,
            String tipo,
            String titulo,
            String descricao,
            String acaoRotulo,
            String acaoHref,
            int prioridade) {
        return new RecomendacaoComPrioridade(
                prioridade,
                new RecomendacaoDto(
                        tipo.toLowerCase(java.util.Locale.ROOT) + ":" + anuncio.id(),
                        anuncio.id(),
                        anuncio.slug(),
                        anuncio.titulo(),
                        tipo,
                        titulo,
                        descricao,
                        acaoRotulo,
                        acaoHref));
    }

    private boolean elegivel(MeuAnuncioDto anuncio) {
        return anuncio != null
                && anuncio.id() != null
                && preenchido(anuncio.slug())
                && preenchido(anuncio.titulo())
                && !ESTADOS_INELEGIVEIS.contains(anuncio.status());
    }

    private boolean disponivel(MeuAnuncioMidiaDto item) {
        return !"REMOVIDA".equals(item.status()) && !"REJEITADA".equals(item.status());
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private record RecomendacaoComPrioridade(int prioridade, RecomendacaoDto recomendacao) {
    }
}
