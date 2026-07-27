package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorDocumentSubmitResponseDto;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator.DocumentoValidado;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EstadoPublicoAgeGate;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.MetodoVerificacaoEtaria;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorDocumentoEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorDocumentoRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceVisitorDocumentService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ComplianceVisitorDocumentService.class);

  private final ComplianceVisitorChallengeRepository challengeRepository;
  private final ComplianceVisitorDocumentoRepository documentoRepository;
  private final ComplianceVisitorSessionService sessionService;
  private final ComplianceVisitorAuditService auditService;
  private final DocumentoUploadValidator validator;
  private final MetricaPublicaHashService hashService;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;

  public ComplianceVisitorDocumentService(
      ComplianceVisitorChallengeRepository challengeRepository,
      ComplianceVisitorDocumentoRepository documentoRepository,
      ComplianceVisitorSessionService sessionService,
      ComplianceVisitorAuditService auditService,
      DocumentoUploadValidator validator,
      MetricaPublicaHashService hashService,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties) {
    this.challengeRepository = challengeRepository;
    this.documentoRepository = documentoRepository;
    this.sessionService = sessionService;
    this.auditService = auditService;
    this.validator = validator;
    this.hashService = hashService;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
  }

  @Transactional
  public SubmitResult submeter(
      UUID challengeId,
      String idempotencyKey,
      MultipartFile arquivo,
      HttpServletRequest request) {
    if (challengeId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "challenge obrigatorio");
    }
    ComplianceVisitorSessionService.SessionContext session =
        sessionService.obterOuCriar(request);
    String idempotenciaHash = idempotencia(session.sessionHash(), idempotencyKey);
    DocumentoValidado validado = validator.validar(arquivo);
    var existente = documentoRepository.findBySessionHashAndIdempotenciaHash(
        session.sessionHash(),
        idempotenciaHash);
    if (existente.isPresent()) {
      if (!challengeId.equals(existente.get().getChallengeId())
          || !validado.sha256().equals(existente.get().getSha256())
          || !validado.mimeType().equals(existente.get().getMimeType())
          || validado.bytes().length != existente.get().getTamanhoBytes()) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "identificador de idempotencia usado com outro documento");
      }
      return new SubmitResult(toResponse(existente.get()), session.cookie());
    }
    ComplianceVisitorChallengeEntity challenge = challengeRepository
        .findByIdForUpdate(challengeId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "challenge nao encontrado"));
    if (!session.sessionHash().equals(challenge.getSessionHash())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "challenge nao encontrado");
    }
    if (challenge.getStatus() != StatusChallengeVisitante.DOCUMENT_PENDING
        && challenge.getStatus() != StatusChallengeVisitante.DOCUMENT_REJECTED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "challenge nao aceita documento");
    }
    ObjectStorage storage = storage();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    UUID documentoId = UUID.randomUUID();
    String key = storageProperties.getDocumentPrefix()
        + "compliance/visitor/"
        + session.sessionHash().substring(0, 16)
        + "/"
        + documentoId
        + "."
        + validado.extensao();
    ObjectWriteResult writeResult = storage.putIfAbsent(
        StorageArea.PRIVATE_DOCUMENT,
        key,
        validado.bytes(),
        validado.mimeType());
    compensarRollback(storage, key, writeResult == ObjectWriteResult.CREATED);
    validarObjetoPersistido(storage, key, validado);
    ComplianceVisitorDocumentoEntity documento =
        ComplianceVisitorDocumentoEntity.criarPendente(
            documentoId,
            session.sessionHash(),
            challenge.getId(),
            challenge.getAnuncioId(),
            storageProperties.getDocumentBucket(),
            key,
            validado.mimeType(),
            validado.bytes().length,
            validado.sha256(),
            idempotenciaHash,
            agora);
    documentoRepository.save(documento);
    challenge.reabrirSubmissaoDocumento(agora);
    auditService.registrar(
        ResultadoVerificacaoEtaria.INDETERMINADO,
        MetodoVerificacaoEtaria.DOCUMENTO,
        challenge.getAnuncioId(),
        challenge.getId(),
        session.sessionHash(),
        EstadoPublicoAgeGate.DOCUMENT_PENDING.name(),
        challenge.getEscopo().name(),
        "DOCUMENTO_RECEBIDO",
        "PENDING",
        request);
    return new SubmitResult(toResponse(documento), session.cookie());
  }

  private ObjectStorage storage() {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || storageProperties == null || !storageProperties.isEnabled()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "storage documental indisponivel");
    }
    return storage;
  }

  private String idempotencia(String sessionHash, String value) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "identificador de idempotencia obrigatorio");
    }
    String normalized = value.trim();
    if (normalized.length() > 160 || !normalized.matches("[A-Za-z0-9._:-]+")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "identificador de idempotencia invalido");
    }
    return hashService.hash(
        "compliance-document-idempotencia",
        sessionHash + "|" + normalized);
  }

  private void validarObjetoPersistido(
      ObjectStorage storage,
      String key,
      DocumentoValidado validado) {
    StoredObject stored = storage.get(StorageArea.PRIVATE_DOCUMENT, key);
    String storedHash = sha256(stored.content());
    String storedType = stored.contentType() == null
        ? ""
        : stored.contentType().split(";", 2)[0].trim().toLowerCase();
    if (!validado.sha256().equals(storedHash)
        || !validado.mimeType().equals(storedType)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "documento privado divergente");
    }
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(content));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private void compensarRollback(ObjectStorage storage, String key, boolean criado) {
    if (!criado || !TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
              return;
            }
            try {
              storage.delete(StorageArea.PRIVATE_DOCUMENT, key);
            } catch (RuntimeException ignored) {
              LOGGER.warn("Falha sanitizada ao compensar documento de visitante");
            }
          }
        });
  }

  private VisitorDocumentSubmitResponseDto toResponse(
      ComplianceVisitorDocumentoEntity documento) {
    EstadoPublicoAgeGate state = switch (documento.getStatus()) {
      case PENDING -> EstadoPublicoAgeGate.DOCUMENT_PENDING;
      case APPROVED -> EstadoPublicoAgeGate.DOCUMENT_APPROVED;
      case REJECTED -> EstadoPublicoAgeGate.DOCUMENT_REJECTED;
    };
    String message = switch (documento.getStatus()) {
      case PENDING -> "Documento recebido para analise.";
      case APPROVED ->
          "Documento aprovado. Repita a verificacao para emitir o acesso.";
      case REJECTED -> documento.getMotivoPublicoSanitizado() == null
          ? "Documento rejeitado. Envie um novo arquivo valido."
          : documento.getMotivoPublicoSanitizado();
    };
    return new VisitorDocumentSubmitResponseDto(
        documento.getId(),
        documento.getChallengeId(),
        state.name(),
        documento.getStatus().name(),
        documento.getCriadoEm(),
        message);
  }

  public record SubmitResult(
      VisitorDocumentSubmitResponseDto response,
      org.springframework.http.ResponseCookie sessionCookie) {
  }
}
