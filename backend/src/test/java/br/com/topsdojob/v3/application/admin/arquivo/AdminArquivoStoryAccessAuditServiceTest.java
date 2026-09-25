package br.com.topsdojob.v3.application.admin.arquivo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminArquivoStoryAccessAuditServiceTest {
  @Test
  void registraFinalidadeControladaSemDadosPrivados() {
    AuditoriaEventoRepository repository = mock(AuditoriaEventoRepository.class);
    var service = new AdminArquivoStoryAccessAuditService(repository);
    UUID atorId = UUID.randomUUID();
    UUID veiculacaoId = UUID.randomUUID();

    service.registrar(atorId, veiculacaoId,
        "ARQUIVO_PUBLICIDADE_STORY_EXPORTACAO_PREPARADA", "request-story-1",
        FinalidadeAcessoArquivoPublicidade.APURACAO_INCIDENTE);

    ArgumentCaptor<AuditoriaEventoEntity> captor =
        ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(repository).save(captor.capture());
    AuditoriaEventoEntity evento = captor.getValue();
    assertEquals(atorId, evento.getAtorUsuarioId());
    assertEquals(veiculacaoId, evento.getRecursoId());
    assertEquals("ARQUIVO_PUBLICIDADE_STORY", evento.getRecursoTipo());
    assertEquals("ARQUIVO_PUBLICIDADE_STORY_EXPORTACAO_PREPARADA", evento.getAcao());
    assertTrue(evento.getDepoisJson().contains("\"finalidade\":\"APURACAO_INCIDENTE\""));
    assertFalse(evento.getDepoisJson().contains(atorId.toString()));
  }
}
