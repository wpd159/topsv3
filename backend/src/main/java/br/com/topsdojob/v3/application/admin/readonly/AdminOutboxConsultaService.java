package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminOutboxDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminOutboxListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOutboxConsultaService {

    private static final int FILTER_MAX_LENGTH = 80;

    private final OutboxEventoRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdminOutboxConsultaService(
            OutboxEventoRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminOutboxListaItemDto> listar(
            int page,
            int size,
            StatusOutbox status,
            String tipoEvento,
            String entidadeTipo,
            OffsetDateTime criadoDe,
            OffsetDateTime criadoAte,
            AdminUserPrincipal actor) {
        var pageable = AdminReadOnlyPageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        Page<OutboxEventoEntity> result = outboxRepository.findAll(
                outboxSpec(
                        status,
                        filtroSeguro(tipoEvento),
                        filtroSeguro(entidadeTipo),
                        criadoDe,
                        criadoAte,
                        moderadorRestrito(actor)),
                pageable);
        return new AdminPaginaDto<>(
                result.getContent().stream().map(this::item).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminOutboxDetalheDto detalhar(UUID id, AdminUserPrincipal actor) {
        OutboxEventoEntity entity = outboxRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "outbox nao encontrado"));
        if (moderadorRestrito(actor) && !AdminOutboxSanitizer.eventoModeracao(entity.getTipoEvento())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "outbox fora do escopo de moderacao");
        }
        Map<String, Object> dados = AdminOutboxSanitizer.dadosSanitizados(entity.getPayloadJson(), objectMapper);
        return new AdminOutboxDetalheDto(
                entity.getId(),
                entity.getTipoEvento(),
                entity.getAggregateTipo(),
                entity.getAggregateId(),
                enumName(entity.getStatus()),
                entity.getCriadoEm(),
                entity.getTentativas(),
                entity.getProximaTentativaEm(),
                AdminOutboxSanitizer.resumo(entity),
                AdminOutboxSanitizer.previa(entity, dados),
                dados,
                false,
                true);
    }

    private AdminOutboxListaItemDto item(OutboxEventoEntity entity) {
        Map<String, Object> dados = AdminOutboxSanitizer.dadosSanitizados(entity.getPayloadJson(), objectMapper);
        return new AdminOutboxListaItemDto(
                entity.getId(),
                entity.getTipoEvento(),
                entity.getAggregateTipo(),
                entity.getAggregateId(),
                enumName(entity.getStatus()),
                entity.getCriadoEm(),
                entity.getTentativas(),
                entity.getProximaTentativaEm(),
                AdminOutboxSanitizer.resumo(entity),
                AdminOutboxSanitizer.previa(entity, dados),
                false);
    }

    private Specification<OutboxEventoEntity> outboxSpec(
            StatusOutbox status,
            String tipoEvento,
            String entidadeTipo,
            OffsetDateTime criadoDe,
            OffsetDateTime criadoAte,
            boolean somenteModeracao) {
        return (root, query, builder) -> {
            var predicate = builder.conjunction();
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (tipoEvento != null) {
                predicate = builder.and(predicate, builder.equal(root.get("tipoEvento"), tipoEvento));
            }
            if (entidadeTipo != null) {
                predicate = builder.and(predicate, builder.equal(root.get("aggregateTipo"), entidadeTipo));
            }
            if (criadoDe != null) {
                predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("criadoEm"), criadoDe));
            }
            if (criadoAte != null) {
                predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.get("criadoEm"), criadoAte));
            }
            if (somenteModeracao) {
                predicate = builder.and(predicate, root.get("tipoEvento").in(
                        "MODERACAO_SOLICITAR_AJUSTE",
                        "MODERACAO_REPROVADA",
                        "ANUNCIO_REMETIDO_REVISAO"));
            }
            return predicate;
        };
    }

    private boolean moderadorRestrito(AdminUserPrincipal actor) {
        return actor != null
                && !actor.papeis().contains(PapelUsuario.ADMIN)
                && actor.papeis().contains(PapelUsuario.MODERADOR);
    }

    private String filtroSeguro(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = AdminTextoSanitizer.resumo(value, FILTER_MAX_LENGTH);
        if (sanitized == null || sanitized.isBlank()) {
            return null;
        }
        return sanitized.toUpperCase(Locale.ROOT);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
