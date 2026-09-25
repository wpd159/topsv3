package br.com.topsdojob.v3.application.admin.arquivo;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra acessos ao Story preservado sem replicar conteudo privado. */
@Service
public class AdminArquivoStoryAccessAuditService {
  private final AuditoriaEventoRepository repository;

  public AdminArquivoStoryAccessAuditService(AuditoriaEventoRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(UUID atorId, UUID veiculacaoId, String acao, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade) {
    String dados = "{\"dadosPrivadosOcultos\":true,\"finalidade\":\"" + finalidade.name() + "\"}";
    repository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(), atorId, acao, "ARQUIVO_PUBLICIDADE_STORY", veiculacaoId,
        null, dados, requestId, OffsetDateTime.now(ZoneOffset.UTC)));
  }
}
