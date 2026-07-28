package br.com.topsdojob.v3.application.admin.documento;

import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoRequestDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoResponseDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDocumentoDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycUrlTemporariaDto;
import br.com.topsdojob.v3.application.admin.documento.AdminKycThumbnailProcessor.Thumbnail;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoSanitizer;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator.DocumentoValidado;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioAcessoEntity;
import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioAcessoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ParteDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoDocumentoUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminKycService {

  private static final Duration URL_TTL = Duration.ofMinutes(5);

  private final DocumentoUsuarioRepository documentoRepository;
  private final DocumentoUsuarioAcessoRepository acessoRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final UsuarioRepository usuarioRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final R2StorageProperties storageProperties;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final AdminKycThumbnailProcessor thumbnailProcessor;
  private final DocumentoUploadValidator uploadValidator;

  public AdminKycService(
      DocumentoUsuarioRepository documentoRepository,
      DocumentoUsuarioAcessoRepository acessoRepository,
      ArquivoMidiaRepository arquivoRepository,
      UsuarioRepository usuarioRepository,
      AuditoriaEventoRepository auditoriaRepository,
      R2StorageProperties storageProperties,
      ObjectProvider<ObjectStorage> storageProvider,
      AdminKycThumbnailProcessor thumbnailProcessor,
      DocumentoUploadValidator uploadValidator) {
    this.documentoRepository = documentoRepository;
    this.acessoRepository = acessoRepository;
    this.arquivoRepository = arquivoRepository;
    this.usuarioRepository = usuarioRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.storageProperties = storageProperties;
    this.storageProvider = storageProvider;
    this.thumbnailProcessor = thumbnailProcessor;
    this.uploadValidator = uploadValidator;
  }

  @Transactional(readOnly = true)
  public List<AdminKycEnvioDto> listarPendentes() {
    List<DocumentoUsuarioEntity> documentos = documentoRepository
        .findByStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmAsc(
            List.of(StatusDocumentoUsuario.PENDENTE, StatusDocumentoUsuario.EM_ANALISE));
    return documentos.stream()
        .collect(Collectors.groupingBy(
            DocumentoUsuarioEntity::getEnvioId,
            LinkedHashMap::new,
            Collectors.toList()))
        .values().stream()
        .map(this::mapear)
        .sorted(Comparator.comparing(AdminKycEnvioDto::enviadoEm))
        .toList();
  }

  @Transactional(readOnly = true)
  public AdminKycEnvioDto detalhar(UUID envioId) {
    return mapear(documentosDoEnvio(envioId));
  }

  @Transactional(readOnly = true)
  public List<AdminKycEnvioDto> listarPorUsuario(UUID usuarioId) {
    return documentoRepository
        .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(usuarioId)
        .stream()
        .collect(Collectors.groupingBy(
            DocumentoUsuarioEntity::getEnvioId,
            LinkedHashMap::new,
            Collectors.toList()))
        .values().stream()
        .map(this::mapear)
        .sorted(Comparator.comparing(AdminKycEnvioDto::enviadoEm).reversed())
        .toList();
  }

  @Transactional
  public AdminKycUrlTemporariaDto urlTemporaria(
      UUID documentoId,
      AdminUserPrincipal ator,
      String requestId) {
    DocumentoArquivo acesso = documentoArquivo(documentoId);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    documentosDoEnvio(acesso.documento().getEnvioId()).forEach(item -> item.marcarEmAnalise(agora));
    acessoRepository.save(DocumentoUsuarioAcessoEntity.registrarPermitido(
        UUID.randomUUID(),
        acesso.documento().getId(),
        ator.usuarioId(),
        requestId,
        agora));
    return new AdminKycUrlTemporariaDto(
        acesso.storage().temporaryGetUrl(
            StorageArea.PRIVATE_DOCUMENT,
            acesso.arquivo().getChaveObjeto(),
            URL_TTL).toString(),
        agora.plus(URL_TTL));
  }

  @Transactional
  public Thumbnail miniatura(
      UUID documentoId,
      AdminUserPrincipal ator,
      String requestId) {
    DocumentoArquivo acesso = documentoArquivo(documentoId);
    ArquivoMidiaEntity arquivo = acesso.arquivo();
    Thumbnail thumbnail = thumbnailProcessor.processar(
        arquivo.getId(),
        arquivo.getSha256(),
        arquivo.getMimeType(),
        () -> acesso.storage().get(StorageArea.PRIVATE_DOCUMENT, arquivo.getChaveObjeto()).content());
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    acessoRepository.save(DocumentoUsuarioAcessoEntity.registrarPermitido(
        UUID.randomUUID(),
        acesso.documento().getId(),
        ator.usuarioId(),
        requestId,
        agora));
    return thumbnail;
  }

  @Transactional
  public AdminKycDecisaoResponseDto decidir(
      UUID envioId,
      AdminKycDecisaoRequestDto request,
      AdminUserPrincipal ator,
      String requestId) {
    AdminDecisaoModeracaoAcao decisao = request == null ? null : request.decisao();
    if (decisao == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisao obrigatoria");
    }
    String motivo = AdminModeracaoSanitizer.texto(request.motivo(), 240);
    if ((decisao == AdminDecisaoModeracaoAcao.REPROVAR
        || decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE)
        && (motivo == null || motivo.length() < 3)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo deve ter ao menos 3 caracteres");
    }
    List<DocumentoUsuarioEntity> documentos = documentosDoEnvio(envioId);
    if (documentos.stream().anyMatch(item -> item.getStatus() != StatusDocumentoUsuario.PENDENTE
        && item.getStatus() != StatusDocumentoUsuario.EM_ANALISE)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "envio documental ja decidido");
    }
    String statusAnterior = statusPublico(documentos);
    StatusDocumentoUsuario novoStatus = switch (decisao) {
      case APROVAR -> StatusDocumentoUsuario.VALIDADO;
      case REPROVAR -> StatusDocumentoUsuario.REJEITADO;
      case SOLICITAR_AJUSTE -> StatusDocumentoUsuario.AJUSTE_SOLICITADO;
    };
    StatusArquivoMidia statusArquivo = switch (decisao) {
      case APROVAR -> StatusArquivoMidia.VALIDADO;
      case REPROVAR -> StatusArquivoMidia.REJEITADO;
      case SOLICITAR_AJUSTE -> StatusArquivoMidia.PENDENTE;
    };
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    documentos.forEach(item -> item.aplicarDecisao(novoStatus, ator.usuarioId(), motivo, agora));
    arquivoRepository.findByIdIn(documentos.stream().map(DocumentoUsuarioEntity::getArquivoMidiaId).toList())
        .forEach(arquivo -> arquivo.aplicarDecisao(statusArquivo));
    auditoriaRepository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        ator.usuarioId(),
        "KYC_DOCUMENTOS_DECIDIR",
        "KYC_ENVIO",
        envioId,
        "{\"status\":\"" + statusAnterior + "\",\"dadosPrivadosOcultos\":true}",
        "{\"status\":\"" + novoStatus.name() + "\",\"motivoSanitizado\":"
            + (motivo == null ? "null" : "true") + ",\"dadosPrivadosOcultos\":true}",
        requestId,
        agora));
    return new AdminKycDecisaoResponseDto(envioId, statusPublico(documentos), requestId, agora);
  }

  @Transactional
  public AdminKycEnvioDto enviarAdministrativamente(
      UUID usuarioId,
      String tipoDocumento,
      String modoDocumento,
      UUID envioSubstituidoId,
      MultipartFile documentoUnico,
      MultipartFile documentoFrente,
      MultipartFile documentoVerso,
      String idempotencyKey,
      AdminUserPrincipal ator,
      String requestId) {
    if (ator == null || !ator.isEnabled() || !ator.papeis().contains(PapelUsuario.ADMIN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "operacao exclusiva de ADMIN");
    }
    UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(usuarioId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
    String chaveIdempotencia = chaveIdempotencia(idempotencyKey);
    UUID envioId = uuidDeterministico("admin-kyc:" + usuarioId + ":" + chaveIdempotencia);
    List<DocumentoUsuarioEntity> existente = documentoRepository
        .findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(envioId);
    if (!existente.isEmpty()) {
      if (existente.stream().anyMatch(item -> !usuarioId.equals(item.getUsuarioId()))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "envio documental incompativel");
      }
      return mapear(existente);
    }

    TipoDocumentoUsuario tipo = tipoDocumento(tipoDocumento);
    List<ParteUpload> partes = partes(
        modoDocumento,
        documentoUnico,
        documentoFrente,
        documentoVerso);
    List<DocumentoUsuarioEntity> substituidos = documentosSubstituidos(usuarioId, envioSubstituidoId);
    ObjectStorage storage = storageObrigatorio();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    List<String> chavesCriadas = new ArrayList<>();
    List<DocumentoUsuarioEntity> documentosNovos = new ArrayList<>();

    try {
      for (ParteUpload parte : partes) {
        DocumentoValidado validado = uploadValidator.validar(parte.arquivo());
        if (parte.parte() != ParteDocumentoUsuario.UNICO
            && !validado.mimeType().startsWith("image/")) {
          throw new ResponseStatusException(
              HttpStatus.UNSUPPORTED_MEDIA_TYPE,
              "frente e verso devem ser imagens");
        }
        UUID arquivoId = uuidDeterministico(envioId + ":" + parte.parte().name());
        String key = storageProperties.getDocumentPrefix()
            + "usuarios/" + usuarioId
            + "/envios/" + envioId
            + "/" + parte.parte().name().toLowerCase(Locale.ROOT)
            + "-" + arquivoId + "." + validado.extensao();
        ObjectWriteResult write = storage.putIfAbsent(
            StorageArea.PRIVATE_DOCUMENT,
            key,
            validado.bytes(),
            validado.mimeType());
        if (write == ObjectWriteResult.CREATED) {
          chavesCriadas.add(key);
        }
        validarObjetoPersistido(storage, key, validado);
        arquivoRepository.save(ArquivoMidiaEntity.criarUploadPendente(
            arquivoId,
            "R2",
            storageProperties.getDocumentBucket(),
            key,
            null,
            validado.mimeType(),
            validado.bytes().length,
            validado.largura(),
            validado.altura(),
            null,
            validado.sha256(),
            agora));
        DocumentoUsuarioEntity documento = DocumentoUsuarioEntity.criarPendente(
            uuidDeterministico(envioId + ":documento:" + parte.parte().name()),
            usuarioId,
            arquivoId,
            envioId,
            parte.parte(),
            tipo,
            agora);
        documentoRepository.save(documento);
        documentosNovos.add(documento);
      }
      documentoRepository.flush();
      substituidos.forEach(item -> item.marcarSubstituido(agora));
      if (!substituidos.isEmpty()) {
        documentoRepository.saveAll(substituidos);
      }
      auditoriaRepository.save(AuditoriaEventoEntity.registrar(
          UUID.randomUUID(),
          ator.usuarioId(),
          envioSubstituidoId == null
              ? "KYC_DOCUMENTOS_ADMIN_ADICIONAR"
              : "KYC_DOCUMENTOS_ADMIN_SUBSTITUIR",
          "KYC_ENVIO",
          envioId,
          null,
          "{\"usuarioId\":\"" + usuario.getId()
              + "\",\"tipo\":\"" + tipo.name()
              + "\",\"documentos\":" + documentosNovos.size()
              + ",\"substituicao\":" + (envioSubstituidoId != null)
              + ",\"dadosPrivadosOcultos\":true}",
          requestId,
          agora));
      limparObjetosSeRollback(storage, chavesCriadas);
      return mapear(documentosNovos);
    } catch (DataIntegrityViolationException exception) {
      limparObjetosAgora(storage, chavesCriadas);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "envio documental ja processado");
    } catch (RuntimeException exception) {
      limparObjetosAgora(storage, chavesCriadas);
      throw exception;
    }
  }

  private List<DocumentoUsuarioEntity> documentosSubstituidos(
      UUID usuarioId,
      UUID envioSubstituidoId) {
    if (envioSubstituidoId == null) return List.of();
    List<DocumentoUsuarioEntity> documentos = documentoRepository
        .findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(envioSubstituidoId);
    if (documentos.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "envio a substituir nao encontrado");
    }
    if (documentos.stream().anyMatch(item -> !usuarioId.equals(item.getUsuarioId()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "envio pertence a outro usuario");
    }
    return documentos;
  }

  private List<ParteUpload> partes(
      String modo,
      MultipartFile unico,
      MultipartFile frente,
      MultipartFile verso) {
    if ("UNICO".equalsIgnoreCase(modo)) {
      if (vazio(unico) || !vazio(frente) || !vazio(verso)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "envie somente o documento unico");
      }
      return List.of(new ParteUpload(ParteDocumentoUsuario.UNICO, unico));
    }
    if ("FRENTE_VERSO".equalsIgnoreCase(modo)) {
      if (vazio(frente) || !vazio(unico)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frente do documento obrigatoria");
      }
      List<ParteUpload> resultado = new ArrayList<>();
      resultado.add(new ParteUpload(ParteDocumentoUsuario.FRENTE, frente));
      if (!vazio(verso)) resultado.add(new ParteUpload(ParteDocumentoUsuario.VERSO, verso));
      return List.copyOf(resultado);
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modo de documento invalido");
  }

  private TipoDocumentoUsuario tipoDocumento(String value) {
    try {
      return TipoDocumentoUsuario.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo de documento invalido");
    }
  }

  private void validarObjetoPersistido(
      ObjectStorage storage,
      String key,
      DocumentoValidado esperado) {
    var persistido = storage.get(StorageArea.PRIVATE_DOCUMENT, key);
    byte[] conteudo = persistido.content();
    if (conteudo.length != esperado.bytes().length
        || !esperado.sha256().equals(sha256(conteudo))
        || !esperado.mimeType().equalsIgnoreCase(persistido.contentType())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "documento nao foi confirmado no storage privado");
    }
  }

  private String chaveIdempotencia(String value) {
    String normalizado = value == null ? "" : value.trim();
    if (normalizado.isEmpty()
        || normalizado.length() > 120
        || !normalizado.matches("[A-Za-z0-9._:-]+")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chave de idempotencia invalida");
    }
    return normalizado;
  }

  private UUID uuidDeterministico(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private boolean vazio(MultipartFile file) {
    return file == null || file.isEmpty();
  }

  private AdminKycEnvioDto mapear(List<DocumentoUsuarioEntity> documentos) {
    DocumentoUsuarioEntity primeiro = documentos.get(0);
    UsuarioEntity usuario = usuarioRepository.findById(primeiro.getUsuarioId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario do KYC nao encontrado"));
    Map<UUID, ArquivoMidiaEntity> arquivos = arquivoRepository
        .findByIdIn(documentos.stream().map(DocumentoUsuarioEntity::getArquivoMidiaId).toList()).stream()
        .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
    return new AdminKycEnvioDto(
        primeiro.getEnvioId(),
        usuario.getId(),
        usuario.getNomeCivil(),
        mascararCpf(usuario.getCpfNormalizado()),
        usuario.getDataNascimento() == null ? null : usuario.getDataNascimento().toString(),
        statusPublico(documentos),
        documentos.stream().map(DocumentoUsuarioEntity::getMotivoModeracao)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null),
        documentos.stream().map(DocumentoUsuarioEntity::getCriadoEm).min(Comparator.naturalOrder()).orElse(null),
        documentos.stream().map(DocumentoUsuarioEntity::getRevisadoEm).filter(value -> value != null)
            .max(Comparator.naturalOrder()).orElse(null),
        documentos.stream().sorted(Comparator.comparing(DocumentoUsuarioEntity::getParte))
            .map(documento -> {
              ArquivoMidiaEntity arquivo = arquivos.get(documento.getArquivoMidiaId());
              return new AdminKycDocumentoDto(
                  documento.getId(),
                  documento.getTipo().name(),
                  documento.getParte().name(),
                  documento.getStatus().name(),
                  arquivo == null ? null : arquivo.getMimeType(),
                  arquivo == null || arquivo.getTamanhoBytes() == null ? 0L : arquivo.getTamanhoBytes(),
                  documento.getCriadoEm());
            }).toList());
  }

  private List<DocumentoUsuarioEntity> documentosDoEnvio(UUID envioId) {
    List<DocumentoUsuarioEntity> documentos = documentoRepository
        .findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(envioId);
    if (documentos.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "envio documental nao encontrado");
    }
    return documentos;
  }

  private String statusPublico(List<DocumentoUsuarioEntity> documentos) {
    if (documentos.stream().allMatch(item -> item.getStatus() == StatusDocumentoUsuario.VALIDADO)) return "APROVADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.AJUSTE_SOLICITADO)) return "AJUSTE_SOLICITADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.REJEITADO)) return "REJEITADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.EM_ANALISE)) return "EM_ANALISE";
    return "PENDENTE";
  }

  private void validarLocalPrivado(ArquivoMidiaEntity arquivo) {
    if (!storageProperties.getDocumentBucket().equals(arquivo.getBucket())
        || arquivo.getChaveObjeto() == null
        || !arquivo.getChaveObjeto().startsWith(storageProperties.getDocumentPrefix())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "documento fora do storage privado canonico");
    }
  }

  private DocumentoArquivo documentoArquivo(UUID documentoId) {
    DocumentoUsuarioEntity documento = documentoRepository.findById(documentoId)
        .filter(item -> item.getRemovidoEm() == null && item.getExpurgadoEm() == null)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "documento nao encontrado"));
    ArquivoMidiaEntity arquivo = arquivoRepository.findById(documento.getArquivoMidiaId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "arquivo do documento nao encontrado"));
    validarLocalPrivado(arquivo);
    return new DocumentoArquivo(documento, arquivo, storageObrigatorio());
  }

  private ObjectStorage storageObrigatorio() {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de documentos indisponivel");
    }
    return storage;
  }

  private void limparObjetosSeRollback(ObjectStorage storage, List<String> keys) {
    if (!TransactionSynchronizationManager.isSynchronizationActive() || keys.isEmpty()) return;
    List<String> snapshot = List.copyOf(keys);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
          limparObjetosAgora(storage, snapshot);
        }
      }
    });
  }

  private void limparObjetosAgora(ObjectStorage storage, List<String> keys) {
    for (String key : keys) {
      try {
        storage.delete(StorageArea.PRIVATE_DOCUMENT, key);
      } catch (RuntimeException ignored) {
        // O objeto continua privado e pode ser reconciliado sem expor dados do documento.
      }
    }
  }

  private String mascararCpf(String cpf) {
    return cpf == null || cpf.length() != 11 ? null : "***.***.***-" + cpf.substring(9);
  }

  private record DocumentoArquivo(
      DocumentoUsuarioEntity documento,
      ArquivoMidiaEntity arquivo,
      ObjectStorage storage) {
  }

  private record ParteUpload(
      ParteDocumentoUsuario parte,
      MultipartFile arquivo) {
  }
}
