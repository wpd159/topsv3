package br.com.topsdojob.v3.application.admin.outbox;

import br.com.topsdojob.v3.application.admin.outbox.dto.AdminOutboxSimularProcessamentoRequestDto;
import br.com.topsdojob.v3.application.admin.outbox.dto.AdminOutboxSimularProcessamentoResponseDto;
import br.com.topsdojob.v3.application.admin.readonly.AdminOutboxSanitizer;
import br.com.topsdojob.v3.application.admin.readonly.AdminTextoSanitizer;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOutboxSimulacaoService {

    private static final int OBSERVACAO_MAX_LENGTH = 240;
    private static final int REQUEST_ID_CLIENTE_MAX_LENGTH = 120;

    private final OutboxEventoRepository outboxRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;
    private final boolean ambienteLocal;

    public AdminOutboxSimulacaoService(
            OutboxEventoRepository outboxRepository,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper,
            @Value("${app.env:nao_configurado}") String appEnv) {
        this.outboxRepository = outboxRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
        this.ambienteLocal = "local".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim());
    }

    @Transactional
    public AdminOutboxSimularProcessamentoResponseDto simularProcessamentoLocal(
            UUID id,
            AdminOutboxSimularProcessamentoRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        if (!ambienteLocal) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "simulacao de outbox disponivel somente em APP_ENV=local");
        }
        if (actor == null || !actor.papeis().contains(PapelUsuario.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "somente ADMIN pode simular processamento local");
        }
        OutboxEventoEntity entity = outboxRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "outbox nao encontrado"));
        StatusOutbox statusAntes = entity.getStatus();
        if (statusAntes != StatusOutbox.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "outbox nao pendente para simulacao local");
        }

        OffsetDateTime agora = OffsetDateTime.now();
        String observacao = AdminTextoSanitizer.resumo(request == null ? null : request.observacao(), OBSERVACAO_MAX_LENGTH);
        String requestIdCliente = AdminTextoSanitizer.resumo(
                request == null ? null : request.requestIdCliente(),
                REQUEST_ID_CLIENTE_MAX_LENGTH);
        Map<String, Object> dados = AdminOutboxSanitizer.dadosSanitizados(entity.getPayloadJson(), objectMapper);
        String antes = snapshot(entity, statusAntes, false, observacao, requestIdCliente, dados);
        entity.marcarProcessadoPorSimulacaoLocal(agora);
        outboxRepository.save(entity);
        String depois = snapshot(entity, entity.getStatus(), true, observacao, requestIdCliente, dados);

        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                actor.usuarioId(),
                "OUTBOX_SIMULACAO_LOCAL",
                "OUTBOX_EVENTO",
                entity.getId(),
                antes,
                depois,
                requestId,
                agora));

        return new AdminOutboxSimularProcessamentoResponseDto(
                entity.getId(),
                enumName(statusAntes),
                enumName(entity.getStatus()),
                true,
                false,
                true,
                requestId,
                agora,
                "processamento local simulado; nenhuma comunicacao externa enviada");
    }

    private String snapshot(
            OutboxEventoEntity entity,
            StatusOutbox status,
            boolean simulacaoExecutada,
            String observacao,
            String requestIdCliente,
            Map<String, Object> dadosSanitizados) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("outboxId", entity.getId());
        values.put("tipoEvento", AdminTextoSanitizer.resumo(entity.getTipoEvento(), 120));
        values.put("entidadeTipo", AdminTextoSanitizer.resumo(entity.getAggregateTipo(), 80));
        values.put("entidadeId", entity.getAggregateId());
        values.put("status", enumName(status));
        values.put("statusAlteradoPorSimulacao", simulacaoExecutada);
        values.put("observacaoSanitizada", observacao);
        values.put("requestIdClienteReservado", requestIdCliente != null);
        values.put("dadosSanitizados", dadosSanitizados);
        values.put("payloadBrutoExposto", false);
        values.put("contatoRealExposto", false);
        values.put("documentoPrivadoExposto", false);
        values.put("storagePrivadoExposto", false);
        values.put("financeiroExposto", false);
        values.put("envioExternoExecutado", false);
        values.put("workerExecutado", false);
        values.put("schedulerExecutado", false);
        return toJson(values);
    }

    private String toJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falha ao registrar auditoria");
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
