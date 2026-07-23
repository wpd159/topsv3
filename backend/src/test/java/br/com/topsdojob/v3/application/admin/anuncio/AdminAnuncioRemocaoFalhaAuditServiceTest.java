package br.com.topsdojob.v3.application.admin.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ResultadoAuditoria;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminAnuncioRemocaoFalhaAuditServiceTest {

  private final AuditoriaEventoRepository repository = mock(AuditoriaEventoRepository.class);
  private final AdminAnuncioRemocaoFalhaAuditService service =
      new AdminAnuncioRemocaoFalhaAuditService(repository, new ObjectMapper());

  @Test
  void registraFalhaSanitizadaUmaUnicaVezPorRequest() {
    UUID anuncioId = UUID.randomUUID();
    UUID atorId = UUID.randomUUID();
    when(repository.existsByAcaoAndRecursoIdAndRequestId(
        "ANUNCIO_REMOCAO_MIDIAS_R2_FALHOU",
        anuncioId,
        "req-falha")).thenReturn(false);

    service.registrar(anuncioId, atorId, "req-falha", "FALHA_OPERACIONAL_R2");

    ArgumentCaptor<AuditoriaEventoEntity> captor =
        ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getResultado()).isEqualTo(ResultadoAuditoria.ERRO);
    assertThat(captor.getValue().getDepoisJson())
        .contains("\"etapa\":\"LIMPEZA_MIDIAS_R2\"")
        .contains("\"codigo\":\"FALHA_OPERACIONAL_R2\"")
        .doesNotContain("object")
        .doesNotContain("bucket");

    clearInvocations(repository);
    when(repository.existsByAcaoAndRecursoIdAndRequestId(
        "ANUNCIO_REMOCAO_MIDIAS_R2_FALHOU",
        anuncioId,
        "req-repetida")).thenReturn(true);
    service.registrar(anuncioId, atorId, "req-repetida", "FALHA_OPERACIONAL_R2");
    verify(repository, never()).save(any(AuditoriaEventoEntity.class));
  }
}
