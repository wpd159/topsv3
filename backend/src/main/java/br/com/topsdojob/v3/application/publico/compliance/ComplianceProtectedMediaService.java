package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceProtectedMediaService {

  private final ComplianceVisitorAccessService accessService;
  private final AnuncioMidiaRepository midiaRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AnuncioRepository anuncioRepository;
  private final StoryAnuncioRepository storyRepository;
  private final UsuarioRepository usuarioRepository;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;

  @Autowired
  public ComplianceProtectedMediaService(
      ComplianceVisitorAccessService accessService,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      AnuncioRepository anuncioRepository,
      StoryAnuncioRepository storyRepository,
      UsuarioRepository usuarioRepository,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties) {
    this.accessService = accessService;
    this.midiaRepository = midiaRepository;
    this.arquivoRepository = arquivoRepository;
    this.anuncioRepository = anuncioRepository;
    this.storyRepository = storyRepository;
    this.usuarioRepository = usuarioRepository;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
  }

  ComplianceProtectedMediaService(
      ComplianceVisitorAccessService accessService,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      AnuncioRepository anuncioRepository,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties) {
    this(
        accessService,
        midiaRepository,
        arquivoRepository,
        anuncioRepository,
        null,
        null,
        storageProvider,
        storageProperties);
  }

  @Transactional(readOnly = true)
  public Conteudo carregar(UUID midiaId, HttpServletRequest request) {
    if (!accessService.autorizado(request, EscopoConteudoVisitante.MIDIA_RESTRITA)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "verificacao reforcada necessaria");
    }
    AnuncioMidiaEntity midia = midiaRepository.findById(midiaId)
        .filter(this::midiaProtegidaPublicavel)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "midia protegida nao encontrada"));
    anuncioRepository.findPublicoComProprietarioAtivoPorId(midia.getAnuncioId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "midia protegida nao encontrada"));
    ArquivoMidiaEntity arquivo = arquivoRepository.findById(midia.getArquivoMidiaId())
        .filter(this::arquivoPrivadoValido)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "midia protegida nao encontrada"));
    return carregarArquivoPrivado(
        arquivo,
        "midia protegida indisponivel",
        "midia protegida nao encontrada");
  }

  @Transactional(readOnly = true)
  public Conteudo carregarStory(UUID storyId, HttpServletRequest request) {
    if (!accessService.autorizado(request, EscopoConteudoVisitante.STORY)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "verificacao reforcada necessaria");
    }
    if (storyRepository == null || usuarioRepository == null) {
      throw storyNaoEncontrado();
    }
    StoryAnuncioEntity story = storyRepository
        .findByIdAndStatus(storyId, StatusStoryAnuncio.PUBLICADO)
        .filter(item -> item.getModoConteudoEfetivo() == ModoConteudoStory.MIDIA_UPLOAD)
        .filter(this::storyAtivo)
        .orElseThrow(this::storyNaoEncontrado);
    ArquivoMidiaEntity arquivo;
    UUID proprietarioId;
    if (story.getArquivoMidiaId() != null) {
      proprietarioId = story.getCriadoPor();
      arquivo = arquivoRepository.findById(story.getArquivoMidiaId()).orElse(null);
    } else {
      AnuncioMidiaEntity vinculo = midiaRepository.findById(story.getAnuncioMidiaId())
          .filter(this::vinculoStoryProtegido)
          .orElseThrow(this::storyNaoEncontrado);
      AnuncioEntity anuncio = anuncioRepository.findById(vinculo.getAnuncioId())
          .filter(item -> item.getRemovidoEm() == null && item.getStatus() != StatusAnuncio.REMOVIDO)
          .orElseThrow(this::storyNaoEncontrado);
      proprietarioId = story.getCriadoPor() == null
          ? anuncio.getUsuarioId()
          : story.getCriadoPor();
      if (!Objects.equals(anuncio.getUsuarioId(), proprietarioId)) {
        throw storyNaoEncontrado();
      }
      arquivo = arquivoRepository.findById(vinculo.getArquivoMidiaId()).orElse(null);
    }
    usuarioRepository.findById(proprietarioId)
        .filter(this::usuarioAtivo)
        .orElseThrow(this::storyNaoEncontrado);
    if (arquivo == null || (story.getArquivoMidiaId() != null
        ? !arquivoStoryDiretoValido(story, arquivo)
        : !arquivoPrivadoValido(arquivo))) {
      throw storyNaoEncontrado();
    }
    return carregarArquivoPrivado(arquivo, "Story indisponivel", "Story nao encontrado");
  }

  private Conteudo carregarArquivoPrivado(
      ArquivoMidiaEntity arquivo,
      String indisponivel,
      String naoEncontrado) {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, indisponivel);
    }
    StoredObject stored;
    try {
      stored = storage.get(StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto());
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, naoEncontrado);
    }
    return new Conteudo(
        stored.content(),
        normalizeMime(stored.contentType(), arquivo.getMimeType()));
  }

  private boolean midiaProtegidaPublicavel(AnuncioMidiaEntity midia) {
    return midia.getStatus() == StatusAnuncioMidia.PUBLICAVEL
        && midia.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18;
  }

  private boolean vinculoStoryProtegido(AnuncioMidiaEntity midia) {
    return midia.getStatus() == StatusAnuncioMidia.PUBLICAVEL
        && (midia.getTipo() == TipoAnuncioMidia.STORY
            || midia.getFinalidade() == FinalidadeAnuncioMidia.STORY)
        && midia.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18;
  }

  private boolean storyAtivo(StoryAnuncioEntity story) {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    return story.getEncerradoEm() == null
        && (story.getInicioEm() == null || !story.getInicioEm().isAfter(agora))
        && (story.getFimEm() == null || story.getFimEm().isAfter(agora));
  }

  private boolean usuarioAtivo(UsuarioEntity usuario) {
    return usuario.getStatus() == StatusUsuario.ATIVO
        && usuario.getDesativadoEm() == null
        && usuario.getExcluidoEm() == null;
  }

  private boolean arquivoPrivadoValido(ArquivoMidiaEntity arquivo) {
    return arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO
        && "R2".equals(arquivo.getStorageProvider())
        && Objects.equals(storageProperties.getPrivateMediaBucket(), arquivo.getBucket())
        && arquivo.getChaveObjeto() != null
        && arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix());
  }

  private boolean arquivoStoryDiretoValido(
      StoryAnuncioEntity story,
      ArquivoMidiaEntity arquivo) {
    if (!arquivoPrivadoValido(arquivo)
        || story.getId() == null
        || story.getCriadoPor() == null
        || story.getArquivoMidiaId() == null
        || arquivo.getId() == null
        || !Objects.equals(story.getArquivoMidiaId(), arquivo.getId())) {
      return false;
    }
    String prefixoEsperado = storageProperties.getPrivateMediaPrefix()
        + "stories/contas/" + story.getCriadoPor() + "/" + story.getId()
        + "/" + arquivo.getId() + "/";
    return arquivo.getChaveObjeto().startsWith(prefixoEsperado)
        && !arquivo.getChaveObjeto().startsWith(storageProperties.getDocumentPrefix());
  }

  private String normalizeMime(String storedMime, String persistedMime) {
    String value = storedMime == null || storedMime.isBlank()
        ? persistedMime
        : storedMime;
    if (value == null || value.isBlank()) {
      return "application/octet-stream";
    }
    String normalized = value.split(";", 2)[0].trim().toLowerCase();
    return normalized.startsWith("image/") || normalized.startsWith("video/")
        ? normalized
        : "application/octet-stream";
  }

  private ResponseStatusException storyNaoEncontrado() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Story nao encontrado");
  }

  public record Conteudo(byte[] bytes, String mimeType) {

    public Conteudo {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }
}
