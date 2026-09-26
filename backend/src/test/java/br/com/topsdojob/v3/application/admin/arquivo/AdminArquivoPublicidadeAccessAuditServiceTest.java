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

class AdminArquivoPublicidadeAccessAuditServiceTest {
  @Test
  void registraFinalidadeControladaSemCopiarDadosPrivados() {
    AuditoriaEventoRepository repository = mock(AuditoriaEventoRepository.class);
    AdminArquivoPublicidadeAccessAuditService service =
        new AdminArquivoPublicidadeAccessAuditService(repository);
    UUID atorId = UUID.randomUUID();
    UUID veiculacaoId = UUID.randomUUID();

    service.registrar(atorId, veiculacaoId, "ARQUIVO_PUBLICIDADE_EXPORTACAO_PREPARADA",
        "request-1", FinalidadeAcessoArquivoPublicidade.ATENDIMENTO_FISCALIZACAO);

    ArgumentCaptor<AuditoriaEventoEntity> captor =
        ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(repository).save(captor.capture());
    AuditoriaEventoEntity evento = captor.getValue();
    assertEquals(atorId, evento.getAtorUsuarioId());
    assertEquals(veiculacaoId, evento.getRecursoId());
    assertEquals("ARQUIVO_PUBLICIDADE_EXPORTACAO_PREPARADA", evento.getAcao());
    assertEquals("request-1", evento.getRequestId());
    assertTrue(evento.getDepoisJson().contains("\"finalidade\":\"ATENDIMENTO_FISCALIZACAO\""));
    assertFalse(evento.getDepoisJson().contains(atorId.toString()));
  }

  @Test
  void registraMidiaEspecificaSemCopiarDadosPrivados() {
    AuditoriaEventoRepository repository = mock(AuditoriaEventoRepository.class);
    var service = new AdminArquivoPublicidadeAccessAuditService(repository);
    UUID atorId = UUID.randomUUID();
    UUID veiculacaoId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();

    service.registrarMidia(atorId, veiculacaoId, midiaId,
        "ARQUIVO_PUBLICIDADE_MIDIA_PREPARADA", "request-2",
        FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA);

    ArgumentCaptor<AuditoriaEventoEntity> captor =
        ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(repository).save(captor.capture());
    AuditoriaEventoEntity evento = captor.getValue();
    assertEquals(veiculacaoId, evento.getRecursoId());
    assertEquals("request-2", evento.getRequestId());
    assertTrue(evento.getDepoisJson().contains("\"midiaId\":\"" + midiaId + "\""));
    assertTrue(evento.getDepoisJson().contains("\"finalidade\":\"AUDITORIA_INTERNA\""));
    assertFalse(evento.getDepoisJson().contains(atorId.toString()));
  }
}
