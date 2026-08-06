package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuStoryGerenciadoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeusStoriesPaginaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemEncerramentoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeusStoriesConsultaService {

  private final MeusAnunciosConsultaService usuarioService;
  private final StoryAnuncioRepository storyRepository;
  private final AnuncioMidiaRepository midiaRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AnuncioRepository anuncioRepository;
  private final Clock clock;

  @Autowired
  public MeusStoriesConsultaService(
      MeusAnunciosConsultaService usuarioService,
      StoryAnuncioRepository storyRepository,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      AnuncioRepository anuncioRepository) {
    this(
        usuarioService,
        storyRepository,
        midiaRepository,
        arquivoRepository,
        anuncioRepository,
        Clock.systemUTC());
  }

  MeusStoriesConsultaService(
      MeusAnunciosConsultaService usuarioService,
      StoryAnuncioRepository storyRepository,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      AnuncioRepository anuncioRepository,
      Clock clock) {
    this.usuarioService = usuarioService;
    this.storyRepository = storyRepository;
    this.midiaRepository = midiaRepository;
    this.arquivoRepository = arquivoRepository;
    this.anuncioRepository = anuncioRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public MeusStoriesPaginaDto listar(
      int pagina,
      int tamanho,
      Authentication authentication) {
    UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
    Page<StoryAnuncioEntity> stories = storyRepository
        .findByCriadoPorOrderByCriadoEmDescIdDesc(
            usuarioId,
            PageRequest.of(paginaSegura(pagina), tamanhoSeguro(tamanho)));
    return new MeusStoriesPaginaDto(
        mapear(stories.getContent()),
        stories.getNumber(),
        stories.getSize(),
        stories.getTotalElements(),
        stories.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<MeuStoryGerenciadoDto> mapear(List<StoryAnuncioEntity> stories) {
    if (stories == null || stories.isEmpty()) {
      return List.of();
    }
    Set<UUID> vinculoIds = stories.stream()
        .map(StoryAnuncioEntity::getAnuncioMidiaId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<UUID, AnuncioMidiaEntity> vinculos = midiaRepository.findByIdIn(vinculoIds).stream()
        .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));

    Set<UUID> arquivoIds = stories.stream()
        .map(StoryAnuncioEntity::getArquivoMidiaId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    vinculos.values().stream()
        .map(AnuncioMidiaEntity::getArquivoMidiaId)
        .filter(java.util.Objects::nonNull)
        .forEach(arquivoIds::add);
    Map<UUID, ArquivoMidiaEntity> arquivos = arquivoRepository.findByIdIn(arquivoIds).stream()
        .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));

    Set<UUID> anuncioIds = stories.stream()
        .filter(item -> item.getModoConteudoEfetivo() == ModoConteudoStory.ANUNCIO)
        .map(StoryAnuncioEntity::getAnuncioId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<UUID, AnuncioEntity> anuncios = anuncioRepository.findAllById(anuncioIds).stream()
        .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));

    OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    return stories.stream()
        .map(story -> toDto(story, vinculos, arquivos, anuncios, agora))
        .toList();
  }

  private MeuStoryGerenciadoDto toDto(
      StoryAnuncioEntity story,
      Map<UUID, AnuncioMidiaEntity> vinculos,
      Map<UUID, ArquivoMidiaEntity> arquivos,
      Map<UUID, AnuncioEntity> anuncios,
      OffsetDateTime agora) {
    ModoConteudoStory modo = story.getModoConteudoEfetivo();
    AnuncioMidiaEntity vinculo = story.getAnuncioMidiaId() == null
        ? null
        : vinculos.get(story.getAnuncioMidiaId());
    UUID arquivoId = story.getArquivoMidiaId() != null
        ? story.getArquivoMidiaId()
        : vinculo == null ? null : vinculo.getArquivoMidiaId();
    ArquivoMidiaEntity arquivo = arquivoId == null ? null : arquivos.get(arquivoId);
    boolean encerrado = story.getStatus() == StatusStoryAnuncio.REMOVIDO
        || story.getEncerradoEm() != null;
    boolean expirado = !encerrado
        && (story.getStatus() == StatusStoryAnuncio.EXPIRADO
            || story.getStatus() == StatusStoryAnuncio.PUBLICADO
                && story.getFimEm() != null
                && !story.getFimEm().isAfter(agora));
    boolean midiaInvalida = modo == ModoConteudoStory.MIDIA_UPLOAD
        && (vinculo != null && vinculo.getStatus() != StatusAnuncioMidia.PUBLICAVEL
            || arquivo == null
            || arquivo.getStatusArquivo() != StatusArquivoMidia.VALIDADO);
    boolean falhaTecnica = !encerrado && !expirado && midiaInvalida;
    String estadoGerenciamento =
        estadoGerenciamento(story, encerrado, expirado, falhaTecnica);
    String status = expirado ? StatusStoryAnuncio.EXPIRADO.name() : story.getStatus().name();
    AnuncioEntity anuncio = modo == ModoConteudoStory.ANUNCIO
        ? anuncios.get(story.getAnuncioId())
        : null;
    String estadoMidia = modo == ModoConteudoStory.ANUNCIO
        ? "NAO_APLICAVEL"
        : encerrado ? "REMOVIDA"
            : falhaTecnica ? "FALHA_PUBLICACAO"
                : midiaInvalida ? "INDISPONIVEL" : "DISPONIVEL";
    return new MeuStoryGerenciadoDto(
        story.getId(),
        modo.name(),
        status,
        estadoGerenciamento,
        story.getInicioEm(),
        story.getFimEm(),
        anuncio == null ? null : anuncio.getSlug(),
        anuncio == null ? null : anuncio.getTitulo(),
        estadoMidia,
        falhaTecnica,
        !encerrado && !falhaTecnica && !expirado,
        !encerrado && falhaTecnica,
        story.getEncerradoEm(),
        story.isDireitoPreservado());
  }

  private String estadoGerenciamento(
      StoryAnuncioEntity story,
      boolean encerrado,
      boolean expirado,
      boolean falhaTecnica) {
    boolean descarteTecnico = story.isDireitoPreservado()
        || "FALHA_TECNICA".equals(story.getMotivoEncerramento());
    if (encerrado
        && story.getOrigemEncerramento() == OrigemEncerramentoStory.USUARIO
        && !descarteTecnico) {
      return "ENCERRADO_USUARIO";
    }
    if (encerrado && story.getOrigemEncerramento() == OrigemEncerramentoStory.ADMIN) {
      return "REMOVIDO_ADMIN";
    }
    if (encerrado && descarteTecnico) {
      return "DESCARTADO_FALHA_TECNICA";
    }
    if (encerrado) {
      return "ENCERRADO";
    }
    if (expirado) {
      return "EXPIRADO";
    }
    if (falhaTecnica) {
      return "FALHA_TECNICA";
    }
    return story.getStatus() == StatusStoryAnuncio.PUBLICADO ? "ATIVO" : "OUTRO";
  }

  private int paginaSegura(int pagina) {
    return Math.max(0, pagina);
  }

  private int tamanhoSeguro(int tamanho) {
    return Math.min(50, Math.max(1, tamanho));
  }
}
