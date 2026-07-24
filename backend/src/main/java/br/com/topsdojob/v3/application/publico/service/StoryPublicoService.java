package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.ListaStoriesPublicosDto;
import br.com.topsdojob.v3.application.publico.dto.PoliticaStoryPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.StoryPublicoDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
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
    private final IdadePublicaService idadeService;
    private final MidiaPublicaUrlService urlService;

    public StoryPublicoService(
            AnuncioRepository anuncioRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            StoryAnuncioRepository storyRepository,
            IdadePublicaService idadeService,
            MidiaPublicaUrlService urlService) {
        this.anuncioRepository = anuncioRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.storyRepository = storyRepository;
        this.idadeService = idadeService;
        this.urlService = urlService;
    }

    @Transactional(readOnly = true)
    public ListaStoriesPublicosDto listar(String slug, HttpServletRequest request) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        boolean idadeConfirmada = idadeService.idadeConfirmada(request);
        AnuncioEntity anuncio = anuncioRepository
                .findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                        slugSeguro,
                        StatusAnuncio.PUBLICADO,
                        StatusModeracaoAnuncio.APROVADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));

        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioId(anuncio.getId()).stream()
                .filter(this::vinculoStoryElegivel)
                .toList();
        if (vinculos.isEmpty()) {
            return resposta(slugSeguro, idadeConfirmada, List.of());
        }

        Map<UUID, AnuncioMidiaEntity> vinculosPorId = vinculos.stream()
                .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
        Map<UUID, ArquivoMidiaEntity> arquivosPorId = arquivoMidiaRepository.findByIdIn(vinculos.stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<StoryPublicoDto> stories = storyRepository.findByAnuncioMidiaIdIn(vinculosPorId.keySet()).stream()
                .filter(story -> storyElegivel(story, now))
                .map(story -> toDto(
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

    private StoryPublicoDto toDto(
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
                : urlService.resolverPreviewRestrita(arquivo);
        return new StoryPublicoDto(
                story.getOrdem(),
                enumName(vinculo.getTipo()),
                enumName(vinculo.getFinalidade()),
                enumName(vinculo.getVisibilidadeMidia()),
                urlPublica.urlPublica(),
                arquivo.getLargura(),
                arquivo.getAltura(),
                arquivo.getDuracaoMs(),
                arquivo.getMimeType(),
                urlPublica.pendenciaMidia());
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

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
