package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteResponseDto;
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
public class AdminModeracaoFotosLoteAuditoriaService {

    private static final String ACAO_LOTE = "MODERACAO_FOTOS_LOTE";
    private static final String ACAO_FALHA = "MODERACAO_FOTO_LOTE_FALHOU";

    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public AdminModeracaoFotosLoteAuditoriaService(
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper) {
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarLote(
            UUID atorId,
            AdminDecidirFotosLoteResponseDto resultado) {
        if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(
                ACAO_LOTE,
                resultado.anuncioId(),
                resultado.requestId())) {
            return;
        }
        Map<String, Object> depois = new LinkedHashMap<>();
        depois.put("aprovadas", resultado.aprovadas());
        depois.put("excluidas", resultado.excluidas());
        depois.put("jaProcessadas", resultado.jaProcessadas());
        depois.put("falhas", resultado.falhas());
        depois.put("concluido", resultado.concluido());
        depois.put("resultadosIndividuaisRegistrados", true);
        depois.put("resultados", resultado.resultados().stream().map(item -> {
            Map<String, Object> resumo = new LinkedHashMap<>();
            resumo.put("mediaId", item.mediaId());
            resumo.put("decisao", item.decisao());
            resumo.put("classificacao", item.classificacao());
            resumo.put("resultado", item.resultado());
            resumo.put("status", item.status());
            resumo.put("motivoSanitizado", item.motivo());
            return resumo;
        }).toList());
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                atorId,
                ACAO_LOTE,
                "ANUNCIO",
                resultado.anuncioId(),
                null,
                json(depois),
                resultado.requestId(),
                resultado.processadoEm()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalha(
            UUID atorId,
            UUID mediaId,
            String requestId,
            String codigoSanitizado) {
        if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(
                ACAO_FALHA,
                mediaId,
                requestId)) {
            return;
        }
        Map<String, Object> depois = new LinkedHashMap<>();
        depois.put("resultado", "FALHA");
        depois.put("codigo", codigoSanitizado);
        depois.put("storageOculto", true);
        auditoriaRepository.save(AuditoriaEventoEntity.registrarErro(
                UUID.randomUUID(),
                atorId,
                ACAO_FALHA,
                "ANUNCIO_MIDIA",
                mediaId,
                null,
                json(depois),
                requestId,
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria do lote de fotos", exception);
        }
    }
}
