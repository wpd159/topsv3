package br.com.topsdojob.v3.application.admin.compliance;

import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceDocumentoDecisaoRequestDto;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceDocumentoDecisaoResponseDto;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceDocumentoDto;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceRiscoDto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceAgeGateProperties;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusDocumentoVisitante;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorDocumentoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorDocumentoRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorRiskProfileRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminComplianceVisitorService {

  private static final Pattern EMAIL = Pattern.compile(
      "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
  private static final Pattern URL = Pattern.compile(
      "(?i)\\b(?:https?://|www\\.)\\S+");
  private static final Pattern CPF = Pattern.compile(
      "\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
  private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{8,}\\b");

  private final ComplianceVisitorDocumentoRepository documentoRepository;
  private final ComplianceVisitorChallengeRepository challengeRepository;
  private final ComplianceVisitorRiskProfileRepository riskRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;
  private final ComplianceAgeGateProperties ageGateProperties;

  public AdminComplianceVisitorService(
      ComplianceVisitorDocumentoRepository documentoRepository,
      ComplianceVisitorChallengeRepository challengeRepository,
      ComplianceVisitorRiskProfileRepository riskRepository,
      AuditoriaEventoRepository auditoriaRepository,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties,
      ComplianceAgeGateProperties ageGateProperties) {
    this.documentoRepository = documentoRepository;
    this.challengeRepository = challengeRepository;
    this.riskRepository = riskRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
    this.ageGateProperties = ageGateProperties;
  }

  @Transactional(readOnly = true)
  public List<AdminComplianceDocumentoDto> listarDocumentos(int limite) {
    int safeLimit = Math.max(1, Math.min(limite, 100));
    return documentoRepository
        .findAllByOrderByCriadoEmDesc(PageRequest.of(0, safeLimit))
        .stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public DocumentoPrivado carregarDocumento(UUID id) {
    ComplianceVisitorDocumentoEntity documento = documentoRepository.findById(id)
        .filter(this::documentoCanonico)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "documento de visitante nao encontrado"));
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "documento de visitante indisponivel");
    }
    StoredObject object;
    try {
      object = storage.get(StorageArea.PRIVATE_DOCUMENT, documento.getChaveObjeto());
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "documento de visitante nao encontrado");
    }
    return new DocumentoPrivado(
        object.content(),
        normalizeMime(object.contentType(), documento.getMimeType()));
  }

  @Transactional
  public AdminComplianceDocumentoDecisaoResponseDto decidir(
      UUID id,
      AdminComplianceDocumentoDecisaoRequestDto request,
      AdminUserPrincipal actor,
      String requestId) {
    if (actor == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa necessaria");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId obrigatorio");
    }
    StatusDocumentoVisitante decision = decision(request == null ? null : request.decision());
    String reason = sanitizeReason(request == null ? null : request.reason(), decision);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorDocumentoEntity documento = documentoRepository
        .findByIdForUpdate(id)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "documento de visitante nao encontrado"));
    ComplianceVisitorChallengeEntity challenge = challengeRepository
        .findByIdForUpdate(documento.getChallengeId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "challenge documental indisponivel"));
    String before = documento.getStatus().name();
    try {
      documento.decidir(decision, actor.usuarioId(), reason, now);
      if (decision == StatusDocumentoVisitante.APPROVED) {
        challenge.marcarDocumentoAprovado(
            now,
            now.plus(ageGateProperties.challengeTtl()));
      } else {
        challenge.marcarDocumentoRejeitado(reason, now);
      }
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "documento ja decidido");
    }
    auditoriaRepository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        actor.usuarioId(),
        decision == StatusDocumentoVisitante.APPROVED
            ? "COMPLIANCE_DOCUMENTO_APROVAR"
            : "COMPLIANCE_DOCUMENTO_REJEITAR",
        "COMPLIANCE_VISITOR_DOCUMENTO",
        documento.getId(),
        "{\"status\":\"" + before + "\",\"dadosPrivadosOcultos\":true}",
        "{\"status\":\"" + decision.name()
            + "\",\"motivoSanitizado\":" + (reason == null ? "null" : "true")
            + ",\"tokenEmitido\":false,\"dadosPrivadosOcultos\":true}",
        requestId,
        now));
    return new AdminComplianceDocumentoDecisaoResponseDto(
        documento.getId(),
        documento.getStatus().name(),
        requestId,
        now);
  }

  @Transactional(readOnly = true)
  public List<AdminComplianceRiscoDto> listarRisco(int limite) {
    int safeLimit = Math.max(1, Math.min(limite, 100));
    return riskRepository.findAllByOrderByAtualizadoEmDesc(PageRequest.of(0, safeLimit))
        .stream()
        .map(item -> new AdminComplianceRiscoDto(
            item.getId(),
            "sessao-" + item.getId().toString().substring(0, 8),
            item.getScoreAtual(),
            item.getDecisaoAtual().name(),
            item.getFalhasConsecutivas(),
            item.getAcessosRestritos(),
            item.getAcessosExplicitos(),
            item.isSinalizadoRevisao(),
            later(item.getBloqueadoTemporariamenteAte(), item.getBloqueadoDefinitivamenteAte()),
            item.getUltimoMotivoSanitizado(),
            item.getAtualizadoEm()))
        .toList();
  }

  private AdminComplianceDocumentoDto toDto(ComplianceVisitorDocumentoEntity item) {
    return new AdminComplianceDocumentoDto(
        item.getId(),
        item.getChallengeId(),
        item.getAnuncioId(),
        item.getStatus().name(),
        item.getMimeType(),
        item.getTamanhoBytes(),
        item.getMotivoPublicoSanitizado(),
        item.getCriadoEm(),
        item.getRevisadoEm());
  }

  private boolean documentoCanonico(ComplianceVisitorDocumentoEntity item) {
    return "R2".equals(item.getStorageProvider())
        && Objects.equals(storageProperties.getDocumentBucket(), item.getBucket())
        && item.getChaveObjeto() != null
        && item.getChaveObjeto().startsWith(
            storageProperties.getDocumentPrefix() + "compliance/visitor/");
  }

  private StatusDocumentoVisitante decision(String value) {
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisao obrigatoria");
    }
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "APPROVE", "APPROVED", "APROVAR" -> StatusDocumentoVisitante.APPROVED;
      case "REJECT", "REJECTED", "REJEITAR" -> StatusDocumentoVisitante.REJECTED;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisao invalida");
    };
  }

  private String sanitizeReason(String value, StatusDocumentoVisitante decision) {
    if (value == null || value.isBlank()) {
      if (decision == StatusDocumentoVisitante.REJECTED) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "motivo de rejeicao obrigatorio");
      }
      return null;
    }
    String reason = value.trim().replaceAll("[\\r\\n\\t]+", " ");
    reason = EMAIL.matcher(reason).replaceAll("[dado omitido]");
    reason = URL.matcher(reason).replaceAll("[link omitido]");
    reason = CPF.matcher(reason).replaceAll("[dado omitido]");
    reason = LONG_NUMBER.matcher(reason).replaceAll("[dado omitido]");
    if (reason.length() < 3 || reason.length() > 240) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo invalido");
    }
    return reason;
  }

  private String normalizeMime(String stored, String persisted) {
    String value = stored == null || stored.isBlank() ? persisted : stored;
    if (value == null || value.isBlank()) {
      return "application/octet-stream";
    }
    return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }

  private OffsetDateTime later(OffsetDateTime first, OffsetDateTime second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return first.isAfter(second) ? first : second;
  }

  public record DocumentoPrivado(byte[] bytes, String mimeType) {

    public DocumentoPrivado {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }
}
