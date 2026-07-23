package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnuncioRemocaoFalhaAuditService {

  private static final String ACAO = "ANUNCIO_REMOCAO_MIDIAS_R2_FALHOU";

  private final AuditoriaEventoRepository auditoriaRepository;
  private final ObjectMapper objectMapper;

  public AdminAnuncioRemocaoFalhaAuditService(
      AuditoriaEventoRepository auditoriaRepository,
      ObjectMapper objectMapper) {
    this.auditoriaRepository = auditoriaRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(
      UUID anuncioId,
      UUID atorId,
      String requestId,
      String codigoSanitizado) {
    if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(ACAO, anuncioId, requestId)) {
      return;
    }
    Map<String, Object> resultado = new LinkedHashMap<>();
    resultado.put("etapa", "LIMPEZA_MIDIAS_R2");
    resultado.put("resultado", "ERRO");
    resultado.put("codigo", codigoSanitizado);
    auditoriaRepository.save(AuditoriaEventoEntity.registrarErro(
        UUID.randomUUID(),
        atorId,
        ACAO,
        "ANUNCIO",
        anuncioId,
        null,
        json(resultado),
        requestId,
        OffsetDateTime.now(ZoneOffset.UTC)));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar auditoria de erro R2", exception);
    }
  }
}
