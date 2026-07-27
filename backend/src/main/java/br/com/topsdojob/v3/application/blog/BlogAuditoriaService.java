package br.com.topsdojob.v3.application.blog;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BlogAuditoriaService {

  private final AuditoriaEventoRepository repository;
  private final ObjectMapper objectMapper;

  public BlogAuditoriaService(AuditoriaEventoRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public void registrar(
      UUID atorId,
      String acao,
      String recursoTipo,
      UUID recursoId,
      String requestId,
      OffsetDateTime agora,
      Map<String, ?> antes,
      Map<String, ?> depois) {
    repository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        atorId,
        acao,
        recursoTipo,
        recursoId,
        json(antes),
        json(depois),
        requestId,
        agora));
  }

  public Map<String, Object> postSnapshot(String slug, String status, long versao) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("slug", slug);
    snapshot.put("status", status);
    snapshot.put("versao", versao);
    return snapshot;
  }

  private String json(Map<String, ?> value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar auditoria do Blog", exception);
    }
  }
}
