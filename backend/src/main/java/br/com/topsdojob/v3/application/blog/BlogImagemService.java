package br.com.topsdojob.v3.application.blog;

import br.com.topsdojob.v3.application.blog.BlogImagemProcessor.ImagemProcessada;
import br.com.topsdojob.v3.application.blog.dto.BlogImagemDto;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.blog.BlogImagemEntity;
import br.com.topsdojob.v3.persistence.repository.blog.BlogImagemRepository;
import br.com.topsdojob.v3.persistence.repository.blog.BlogPostRepository;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlogImagemService {

  private static final Set<String> TIPOS = Set.of("CAPA", "OG");

  private final BlogImagemRepository repository;
  private final BlogPostRepository postRepository;
  private final BlogImagemProcessor processor;
  private final ObjectStorage storage;
  private final R2StorageProperties properties;

  public BlogImagemService(
      BlogImagemRepository repository,
      BlogPostRepository postRepository,
      BlogImagemProcessor processor,
      ObjectStorage storage,
      R2StorageProperties properties) {
    this.repository = repository;
    this.postRepository = postRepository;
    this.processor = processor;
    this.storage = storage;
    this.properties = properties;
  }

  @Transactional
  public BlogImagemDto enviar(MultipartFile file, String tipo, UUID atorId) {
    if (atorId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
    }
    String tipoSeguro = tipo == null ? "" : tipo.trim().toUpperCase();
    if (!TIPOS.contains(tipoSeguro)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo de imagem editorial invalido");
    }
    ImagemProcessada imagem = processor.processar(file);
    UUID id = UUID.randomUUID();
    String key = properties.getPrivateMediaPrefix()
        + "blog/"
        + id
        + "/"
        + imagem.sha256()
        + "."
        + imagem.extensao();
    ObjectWriteResult write = storage.putIfAbsent(
        StorageArea.PRIVATE_MEDIA,
        key,
        imagem.bytes(),
        imagem.mimeType());
    if (write != ObjectWriteResult.CREATED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "imagem editorial ja existente");
    }
    removerEmRollback(StorageArea.PRIVATE_MEDIA, key);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    BlogImagemEntity entity = repository.saveAndFlush(BlogImagemEntity.criarPrivada(
        id,
        atorId,
        tipoSeguro,
        key,
        imagem.mimeType(),
        imagem.extensao(),
        imagem.sha256(),
        imagem.bytes().length,
        imagem.largura(),
        imagem.altura(),
        agora));
    return toAdminDto(entity);
  }

  @Transactional(readOnly = true)
  public BlogImagemEntity validarVinculo(
      UUID imagemId,
      String tipo,
      UUID atorId,
      UUID imagemAtualId) {
    if (imagemId == null) {
      return null;
    }
    BlogImagemEntity imagem = repository.findById(imagemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "imagem editorial inexistente"));
    if (!tipo.equals(imagem.getTipo()) || "REMOVIDA".equals(imagem.getEstado())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imagem editorial incompativel");
    }
    boolean jaVinculada = "CAPA".equals(tipo)
        ? postRepository.existsByImagemCapaId(imagemId)
        : postRepository.existsByImagemOgId(imagemId);
    if (jaVinculada && !imagemId.equals(imagemAtualId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "imagem editorial ja vinculada");
    }
    if (!jaVinculada && !imagem.getCriadoPorUsuarioId().equals(atorId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "imagem editorial pertence a outra sessao");
    }
    return imagem;
  }

  @Transactional
  public BlogImagemEntity promover(BlogImagemEntity imagem, OffsetDateTime agora) {
    if (imagem == null || "PUBLICA".equals(imagem.getEstado())) {
      return imagem;
    }
    var source = storage.get(StorageArea.PRIVATE_MEDIA, imagem.getPrivateObjectKey());
    String publicKey = properties.getPublicMediaPrefix()
        + "blog/"
        + imagem.getId()
        + "/"
        + imagem.getSha256()
        + "."
        + imagem.getExtensao();
    ObjectWriteResult result = storage.putIfAbsent(
        StorageArea.PUBLIC_MEDIA,
        publicKey,
        source.content(),
        imagem.getMimeType());
    if (result == ObjectWriteResult.CREATED) {
      removerEmRollback(StorageArea.PUBLIC_MEDIA, publicKey);
    }
    imagem.publicar(publicKey, agora);
    return repository.save(imagem);
  }

  @Transactional
  public void retirarDoPublico(BlogImagemEntity imagem, OffsetDateTime agora) {
    if (imagem == null || imagem.getPublicObjectKey() == null) {
      return;
    }
    String key = imagem.getPublicObjectKey();
    imagem.retirarDoPublico(agora);
    repository.save(imagem);
    removerDepoisDoCommit(StorageArea.PUBLIC_MEDIA, key);
  }

  public String urlPublica(BlogImagemEntity imagem) {
    if (imagem == null || imagem.getPublicObjectKey() == null) {
      return null;
    }
    return storage.publicUrl(StorageArea.PUBLIC_MEDIA, imagem.getPublicObjectKey())
        .map(URI::toString)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "URL publica do Blog nao configurada"));
  }

  public String urlAdmin(BlogImagemEntity imagem) {
    if (imagem == null) {
      return null;
    }
    if (imagem.getPublicObjectKey() != null) {
      return urlPublica(imagem);
    }
    return storage.temporaryGetUrl(
        StorageArea.PRIVATE_MEDIA,
        imagem.getPrivateObjectKey(),
        Duration.ofMinutes(10)).toString();
  }

  @Transactional(readOnly = true)
  public BlogImagemEntity buscar(UUID id) {
    if (id == null) {
      return null;
    }
    return repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "imagem editorial nao encontrada"));
  }

  private BlogImagemDto toAdminDto(BlogImagemEntity imagem) {
    return new BlogImagemDto(
        imagem.getId(),
        imagem.getTipo(),
        urlAdmin(imagem),
        imagem.getMimeType(),
        imagem.getSha256(),
        imagem.getTamanhoBytes(),
        imagem.getLargura(),
        imagem.getAltura());
  }

  private void removerEmRollback(StorageArea area, String key) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) {
          storage.delete(area, key);
        }
      }
    });
  }

  private void removerDepoisDoCommit(StorageArea area, String key) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      storage.delete(area, key);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        storage.delete(area, key);
      }
    });
  }
}
