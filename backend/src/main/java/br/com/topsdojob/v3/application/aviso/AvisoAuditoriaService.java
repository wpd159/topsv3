package br.com.topsdojob.v3.application.aviso;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.aviso.AvisoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AvisoAuditoriaService {

  private final AuditoriaEventoRepository repository;
  private final ObjectMapper objectMapper;

  public AvisoAuditoriaService(
      AuditoriaEventoRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public void registrar(
      UUID atorId,
      String acao,
      AvisoEntity aviso,
      String requestId,
      OffsetDateTime agora,
      Map<String, ?> antes) {
    repository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        atorId,
        acao,
        "AVISO",
        aviso.getId(),
        json(antes),
        json(snapshot(aviso)),
        requestId,
        agora));
  }

  public Map<String, Object> snapshot(AvisoEntity aviso) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("localExibicao", aviso.getLocalExibicao());
    value.put("frequenciaExibicao", aviso.getFrequenciaExibicao());
    value.put("status", aviso.getStatus());
    value.put("permiteDispensar", aviso.isPermiteDispensar());
    value.put("ativoDe", aviso.getAtivoDe());
    value.put("ativoAte", aviso.getAtivoAte());
    value.put("versao", aviso.getVersao());
    return value;
  }

  private String json(Map<String, ?> value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar auditoria do aviso", exception);
    }
  }
}
