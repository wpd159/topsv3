package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class MeuAnuncioStoryConsultaService {

  private final StoryAnuncioRepository storyRepository;
  private final AnuncioMidiaRepository midiaRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final Clock clock;

  @Autowired
  public MeuAnuncioStoryConsultaService(
      StoryAnuncioRepository storyRepository,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository) {
    this(storyRepository, midiaRepository, arquivoRepository, Clock.systemUTC());
  }

  MeuAnuncioStoryConsultaService(
      StoryAnuncioRepository storyRepository,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      Clock clock) {
    this.storyRepository = storyRepository;
    this.midiaRepository = midiaRepository;
    this.arquivoRepository = arquivoRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Map<UUID, MeuAnuncioStoryDto> consultarAtivos(Collection<UUID> anuncioIds) {
    List<UUID> ids = anuncioIds == null ? List.of() : anuncioIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    List<StoryAnuncioEntity> stories = storyRepository.findByAnuncioIds(ids).stream()
        .filter(story -> ativo(story, agora))
        .sorted(Comparator
            .comparing(StoryAnuncioEntity::getInicioEm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StoryAnuncioEntity::getId))
        .toList();
    if (stories.isEmpty()) {
      return Map.of();
    }

    Map<UUID, AnuncioMidiaEntity> midias = midiaRepository.findByIdIn(stories.stream()
            .map(StoryAnuncioEntity::getAnuncioMidiaId)
            .filter(Objects::nonNull)
            .distinct()
            .toList()).stream()
        .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
    Map<UUID, ArquivoMidiaEntity> arquivos = arquivoRepository.findByIdIn(midias.values().stream()
            .map(AnuncioMidiaEntity::getArquivoMidiaId)
            .filter(Objects::nonNull)
            .distinct()
            .toList()).stream()
        .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));

    Map<UUID, MeuAnuncioStoryDto> resposta = new LinkedHashMap<>();
    for (StoryAnuncioEntity story : stories) {
      AnuncioMidiaEntity midia = midias.get(story.getAnuncioMidiaId());
      UUID anuncioId = story.getAnuncioId() != null
          ? story.getAnuncioId()
          : midia == null ? null : midia.getAnuncioId();
      if (anuncioId != null) {
        resposta.putIfAbsent(anuncioId, toDto(story, anuncioId, midia, arquivos));
      }
    }
    return Map.copyOf(resposta);
  }

  MeuAnuncioStoryDto consultar(StoryAnuncioEntity story) {
    AnuncioMidiaEntity midia = story.getAnuncioMidiaId() == null
        ? null
        : midiaRepository.findById(story.getAnuncioMidiaId()).orElse(null);
    UUID anuncioId = story.getAnuncioId() != null
        ? story.getAnuncioId()
        : midia == null ? null : midia.getAnuncioId();
    Map<UUID, ArquivoMidiaEntity> arquivos = midia == null
        ? Map.of()
        : arquivoRepository.findById(midia.getArquivoMidiaId())
            .map(item -> Map.of(item.getId(), item))
            .orElseGet(Map::of);
    return toDto(story, anuncioId, midia, arquivos);
  }

  private MeuAnuncioStoryDto toDto(
      StoryAnuncioEntity story,
      UUID anuncioId,
      AnuncioMidiaEntity midia,
      Map<UUID, ArquivoMidiaEntity> arquivos) {
    ArquivoMidiaEntity arquivo = midia == null ? null : arquivos.get(midia.getArquivoMidiaId());
    String tipoMidia = arquivo == null || arquivo.getMimeType() == null
        ? null
        : arquivo.getMimeType().toLowerCase().startsWith("video/") ? "VIDEO" : "FOTO";
    String estadoMidia = story.getAnuncioMidiaId() == null
        ? null
        : midia != null
            && midia.getStatus() == StatusAnuncioMidia.PUBLICAVEL
            && arquivo != null
            && arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO
                ? "DISPONIVEL"
                : "INDISPONIVEL";
    return new MeuAnuncioStoryDto(
        story.getId(),
        anuncioId,
        story.getModoConteudoEfetivo().name(),
        tipoMidia,
        story.getStatus().name(),
        story.getInicioEm(),
        story.getFimEm(),
        estadoMidia);
  }

  private boolean ativo(StoryAnuncioEntity story, OffsetDateTime agora) {
    return story != null
        && story.getStatus() == StatusStoryAnuncio.PUBLICADO
        && (story.getInicioEm() == null || !story.getInicioEm().isAfter(agora))
        && (story.getFimEm() == null || story.getFimEm().isAfter(agora));
  }
}
