package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.MetodoVerificacaoEtaria;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ComplianceVisitorAuditServiceTest {

  @Test
  void persisteSomenteCodigosSanitizadosEHashesDeRede() {
    EventoVerificacaoEtariaRepository repository =
        mock(EventoVerificacaoEtariaRepository.class);
    AtomicReference<EventoVerificacaoEtariaEntity> persisted =
        new AtomicReference<>();
    when(repository.save(any(EventoVerificacaoEtariaEntity.class)))
        .thenAnswer(invocation -> {
          EventoVerificacaoEtariaEntity entity = invocation.getArgument(0);
          persisted.set(entity);
          return entity;
        });
    ComplianceVisitorAuditService service = new ComplianceVisitorAuditService(
        repository,
        new MetricaPublicaHashService(
            "hash-test-safe-value",
            "homologacao"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.42");
    request.addHeader("User-Agent", "Browser QA 123");
    request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, "request-qa");

    service.registrar(
        ResultadoVerificacaoEtaria.NEGADO,
        MetodoVerificacaoEtaria.DECLARACAO,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "1".repeat(64),
        "RETRY",
        "MIDIA_RESTRITA",
        "CPF 123.456.789-09 NASC 01/01/1990 qa@example.invalid",
        null,
        request);

    EventoVerificacaoEtariaEntity entity = persisted.get();
    assertThat(entity.getMotivoSanitizado())
        .contains("DADO_OMITIDO")
        .doesNotContain("123.456", "01/01/1990", "@");
    assertThat((String) ReflectionTestUtils.getField(entity, "ipHash"))
        .matches("[0-9a-f]{64}")
        .doesNotContain("203.0.113.42");
    assertThat((String) ReflectionTestUtils.getField(entity, "userAgentHash"))
        .matches("[0-9a-f]{64}")
        .doesNotContain("Browser QA 123");
    assertThat(entity.getRequestId()).isEqualTo("request-qa");
  }
}
