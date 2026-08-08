package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MinhaContaStoriesPublicacaoService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MinhaContaStoriesPublicacaoService.class);

  private final MeusAnunciosConsultaService usuarioService;
  private final MeuAnuncioStoryConsultaService consultaService;
  private final MinhaContaStoriesDireitoService direitoService;
  private final AnuncioRepository anuncioRepository;
  private final StoryAnuncioRepository storyRepository;
  private final UsuarioRepository usuarioRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final MidiaUploadValidator uploadValidator;
  private final FotoUploadProcessor fotoProcessor;
  private final R2StorageProperties storageProperties;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final StoryUploadCleanupAuditService cleanupAuditService;
  private final Clock clock;

  @Autowired
  public MinhaContaStoriesPublicacaoService(
      MeusAnunciosConsultaService usuarioService,
      MeuAnuncioStoryConsultaService consultaService,
      MinhaContaStoriesDireitoService direitoService,
      AnuncioRepository anuncioRepository,
      StoryAnuncioRepository storyRepository,
      UsuarioRepository usuarioRepository,
      ArquivoMidiaRepository arquivoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      MidiaUploadValidator uploadValidator,
      FotoUploadProcessor fotoProcessor,
      R2StorageProperties storageProperties,
      ObjectProvider<ObjectStorage> storageProvider,
      StoryUploadCleanupAuditService cleanupAuditService) {
    this(
        usuarioService,
        consultaService,
        direitoService,
        anuncioRepository,
        storyRepository,
        usuarioRepository,
        arquivoRepository,
        auditoriaRepository,
        uploadValidator,
        fotoProcessor,
        storageProperties,
        storageProvider,
        cleanupAuditService,
        Clock.systemUTC());
  }

  MinhaContaStoriesPublicacaoService(
      MeusAnunciosConsultaService usuarioService,
      MeuAnuncioStoryConsultaService consultaService,
      MinhaContaStoriesDireitoService direitoService,
      AnuncioRepository anuncioRepository,
      StoryAnuncioRepository storyRepository,
      UsuarioRepository usuarioRepository,
      ArquivoMidiaRepository arquivoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      MidiaUploadValidator uploadValidator,
      FotoUploadProcessor fotoProcessor,
      R2StorageProperties storageProperties,
      ObjectProvider<ObjectStorage> storageProvider,
      StoryUploadCleanupAuditService cleanupAuditService,
      Clock clock) {
    this.usuarioService = usuarioService;
    this.consultaService = consultaService;
    this.direitoService = direitoService;
    this.anuncioRepository = anuncioRepository;
    this.storyRepository = storyRepository;
    this.usuarioRepository = usuarioRepository;
    this.arquivoRepository = arquivoRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.uploadValidator = uploadValidator;
    this.fotoProcessor = fotoProcessor;
    this.storageProperties = storageProperties;
    this.storageProvider = storageProvider;
    this.cleanupAuditService = cleanupAuditService;
    this.clock = clock;
  }

  @Transactional
  public MinhaContaStoryDto publicar(
      String modoConteudo,
      UUID anuncioId,
      List<MultipartFile> arquivos,
      String idempotencyKey,
      Authentication authentication,
      String requestId) {
    UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
    return publicarCanonico(
        modoConteudo,
        anuncioId,
        arquivos,
        idempotencyKey,
        usuarioId,
        null,
        null,
        false,
        requestId);
  }

  @Transactional
  public MinhaContaStoryDto publicarAdministrativamente(
      UUID anuncioId,
      String idempotencyKey,
      AdminUserPrincipal administrador,
      String requestId) {
    validarAdministrador(administrador);
    if (anuncioId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "anuncioId obrigatorio");
    }
    AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    validarAnuncioPublicavel(anuncio);
    UsuarioEntity proprietario = usuarioRepository.findByIdForUpdate(anuncio.getUsuarioId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "proprietario ausente"));
    if (proprietario.getStatus() != StatusUsuario.ATIVO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "proprietario nao esta ativo");
    }
    return publicarCanonico(
        ModoConteudoStory.ANUNCIO.name(),
        anuncioId,
        null,
        idempotencyKey,
        proprietario.getId(),
        anuncio,
        administrador.usuarioId(),
        true,
        requestId);
  }

  private MinhaContaStoryDto publicarCanonico(
      String modoConteudo,
      UUID anuncioId,
      List<MultipartFile> arquivos,
      String idempotencyKey,
      UUID usuarioId,
      AnuncioEntity anuncioBloqueado,
      UUID atorAdministrativo,
      boolean retornarAtivoExistente,
      String requestId) {
    ModoConteudoStory modo = modo(modoConteudo);
    validarContrato(modo, anuncioId, arquivos);
    MultipartFile arquivo = modo == ModoConteudoStory.MIDIA_UPLOAD ? arquivos.get(0) : null;
    String chave = chaveEscopada(modo, anuncioId, idempotencyKey);
    String fingerprint = fingerprint(modo, anuncioId, arquivo);

    StoryAnuncioEntity repetido = (modo == ModoConteudoStory.ANUNCIO
        ? storyRepository.findByCriadoPorAndModoConteudoAndAnuncioIdAndIdempotencyKey(
            usuarioId, modo, anuncioId, chave)
        : storyRepository
            .findByCriadoPorAndModoConteudoAndAnuncioIdIsNullAndIdempotencyKey(
                usuarioId, modo, chave))
            .orElse(null);
    if (repetido != null) {
      validarRepeticao(repetido, modo, anuncioId, fingerprint);
      return consultaService.consultar(repetido);
    }

    OffsetDateTime agora = agoraUtc();
    AnuncioEntity anuncio = modo == ModoConteudoStory.ANUNCIO
        ? (anuncioBloqueado == null ? anuncioParaPublicacao(anuncioId, usuarioId) : anuncioBloqueado)
        : null;
    if (anuncio != null) {
      List<StoryAnuncioEntity> existentes = storyRepository.findByAnuncioIdForUpdate(anuncioId);
      boolean expirou = existentes.stream()
          .map(item -> item.expirarSeVencido(agora))
          .reduce(false, Boolean::logicalOr);
      if (expirou) {
        storyRepository.flush();
      }
      StoryAnuncioEntity ativo = existentes.stream()
          .filter(item -> ativoDoAnuncio(item, agora))
          .findFirst()
          .orElse(null);
      if (ativo != null && retornarAtivoExistente) {
        return consultaService.consultar(ativo);
      }
      if (ativo != null) {
        throw new StoryJaAtivoException();
      }
    }

    var direito = atorAdministrativo == null
        ? direitoService.reservarParaPublicacao(modo, anuncio, usuarioId, agora)
        : direitoService.criarDireitoAdministrativoParaPublicacao(
            anuncio, atorAdministrativo, chave, agora);
    UUID storyId = uuidDeterministico("story", usuarioId, modo, anuncioId, chave);
    UUID arquivoId = modo == ModoConteudoStory.MIDIA_UPLOAD
        ? processarMidia(storyId, arquivo, usuarioId, chave, agora, requestId)
        : null;
    OffsetDateTime publicadoEm = agoraUtc();
    OffsetDateTime fimEm = direitoService.iniciarVigencia(direito, publicadoEm);
    StoryAnuncioEntity story = modo == ModoConteudoStory.ANUNCIO
        ? StoryAnuncioEntity.criarAnuncio(
            storyId,
            anuncioId,
            direito.ativacao().getId(),
            chave,
            fingerprint,
            publicadoEm,
            fimEm,
            usuarioId)
        : StoryAnuncioEntity.criarMidiaUpload(
            storyId,
            arquivoId,
            direito.ativacao().getId(),
            chave,
            fingerprint,
            publicadoEm,
            fimEm,
            usuarioId);
    storyRepository.save(story);
    String depoisJson = "{\"modoConteudo\":\"" + modo.name()
        + "\",\"status\":\"PUBLICADO\"}";
    auditoriaRepository.save(atorAdministrativo == null
        ? AuditoriaEventoEntity.registrarSistema(
            uuidDeterministico("auditoria", usuarioId, modo, anuncioId, chave),
            usuarioId,
            "STORY_PUBLICADO",
            "STORY_ANUNCIO",
            storyId,
            null,
            depoisJson,
            requestId,
            publicadoEm)
        : AuditoriaEventoEntity.registrar(
            uuidDeterministico("auditoria-admin", usuarioId, modo, anuncioId, chave),
            atorAdministrativo,
            "STORY_PUBLICADO_ADMINISTRATIVAMENTE",
            "STORY_ANUNCIO",
            storyId,
            null,
            depoisJson,
            requestId,
            publicadoEm));
    storyRepository.flush();
    return consultaService.consultar(story);
  }

  private AnuncioEntity anuncioParaPublicacao(UUID anuncioId, UUID usuarioId) {
    AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    if (!usuarioId.equals(anuncio.getUsuarioId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "anuncio pertence a outra conta");
    }
    validarAnuncioPublicavel(anuncio);
    return anuncio;
  }

  private void validarAnuncioPublicavel(AnuncioEntity anuncio) {
    if (anuncio.getRemovidoEm() != null
        || anuncio.getStatus() != StatusAnuncio.PUBLICADO
        || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.APROVADO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio nao esta publicavel para Story");
    }
    if (anuncio.getUsuarioId() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "proprietario ausente");
    }
  }

  private void validarAdministrador(AdminUserPrincipal administrador) {
    if (administrador == null
        || !administrador.isEnabled()
        || !administrador.papeis().contains(PapelUsuario.ADMIN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "administrador nao autorizado");
    }
  }

  private UUID processarMidia(
      UUID storyId,
      MultipartFile multipart,
      UUID usuarioId,
      String chave,
      OffsetDateTime agora,
      String requestId) {
    MidiaValidada validada;
    try {
      validada = uploadValidator.validarStory(multipart);
    } catch (ResponseStatusException exception) {
      if (exception.getStatusCode().value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
        LOGGER.warn(
            "story_media_rejected requestId={} validation=unsupported_media mime={} extension={} sizeBytes={}",
            requestId,
            mimeSeguro(multipart),
            extensaoSegura(multipart),
            multipart == null ? 0 : multipart.getSize());
      }
      throw exception;
    }
    FotoProcessada foto = validada.video() ? null : fotoProcessor.processar(validada);
    byte[] bytes = foto == null ? validada.bytes() : foto.bytes();
    String mime = foto == null ? validada.mimeType() : foto.mimeType();
    String extensao = foto == null ? validada.extensao() : foto.extensao();
    Integer largura = foto == null ? validada.largura() : foto.largura();
    Integer altura = foto == null ? validada.altura() : foto.altura();
    String sha = foto == null ? validada.sha256() : foto.sha256();
    UUID arquivoId = uuidDeterministico("arquivo", usuarioId, ModoConteudoStory.MIDIA_UPLOAD, null, chave);
    String nome = validada.video()
        ? "video." + extensao
        : "foto-v" + foto.pipelineVersao() + "." + extensao;
    String key = storageProperties.getPrivateMediaPrefix()
        + "stories/contas/" + usuarioId + "/" + storyId + "/" + arquivoId + "/" + nome;

    ObjectStorage storage = storageObrigatorio();
    ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
        arquivoId,
        "R2",
        storageProperties.getPrivateMediaBucket(),
        key,
        validada.nomeOriginal(),
        mime,
        bytes.length,
        largura,
        altura,
        validada.video() ? Math.toIntExact(validada.duracaoMs()) : null,
        sha,
        agora);
    if (foto != null) {
      arquivo.registrarProcessamento(
          foto.pipelineVersao(),
          foto.marcaDaguaVersao(),
          foto.processadoEm(),
          foto.sha256Origem());
    }
    ArquivoMidiaEntity persistido = arquivoRepository.saveAndFlush(arquivo);
    ObjectWriteResult resultado;
    try {
      resultado = storage.putIfAbsent(StorageArea.PRIVATE_MEDIA, key, bytes, mime);
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de Story indisponivel");
    }
    if (resultado == ObjectWriteResult.CREATED) {
      limparObjetoSeRollback(storage, key, storyId, arquivoId, usuarioId, requestId);
    }
    verificarObjeto(storage, key, bytes, mime, largura, altura, foto != null);
    persistido.aplicarDecisao(StatusArquivoMidia.VALIDADO);
    arquivoRepository.saveAndFlush(persistido);
    return arquivoId;
  }

  private void validarContrato(
      ModoConteudoStory modo,
      UUID anuncioId,
      List<MultipartFile> arquivos) {
    if (modo == ModoConteudoStory.ANUNCIO) {
      if (anuncioId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ANUNCIO exige anuncioId");
      }
      if (arquivos != null && !arquivos.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ANUNCIO nao aceita arquivo");
      }
      return;
    }
    if (anuncioId != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MIDIA_UPLOAD nao aceita anuncioId");
    }
    if (arquivos == null
        || arquivos.size() != 1
        || arquivos.get(0) == null
        || arquivos.get(0).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "envie exatamente uma midia para o Story");
    }
  }

  private void validarRepeticao(
      StoryAnuncioEntity story,
      ModoConteudoStory modo,
      UUID anuncioId,
      String fingerprint) {
    if (story.getModoConteudoEfetivo() != modo
        || !Objects.equals(story.getAnuncioId(), anuncioId)
        || !Objects.equals(story.getRequestFingerprint(), fingerprint)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outra intencao");
    }
  }

  private String chaveEscopada(
      ModoConteudoStory modo,
      UUID anuncioId,
      String value) {
    String cliente = value == null ? "" : value.trim();
    if (!cliente.matches("[A-Za-z0-9._:-]{1,80}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
    }
    return "story-publicacao:" + modo.name() + ":"
        + (anuncioId == null ? "conta" : anuncioId) + ":" + cliente;
  }

  private String fingerprint(
      ModoConteudoStory modo,
      UUID anuncioId,
      MultipartFile arquivo) {
    if (modo == ModoConteudoStory.ANUNCIO) {
      return sha256(("ANUNCIO:" + anuncioId).getBytes(StandardCharsets.UTF_8));
    }
    try (InputStream input = arquivo.getInputStream()) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(("MIDIA_UPLOAD:" + arquivo.getSize() + ":")
          .getBytes(StandardCharsets.UTF_8));
      byte[] buffer = new byte[8192];
      int lidos;
      while ((lidos = input.read(buffer)) >= 0) {
        if (lidos > 0) {
          digest.update(buffer, 0, lidos);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo nao pode ser lido");
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private ModoConteudoStory modo(String value) {
    try {
      return ModoConteudoStory.valueOf(
          value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modoConteudo invalido");
    }
  }

  private boolean ativoDoAnuncio(StoryAnuncioEntity story, OffsetDateTime agora) {
    return story.getModoConteudoEfetivo() == ModoConteudoStory.ANUNCIO
        && story.getStatus() == StatusStoryAnuncio.PUBLICADO
        && story.getEncerradoEm() == null
        && (story.getInicioEm() == null || !story.getInicioEm().isAfter(agora))
        && (story.getFimEm() == null || story.getFimEm().isAfter(agora));
  }

  private ObjectStorage storageObrigatorio() {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de Story indisponivel");
    }
    return storage;
  }

  private void verificarObjeto(
      ObjectStorage storage,
      String key,
      byte[] esperado,
      String mime,
      Integer largura,
      Integer altura,
      boolean foto) {
    try {
      StoredObject objeto = storage.get(StorageArea.PRIVATE_MEDIA, key);
      String mimePersistido = objeto.contentType().split(";", 2)[0]
          .trim()
          .toLowerCase(Locale.ROOT);
      if (!Objects.equals(mime, mimePersistido)
          || !Objects.equals(sha256(esperado), sha256(objeto.content()))) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "objeto do Story diverge da midia processada");
      }
      if (foto) {
        fotoProcessor.validarDerivado(objeto.content(), mime, largura, altura);
      }
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de Story indisponivel");
    }
  }

  private void limparObjetoSeRollback(
      ObjectStorage storage,
      String key,
      UUID storyId,
      UUID arquivoId,
      UUID usuarioId,
      String requestId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
          try {
            cleanupAuditService.limparSeOrfao(storage, key, null, storyId, arquivoId, null);
          } catch (RuntimeException exception) {
            cleanupAuditService.registrar(storyId, usuarioId, requestId);
          }
        }
      }
    });
  }

  private UUID uuidDeterministico(
      String tipo,
      UUID usuarioId,
      ModoConteudoStory modo,
      UUID anuncioId,
      String chave) {
    return UUID.nameUUIDFromBytes(
        ("story-canonico-v1:" + tipo + ":" + usuarioId + ":" + modo.name()
            + ":" + (anuncioId == null ? "conta" : anuncioId) + ":" + chave)
            .getBytes(StandardCharsets.UTF_8));
  }

  private String mimeSeguro(MultipartFile multipart) {
    String value = multipart == null || multipart.getContentType() == null
        ? ""
        : multipart.getContentType().trim().toLowerCase(Locale.ROOT);
    return value.matches("[a-z0-9][a-z0-9.+-]{0,63}/[a-z0-9][a-z0-9.+-]{0,63}")
        ? value
        : "nao_informado";
  }

  private String extensaoSegura(MultipartFile multipart) {
    String nome = multipart == null || multipart.getOriginalFilename() == null
        ? ""
        : multipart.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
    int separador = nome.lastIndexOf('.');
    String value = separador < 0 || separador == nome.length() - 1 ? "" : nome.substring(separador + 1);
    return value.matches("[a-z0-9]{1,10}") ? value : "nao_informada";
  }

  private OffsetDateTime agoraUtc() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }
}
