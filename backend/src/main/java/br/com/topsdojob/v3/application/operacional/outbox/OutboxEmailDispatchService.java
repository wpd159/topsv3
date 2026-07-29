package br.com.topsdojob.v3.application.operacional.outbox;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEmailDispatchService {
  private static final List<String> DELIVERABLE_TYPES = List.of(
      "AUTH_CONFIRMACAO_CONTA_SOLICITADA",
      "AUTH_CONFIRMACAO_CONTA_REENVIADA",
      "AUTH_RECUPERACAO_SENHA_SOLICITADA",
      "MODERACAO_REPROVADA",
      "MODERACAO_SOLICITAR_AJUSTE",
      "STAFF_CONVITE_CRIADO");

  private final OutboxEventoRepository outboxRepository;
  private final AuditoriaEventoRepository auditRepository;
  private final OutboxEmailTemplateService templateService;
  private final OutboxEmailGateway emailGateway;
  private final OutboxEmailProperties properties;

  public OutboxEmailDispatchService(
      OutboxEventoRepository outboxRepository,
      AuditoriaEventoRepository auditRepository,
      OutboxEmailTemplateService templateService,
      OutboxEmailGateway emailGateway,
      OutboxEmailProperties properties) {
    this.outboxRepository = outboxRepository;
    this.auditRepository = auditRepository;
    this.templateService = templateService;
    this.emailGateway = emailGateway;
    this.properties = properties;
  }

  @Transactional
  public boolean processNext() {
    if (!properties.enabled()) {
      return false;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<OutboxEventoEntity> available = outboxRepository.lockNextEmail(
        StatusOutbox.PENDENTE.name(),
        DELIVERABLE_TYPES,
        now);
    if (available.isEmpty()) {
      return false;
    }
    OutboxEventoEntity event = available.get(0);
    try {
      emailGateway.send(templateService.render(event));
      event.markDelivered(now);
      auditRepository.save(AuditoriaEventoEntity.registrarSistema(
          UUID.randomUUID(),
          null,
          "OUTBOX_EMAIL_ENTREGUE",
          "OUTBOX_EVENTO",
          event.getId(),
          null,
          "{\"canal\":\"EMAIL\",\"status\":\"PROCESSADO\",\"destinatarioExposto\":false}",
          "outbox-email:" + event.getId(),
          now));
    } catch (OutboxPermanentDeliveryException exception) {
      event.cancelDelivery(now, "evento_expirado_ou_substituido");
      auditRepository.save(AuditoriaEventoEntity.registrarSistema(
          UUID.randomUUID(),
          null,
          "OUTBOX_EMAIL_CANCELADO",
          "OUTBOX_EVENTO",
          event.getId(),
          null,
          "{\"canal\":\"EMAIL\",\"status\":\"CANCELADO\",\"motivoSanitizado\":true}",
          "outbox-email:" + event.getId() + ":cancelado",
          now));
    } catch (RuntimeException exception) {
      event.registerDeliveryFailure(
          now,
          properties.maxAttempts(),
          retryDelay(event.getTentativas()),
          "provedor_indisponivel");
      auditRepository.save(AuditoriaEventoEntity.registrarErro(
          UUID.randomUUID(),
          null,
          "OUTBOX_EMAIL_FALHA",
          "OUTBOX_EVENTO",
          event.getId(),
          null,
          "{\"canal\":\"EMAIL\",\"status\":\""
              + event.getStatus().name()
              + "\",\"erroSanitizado\":true,\"destinatarioExposto\":false}",
          "outbox-email:" + event.getId() + ":tentativa:" + event.getTentativas(),
          now));
    }
    return true;
  }

  private Duration retryDelay(Integer attempts) {
    int exponent = Math.min(Math.max((attempts == null ? 0 : attempts) - 1, 0), 10);
    long seconds = properties.retryBaseSeconds() * (1L << exponent);
    return Duration.ofSeconds(Math.min(seconds, 21_600));
  }
}
