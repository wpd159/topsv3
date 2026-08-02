package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
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
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeuAnuncioStoryService {

  private final MeusAnunciosConsultaService consultaService;
  private final MeuAnuncioStoryConsultaService storyConsultaService;
  private final AnuncioRepository anuncioRepository;
  private final StoryAnuncioRepository storyRepository;
  private final AnuncioMidiaRepository midiaRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AtivacaoBeneficioRepository ativacaoRepository;
  private final BeneficioAnuncioConsultaService beneficioConsultaService;
  private final BeneficioPremiumOpcaoRepository opcaoRepository;
  private final GrupoAtivacaoBeneficioRepository grupoRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final MidiaUploadValidator uploadValidator;
  private final FotoUploadProcessor fotoProcessor;
  private final R2StorageProperties storageProperties;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final StoryUploadCleanupAuditService cleanupAuditService;
  private final Clock clock;

  @Autowired
  public MeuAnuncioStoryService(
      MeusAnunciosConsultaService consultaService,
      MeuAnuncioStoryConsultaService storyConsultaService,
      AnuncioRepository anuncioRepository,
      StoryAnuncioRepository storyRepository,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      AtivacaoBeneficioRepository ativacaoRepository,
      BeneficioAnuncioConsultaService beneficioConsultaService,
      BeneficioPremiumOpcaoRepository opcaoRepository,
      GrupoAtivacaoBeneficioRepository grupoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      MidiaUploadValidator uploadValidator,
      FotoUploadProcessor fotoProcessor,
      R2StorageProperties storageProperties,
      ObjectProvider<ObjectStorage> storageProvider,
      StoryUploadCleanupAuditService cleanupAuditService) {
    this(
        consultaService, storyConsultaService, anuncioRepository, storyRepository,
        midiaRepository, arquivoRepository, ativacaoRepository, beneficioConsultaService,
        opcaoRepository, grupoRepository, auditoriaRepository, uploadValidator, fotoProcessor, storageProperties,
        storageProvider, cleanupAuditService, Clock.systemUTC());
  }

  MeuAnuncioStoryService(
      MeusAnunciosConsultaService consultaService,
      MeuAnuncioStoryConsultaService storyConsultaService,
      AnuncioRepository anuncioRepository,
      StoryAnuncioRepository storyRepository,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      AtivacaoBeneficioRepository ativacaoRepository,
      BeneficioAnuncioConsultaService beneficioConsultaService,
      BeneficioPremiumOpcaoRepository opcaoRepository,
      GrupoAtivacaoBeneficioRepository grupoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      MidiaUploadValidator uploadValidator,
      FotoUploadProcessor fotoProcessor,
      R2StorageProperties storageProperties,
      ObjectProvider<ObjectStorage> storageProvider,
      StoryUploadCleanupAuditService cleanupAuditService,
      Clock clock) {
    this.consultaService = consultaService;
    this.storyConsultaService = storyConsultaService;
    this.anuncioRepository = anuncioRepository;
    this.storyRepository = storyRepository;
    this.midiaRepository = midiaRepository;
    this.arquivoRepository = arquivoRepository;
    this.ativacaoRepository = ativacaoRepository;
    this.beneficioConsultaService = beneficioConsultaService;
    this.opcaoRepository = opcaoRepository;
    this.grupoRepository = grupoRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.uploadValidator = uploadValidator;
    this.fotoProcessor = fotoProcessor;
    this.storageProperties = storageProperties;
    this.storageProvider = storageProvider;
    this.cleanupAuditService = cleanupAuditService;
    this.clock = clock;
  }

  @Transactional
  public MeuAnuncioStoryDto publicar(
      String slug,
      String modoConteudo,
      List<MultipartFile> arquivos,
      String idempotencyKey,
      Authentication authentication,
      String requestId) {
    UUID usuarioId = consultaService.usuarioAutenticado(authentication).getId();
    ModoConteudoStory modo = modo(modoConteudo);
    String chave = chaveIdempotencia(idempotencyKey);
    validarArquivos(modo, arquivos);
    OffsetDateTime consultaEm = agoraUtc();
    AnuncioEntity anuncio = anuncioRepository.findBySlugForLifecycle(consultaService.slugSeguro(slug))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    if (!usuarioId.equals(anuncio.getUsuarioId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "anuncio pertence a outro usuario");
    }
    String fingerprint = requestFingerprint(modo, arquivos);

    List<StoryAnuncioEntity> existentes = storyRepository.findByAnuncioIdForUpdate(anuncio.getId());
    boolean expirouStory = existentes.stream()
        .map(story -> story.expirarSeVencido(consultaEm))
        .reduce(false, Boolean::logicalOr);
    if (expirouStory) {
      storyRepository.flush();
    }
    StoryAnuncioEntity repetido = existentes.stream()
        .filter(story -> usuarioId.equals(story.getCriadoPor()))
        .filter(story -> chave.equals(story.getIdempotencyKey()))
        .findFirst()
        .orElseGet(() -> storyRepository.findByAnuncioIdAndCriadoPorAndIdempotencyKey(
            anuncio.getId(), usuarioId, chave).orElse(null));
    if (repetido != null) {
      if (!anuncio.getId().equals(anuncioId(repetido))
          || repetido.getModoConteudoEfetivo() != modo
          || !fingerprint.equals(repetido.getRequestFingerprint())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outra intencao");
      }
      return storyConsultaService.consultar(repetido);
    }

    validarAnuncioPublicavel(anuncio);
    if (existentes.stream().anyMatch(story -> ativo(story, consultaEm))) {
      throw new StoryJaAtivoException();
    }
    Set<UUID> ativacoesConsumidas = existentes.stream()
        .map(StoryAnuncioEntity::getAtivacaoBeneficioId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    AtivacaoStories ativacao = ativacaoStories(
        anuncio, usuarioId, consultaEm, ativacoesConsumidas);

    UUID storyId = uuidDeterministico("story", usuarioId, anuncio.getId(), chave);
    UUID vinculoId = null;
    if (modo == ModoConteudoStory.MIDIA_UPLOAD) {
      vinculoId = processarMidia(
          storyId,
          anuncio,
          arquivos.get(0),
          usuarioId,
          chave,
          consultaEm,
          requestId);
    }

    OffsetDateTime publicadoEm = agoraUtc();
    OffsetDateTime fimEm = iniciarVigencia(ativacao, publicadoEm);

    StoryAnuncioEntity story = StoryAnuncioEntity.criarAutogestao(
        storyId,
        anuncio.getId(),
        vinculoId,
        modo,
        ativacao.ativacao().getId(),
        chave,
        fingerprint,
        publicadoEm,
        fimEm,
        usuarioId);
    storyRepository.save(story);
    auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
        uuidDeterministico("auditoria", usuarioId, anuncio.getId(), chave),
        usuarioId,
        "STORY_AUTOGESTAO_PUBLICADO",
        "STORY_ANUNCIO",
        storyId,
        null,
        "{\"modoConteudo\":\"" + modo.name() + "\",\"status\":\"PUBLICADO\"}",
        requestId,
        publicadoEm));
    storyRepository.flush();
    return storyConsultaService.consultar(story);
  }

  private UUID processarMidia(
      UUID storyId,
      AnuncioEntity anuncio,
      MultipartFile multipart,
      UUID usuarioId,
      String chave,
      OffsetDateTime agora,
      String requestId) {
    MidiaValidada validada = uploadValidator.validarStory(multipart);
    FotoProcessada foto = validada.video() ? null : fotoProcessor.processar(validada);
    byte[] bytes = foto == null ? validada.bytes() : foto.bytes();
    String mime = foto == null ? validada.mimeType() : foto.mimeType();
    String extensao = foto == null ? validada.extensao() : foto.extensao();
    Integer largura = foto == null ? validada.largura() : Integer.valueOf(foto.largura());
    Integer altura = foto == null ? validada.altura() : Integer.valueOf(foto.altura());
    String sha = foto == null ? validada.sha256() : foto.sha256();
    UUID arquivoId = uuidDeterministico("arquivo", usuarioId, anuncio.getId(), chave);
    UUID vinculoId = uuidDeterministico("vinculo", usuarioId, anuncio.getId(), chave);
    String nome = validada.video()
        ? "video." + extensao
        : "foto-v" + foto.pipelineVersao() + "." + extensao;
    String key = storageProperties.getPrivateMediaPrefix()
        + "anuncios/" + anuncio.getId() + "/stories/" + storyId + "/" + arquivoId + "/" + nome;

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
    arquivoRepository.saveAndFlush(arquivo);

    ObjectWriteResult resultado;
    try {
      resultado = storage.putIfAbsent(StorageArea.PRIVATE_MEDIA, key, bytes, mime);
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de Story indisponivel");
    }
    if (resultado == ObjectWriteResult.CREATED) {
      limparObjetoSeRollback(
          storage, key, anuncio.getId(), storyId, arquivoId, vinculoId, usuarioId, requestId);
    }
    verificarObjeto(storage, key, bytes, mime, largura, altura, foto != null);
    arquivo.aplicarDecisao(br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia.VALIDADO);

    int ordem = midiaRepository.findByAnuncioId(anuncio.getId()).stream()
        .filter(item -> item.getFinalidade()
            == br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia.STORY)
        .map(AnuncioMidiaEntity::getOrdem)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(-1) + 1;
    midiaRepository.save(AnuncioMidiaEntity.criarStoryUploadValidado(
        vinculoId, anuncio.getId(), arquivoId, ordem, agora));
    return vinculoId;
  }

  private AtivacaoStories ativacaoStories(
      AnuncioEntity anuncio,
      UUID usuarioId,
      OffsetDateTime agora,
      Set<UUID> ativacoesConsumidas) {
    List<AtivacaoBeneficioEntity> ativas = ativacaoRepository.findVigentesByCodigoForUpdate(
        anuncio.getId(), usuarioId, STORIES, StatusAtivacaoBeneficio.ATIVA, agora);
    PremiumBeneficioCalculado selecionada = selecionarAtivacao(
        ativas, agora, ativacoesConsumidas, Set.of(
            PremiumBeneficioStatusCalculado.ATIVO,
            PremiumBeneficioStatusCalculado.VENCENDO));
    if (selecionada == null) {
      List<AtivacaoBeneficioEntity> aguardando = ativacaoRepository.findAguardandoUsoByCodigoForUpdate(
          anuncio.getId(), usuarioId, STORIES, StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
      selecionada = selecionarAtivacao(
          aguardando, agora, ativacoesConsumidas, Set.of(PremiumBeneficioStatusCalculado.PENDENTE));
    }
    if (selecionada == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "beneficio STORIES disponivel obrigatorio");
    }
    AtivacaoBeneficioEntity ativacao = selecionada.ativacao();
    GrupoAtivacaoBeneficioEntity grupo = grupoRepository.findByIdForUpdate(ativacao.getGrupoAtivacaoId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT, "grupo do beneficio STORIES ausente"));
    if (!anuncio.getId().equals(grupo.getAnuncioId())
        || !usuarioId.equals(grupo.getUsuarioId())
        || ativacao.getOrigem() != grupo.getOrigem()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "beneficio STORIES inconsistente");
    }
    return new AtivacaoStories(ativacao, grupo, duracaoContratada(ativacao));
  }

  private PremiumBeneficioCalculado selecionarAtivacao(
      List<AtivacaoBeneficioEntity> candidatas,
      OffsetDateTime agora,
      Set<UUID> ativacoesConsumidas,
      Set<PremiumBeneficioStatusCalculado> statusPermitidos) {
    if (candidatas == null || candidatas.isEmpty()) {
      return null;
    }
    return beneficioConsultaService.calcular(candidatas, agora).stream()
        .filter(item -> item.beneficio() != null && STORIES.equals(item.beneficio().getCodigo()))
        .filter(item -> !item.inconsistente())
        .filter(item -> statusPermitidos.contains(item.status()))
        .filter(item -> !ativacoesConsumidas.contains(item.ativacao().getId()))
        .findFirst()
        .orElse(null);
  }

  private Duration duracaoContratada(AtivacaoBeneficioEntity ativacao) {
    if (ativacao.getOpcaoId() != null) {
      BeneficioPremiumOpcaoEntity opcao = opcaoRepository.findById(ativacao.getOpcaoId())
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.CONFLICT, "duracao do beneficio STORIES ausente"));
      if (opcao.getDuracaoDias() == null || opcao.getDuracaoDias() <= 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "duracao do beneficio STORIES invalida");
      }
      return Duration.ofDays(opcao.getDuracaoDias());
    }
    if (ativacao.getInicioEm() != null && ativacao.getFimEm() != null) {
      Duration historica = Duration.between(ativacao.getInicioEm(), ativacao.getFimEm());
      if (!historica.isNegative() && !historica.isZero()) {
        return historica;
      }
    }
    throw new ResponseStatusException(HttpStatus.CONFLICT, "duracao do beneficio STORIES indisponivel");
  }

  private OffsetDateTime iniciarVigencia(AtivacaoStories selecionada, OffsetDateTime publicadoEm) {
    AtivacaoBeneficioEntity ativacao = selecionada.ativacao();
    if (ativacao.getStatus() == StatusAtivacaoBeneficio.ATIVA) {
      if (ativacao.getInicioEm() == null
          || ativacao.getFimEm() == null
          || ativacao.getInicioEm().isAfter(publicadoEm)
          || !ativacao.getFimEm().isAfter(publicadoEm)) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "beneficio STORIES expirou antes da publicacao");
      }
      return ativacao.getFimEm();
    }
    if (ativacao.getStatus() != StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "beneficio STORIES indisponivel");
    }
    OffsetDateTime fimEm;
    try {
      fimEm = publicadoEm.plus(selecionada.duracao());
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "duracao do beneficio STORIES invalida");
    }
    ativacao.iniciarVigenciaExclusiva(publicadoEm, fimEm);
    selecionada.grupo().estenderValidadeAte(fimEm, publicadoEm);
    ativacaoRepository.save(ativacao);
    grupoRepository.save(selecionada.grupo());
    return fimEm;
  }

  private void validarAnuncioPublicavel(AnuncioEntity anuncio) {
    if (anuncio.getRemovidoEm() != null
        || anuncio.getStatus() != StatusAnuncio.PUBLICADO
        || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.APROVADO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "anuncio nao esta publicavel para Story");
    }
  }

  private void validarArquivos(ModoConteudoStory modo, List<MultipartFile> arquivos) {
    List<MultipartFile> recebidos = arquivos == null ? List.of() : arquivos;
    if (modo == ModoConteudoStory.ANUNCIO && !recebidos.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modo ANUNCIO nao aceita arquivo");
    }
    if (modo == ModoConteudoStory.MIDIA_UPLOAD
        && (recebidos.size() != 1 || recebidos.get(0) == null || recebidos.get(0).isEmpty())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "envie exatamente uma midia para o Story");
    }
  }

  private ModoConteudoStory modo(String value) {
    try {
      return ModoConteudoStory.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modoConteudo invalido");
    }
  }

  private String chaveIdempotencia(String value) {
    String normalizada = value == null ? "" : value.trim();
    if (!normalizada.matches("[A-Za-z0-9._:-]{1,160}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
    }
    return normalizada;
  }

  private String requestFingerprint(ModoConteudoStory modo, List<MultipartFile> arquivos) {
    if (modo == ModoConteudoStory.ANUNCIO) {
      return sha256("ANUNCIO".getBytes(StandardCharsets.UTF_8));
    }
    MultipartFile arquivo = arquivos.get(0);
    try (InputStream input = arquivo.getInputStream()) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(("MIDIA_UPLOAD:" + arquivo.getSize() + ":").getBytes(StandardCharsets.UTF_8));
      byte[] buffer = new byte[8192];
      int lidos;
      while ((lidos = input.read(buffer)) >= 0) {
        if (lidos > 0) digest.update(buffer, 0, lidos);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo nao pode ser lido");
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private UUID anuncioId(StoryAnuncioEntity story) {
    if (story.getAnuncioId() != null) {
      return story.getAnuncioId();
    }
    return story.getAnuncioMidiaId() == null
        ? null
        : midiaRepository.findById(story.getAnuncioMidiaId())
            .map(AnuncioMidiaEntity::getAnuncioId)
            .orElse(null);
  }

  private boolean ativo(StoryAnuncioEntity story, OffsetDateTime agora) {
    return story.getStatus() == StatusStoryAnuncio.PUBLICADO
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
      String mimePersistido = objeto.contentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
      if (!Objects.equals(mime, mimePersistido)
          || !Objects.equals(sha256(esperado), sha256(objeto.content()))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "objeto do Story diverge da midia processada");
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

  private UUID uuidDeterministico(String tipo, UUID usuarioId, UUID anuncioId, String chave) {
    return UUID.nameUUIDFromBytes(
        ("story-autogestao-v1:" + tipo + ":" + usuarioId + ":" + anuncioId + ":" + chave)
            .getBytes(StandardCharsets.UTF_8));
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

  private void limparObjetoSeRollback(
      ObjectStorage storage,
      String key,
      UUID anuncioId,
      UUID storyId,
      UUID arquivoId,
      UUID vinculoId,
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
            cleanupAuditService.limparSeOrfao(
                storage, key, anuncioId, storyId, arquivoId, vinculoId);
          } catch (RuntimeException exception) {
            cleanupAuditService.registrar(storyId, usuarioId, requestId);
          }
        }
      }
    });
  }

  private record AtivacaoStories(
      AtivacaoBeneficioEntity ativacao,
      GrupoAtivacaoBeneficioEntity grupo,
      Duration duracao) {
  }
}
