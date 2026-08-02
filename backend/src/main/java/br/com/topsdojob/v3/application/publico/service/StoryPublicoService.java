package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.ListaStoriesPublicosDto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.dto.PoliticaStoryPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.StoryPublicoDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoryPublicoService {

    private static final String MOTIVO_AUTORIZADO = "STORIES_AUTORIZADOS";
    private static final String MOTIVO_IDADE_NAO_CONFIRMADA = "IDADE_NAO_CONFIRMADA";

    private final AnuncioRepository anuncioRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final StoryAnuncioRepository storyRepository;
    private final UsuarioRepository usuarioRepository;
    private final ComplianceVisitorAccessService visitorAccessService;
    private final MidiaPublicaUrlService urlService;
    private final StoryAnuncioApresentacaoService apresentacaoService;

    @Autowired
    public StoryPublicoService(
            AnuncioRepository anuncioRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            StoryAnuncioRepository storyRepository,
            UsuarioRepository usuarioRepository,
            ComplianceVisitorAccessService visitorAccessService,
            MidiaPublicaUrlService urlService,
            StoryAnuncioApresentacaoService apresentacaoService) {
        this.anuncioRepository = anuncioRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.storyRepository = storyRepository;
        this.usuarioRepository = usuarioRepository;
        this.visitorAccessService = visitorAccessService;
        this.urlService = urlService;
        this.apresentacaoService = apresentacaoService;
    }

    StoryPublicoService(
            AnuncioRepository anuncioRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            StoryAnuncioRepository storyRepository,
            UsuarioRepository usuarioRepository,
            ComplianceVisitorAccessService visitorAccessService,
            MidiaPublicaUrlService urlService) {
        this(
                anuncioRepository, anuncioMidiaRepository, arquivoMidiaRepository,
                storyRepository, usuarioRepository, visitorAccessService, urlService, null);
    }

    @Transactional(readOnly = true)
    public ListaStoriesPublicosDto listar(String slug, HttpServletRequest request) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        boolean idadeConfirmada = visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY);
        AnuncioEntity anuncio = anuncioRepository
                .findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                        slugSeguro,
                        StatusAnuncio.PUBLICADO,
                        StatusModeracaoAnuncio.APROVADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        if (!proprietarioAtivo(anuncio)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<StoryAnuncioEntity> storiesAtivos = storyRepository.findByAnuncioIds(List.of(anuncio.getId())).stream()
                .filter(story -> storyElegivel(story, now))
                .toList();
        if (storiesAtivos.isEmpty()) {
            return resposta(slugSeguro, idadeConfirmada, List.of());
        }

        Map<UUID, AnuncioMidiaEntity> vinculosPorId = anuncioMidiaRepository.findByIdIn(storiesAtivos.stream()
                        .map(StoryAnuncioEntity::getAnuncioMidiaId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .filter(this::vinculoStoryElegivel)
                .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
        Map<UUID, ArquivoMidiaEntity> arquivosPorId = arquivoMidiaRepository.findByIdIn(vinculosPorId.values().stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));

        List<StoryPublicoDto> stories = storiesAtivos.stream()
                .map(story -> story.getModoConteudo() == ModoConteudoStory.ANUNCIO
                        ? toDtoAnuncio(story, anuncio, idadeConfirmada)
                        : toDtoMidia(
                                story,
                                vinculosPorId.get(story.getAnuncioMidiaId()),
                                arquivosPorId,
                                idadeConfirmada))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(
                        StoryPublicoDto::ordem,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();

        return resposta(slugSeguro, idadeConfirmada, stories);
    }

    private ListaStoriesPublicosDto resposta(
            String slug,
            boolean idadeConfirmada,
            List<StoryPublicoDto> stories) {
        return new ListaStoriesPublicosDto(
                slug,
                idadeConfirmada,
                idadeConfirmada,
                stories,
                idadeConfirmada
                        ? new PoliticaStoryPublicoDto(
                                true,
                                MOTIVO_AUTORIZADO,
                                MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN)
                        : new PoliticaStoryPublicoDto(
                                false,
                                MOTIVO_IDADE_NAO_CONFIRMADA,
                                MidiaRestritaDerivacaoService.PENDENTE_DERIVACAO_RESTRITA));
    }

    private StoryPublicoDto toDtoMidia(
            StoryAnuncioEntity story,
            AnuncioMidiaEntity vinculo,
            Map<UUID, ArquivoMidiaEntity> arquivosPorId,
            boolean idadeConfirmada) {
        if (vinculo == null || vinculo.getVisibilidadeMidia() != VisibilidadeMidia.RESTRITA_18) {
            return null;
        }
        ArquivoMidiaEntity arquivo = arquivosPorId.get(vinculo.getArquivoMidiaId());
        if (arquivo == null
                || arquivo.getStatusArquivo() != StatusArquivoMidia.VALIDADO) {
            return null;
        }
        MidiaPublicaUrlService.ResultadoUrlPublica urlPublica = idadeConfirmada
                ? urlService.resolver(vinculo, arquivo)
                : new MidiaPublicaUrlService.ResultadoUrlPublica(null, null);
        return new StoryPublicoDto(
                story.getId().toString(),
                story.getModoConteudoEfetivo().name(),
                story.getOrdem(),
                enumName(vinculo.getTipo()),
                enumName(vinculo.getFinalidade()),
                enumName(vinculo.getVisibilidadeMidia()),
                urlPublica.urlPublica(),
                arquivo.getLargura(),
                arquivo.getAltura(),
                arquivo.getDuracaoMs(),
                arquivo.getMimeType(),
                urlPublica.pendenciaMidia(),
                null,
                null,
                null,
                null,
                null);
    }

    private StoryPublicoDto toDtoAnuncio(
            StoryAnuncioEntity story,
            AnuncioEntity anuncio,
            boolean idadeConfirmada) {
        StoryAnuncioApresentacaoService.Apresentacao apresentacao = idadeConfirmada
                && apresentacaoService != null
                ? apresentacaoService.apresentar(anuncio)
                : null;
        return new StoryPublicoDto(
                story.getId().toString(),
                ModoConteudoStory.ANUNCIO.name(),
                story.getOrdem(),
                "ANUNCIO",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                idadeConfirmada ? null : MOTIVO_IDADE_NAO_CONFIRMADA,
                apresentacao == null ? null : apresentacao.titulo(),
                apresentacao == null ? null : apresentacao.cidade(),
                apresentacao == null ? null : apresentacao.uf(),
                apresentacao == null ? null : apresentacao.preco(),
                apresentacao == null ? null : apresentacao.resumo());
    }

    private boolean vinculoStoryElegivel(AnuncioMidiaEntity vinculo) {
        return vinculo != null
                && vinculo.getStatus() == StatusAnuncioMidia.PUBLICAVEL
                && (vinculo.getTipo() == TipoAnuncioMidia.STORY || vinculo.getFinalidade() == FinalidadeAnuncioMidia.STORY)
                && vinculo.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18;
    }

    private boolean storyElegivel(StoryAnuncioEntity story, OffsetDateTime now) {
        return story != null
                && story.getStatus() == StatusStoryAnuncio.PUBLICADO
                && (story.getInicioEm() == null || !story.getInicioEm().isAfter(now))
                && (story.getFimEm() == null || story.getFimEm().isAfter(now));
    }

    private boolean proprietarioAtivo(AnuncioEntity anuncio) {
        return usuarioRepository.findById(anuncio.getUsuarioId())
                .filter(this::usuarioAtivo)
                .isPresent();
    }

    private boolean usuarioAtivo(UsuarioEntity usuario) {
        return usuario.getStatus() == StatusUsuario.ATIVO
                && usuario.getDesativadoEm() == null
                && usuario.getExcluidoEm() == null;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
