package br.com.topsdojob.v3.application.operacional.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEmailDispatchServiceTest {
  @Test
  void entregaUmaVezEMarcaProcessado() {
    Fixture fixture = new Fixture(true);
    when(fixture.repository.lockNextEmail(anyString(), anyList(), any()))
        .thenReturn(List.of(fixture.event), List.of());

    assertThat(fixture.service.processNext()).isTrue();
    assertThat(fixture.service.processNext()).isFalse();

    verify(fixture.gateway, times(1)).send(any());
    assertThat(fixture.event.getStatus()).isEqualTo(StatusOutbox.PROCESSADO);
    assertThat(fixture.event.getProcessadoEm()).isNotNull();
  }

  @Test
  void falhaMantemPendenteComBackoffSemSucessoFalso() {
    Fixture fixture = new Fixture(true);
    when(fixture.repository.lockNextEmail(anyString(), anyList(), any()))
        .thenReturn(List.of(fixture.event));
    org.mockito.Mockito.doThrow(new IllegalStateException("smtp indisponivel"))
        .when(fixture.gateway).send(any());

    assertThat(fixture.service.processNext()).isTrue();

    assertThat(fixture.event.getStatus()).isEqualTo(StatusOutbox.PENDENTE);
    assertThat(fixture.event.getTentativas()).isEqualTo(1);
    assertThat(fixture.event.getProximaTentativaEm()).isNotNull();
    assertThat(fixture.event.getErroResumido()).isEqualTo("provedor_indisponivel");
    assertThat(fixture.event.getProcessadoEm()).isNull();
  }

  @Test
  void workerDesabilitadoNaoConsultaNemEnvia() {
    Fixture fixture = new Fixture(false);

    assertThat(fixture.service.processNext()).isFalse();

    verify(fixture.repository, never()).lockNextEmail(anyString(), anyList(), any());
    verify(fixture.gateway, never()).send(any());
  }

  @Test
  void tokenExpiradoCancelaEventoSemEnviarOuRetentar() {
    Fixture fixture = new Fixture(true);
    when(fixture.repository.lockNextEmail(anyString(), anyList(), any()))
        .thenReturn(List.of(fixture.event));
    when(fixture.templates.render(fixture.event))
        .thenThrow(new OutboxPermanentDeliveryException("token expirado"));

    assertThat(fixture.service.processNext()).isTrue();

    assertThat(fixture.event.getStatus()).isEqualTo(StatusOutbox.CANCELADO);
    assertThat(fixture.event.getProximaTentativaEm()).isNull();
    verify(fixture.gateway, never()).send(any());
  }

  private static final class Fixture {
    private final OutboxEventoRepository repository = mock(OutboxEventoRepository.class);
    private final AuditoriaEventoRepository audits = mock(AuditoriaEventoRepository.class);
    private final OutboxEmailTemplateService templates = mock(OutboxEmailTemplateService.class);
    private final OutboxEmailGateway gateway = mock(OutboxEmailGateway.class);
    private final OutboxEventoEntity event;
    private final OutboxEmailDispatchService service;

    private Fixture(boolean enabled) {
      UUID id = UUID.randomUUID();
      event = OutboxEventoEntity.registrarPendente(
          id,
          "USUARIO",
          UUID.randomUUID(),
          "AUTH_RECUPERACAO_SENHA_SOLICITADA",
          "{\"communicationVersion\":1}",
          "AUTH:" + id,
          OffsetDateTime.now(ZoneOffset.UTC));
      when(templates.render(event)).thenReturn(new OutboxEmailMessage(
          id,
          event.getIdempotencyKey(),
          "qa@example.invalid",
          "Assunto",
          "Texto",
          "<p>Texto</p>"));
      OutboxEmailProperties properties = new OutboxEmailProperties(
          enabled,
          "CAPTURE",
          "capture@test.invalid",
          "no-reply@topsdojob.com",
          "Tops do Job",
          "",
          10,
          3,
          1);
      service = new OutboxEmailDispatchService(repository, audits, templates, gateway, properties);
    }
  }
}
