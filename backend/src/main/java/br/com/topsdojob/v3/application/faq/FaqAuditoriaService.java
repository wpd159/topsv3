package br.com.topsdojob.v3.application.faq;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.faq.FaqEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FaqAuditoriaService {

  private final AuditoriaEventoRepository repository;
  private final ObjectMapper objectMapper;

  public FaqAuditoriaService(
      AuditoriaEventoRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public void registrar(
      UUID atorId,
      String acao,
      FaqEntity faq,
      String requestId,
      OffsetDateTime agora,
      Map<String, ?> antes) {
    repository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        atorId,
        acao,
        "FAQ",
        faq.getId(),
        json(antes),
        json(snapshot(faq)),
        requestId,
        agora));
  }

  public Map<String, Object> snapshot(FaqEntity faq) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("categoria", faq.getCategoria());
    value.put("status", faq.getStatus());
    value.put("ordem", faq.getOrdem());
    value.put("versao", faq.getVersao());
    return value;
  }

  private String json(Map<String, ?> value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar auditoria da FAQ", exception);
    }
  }
}
