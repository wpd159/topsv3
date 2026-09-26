package br.com.topsdojob.v3.application.admin.arquivo;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A trilha existente registra o acesso, sem duplicar dados privados no evento. */
@Service
public class AdminArquivoPublicidadeAccessAuditService {
  private final AuditoriaEventoRepository repository;

  public AdminArquivoPublicidadeAccessAuditService(AuditoriaEventoRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(UUID atorId, UUID veiculacaoId, String acao, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade) {
    registrarInterno(atorId, veiculacaoId, null, acao, requestId, finalidade);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrarMidia(UUID atorId, UUID veiculacaoId, UUID midiaId, String acao,
      String requestId, FinalidadeAcessoArquivoPublicidade finalidade) {
    registrarInterno(atorId, veiculacaoId, midiaId, acao, requestId, finalidade);
  }

  private void registrarInterno(UUID atorId, UUID veiculacaoId, UUID midiaId, String acao,
      String requestId, FinalidadeAcessoArquivoPublicidade finalidade) {
    String dados = "{\"dadosPrivadosOcultos\":true";
    if (finalidade != null) dados += ",\"finalidade\":\"" + finalidade.name() + "\"";
    if (midiaId != null) dados += ",\"midiaId\":\"" + midiaId + "\"";
    dados += "}";
    repository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(), atorId, acao, "ARQUIVO_PUBLICIDADE", veiculacaoId,
        null, dados, requestId,
        OffsetDateTime.now(ZoneOffset.UTC)));
  }
}
