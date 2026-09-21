package br.com.topsdojob.v3.application.admin.compliance;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminComplianceDocumentoAuditService {

  public enum Etapa { TENTATIVA, AUTORIZADO, BYTES_PREPARADOS, NEGADO, FALHA }

  private final AuditoriaEventoRepository repository;

  public AdminComplianceDocumentoAuditService(AuditoriaEventoRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(UUID documentoId, UUID atorId, String requestId, Etapa etapa) {
    // Preparar bytes nao comprova entrega HTTP concluida nem visualizacao humana.
    String resultado = "{\"etapa\":\"" + etapa.name()
        + "\",\"dadosPrivadosOcultos\":true}";
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    repository.save(etapa == Etapa.NEGADO || etapa == Etapa.FALHA
        ? AuditoriaEventoEntity.registrarErro(UUID.randomUUID(), atorId,
            "COMPLIANCE_DOCUMENTO_ACESSO", "COMPLIANCE_VISITOR_DOCUMENTO", documentoId,
            null, resultado, requestId, agora)
        : AuditoriaEventoEntity.registrar(UUID.randomUUID(), atorId,
            "COMPLIANCE_DOCUMENTO_ACESSO", "COMPLIANCE_VISITOR_DOCUMENTO", documentoId,
            null, resultado, requestId, agora));
  }
}
