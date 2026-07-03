package br.com.topsdojob.v3.application.admin.outbox.template;

import br.com.topsdojob.v3.application.admin.readonly.AdminOutboxSanitizer;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOutboxPreviewService {

    private final OutboxEventoRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final AdminOutboxTemplateRenderer renderer;

    public AdminOutboxPreviewService(
            OutboxEventoRepository outboxRepository,
            ObjectMapper objectMapper,
            AdminOutboxTemplateRenderer renderer) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public AdminOutboxPreviewRenderizadaDto preview(UUID id, AdminUserPrincipal actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao admin obrigatoria");
        }
        OutboxEventoEntity entity = outboxRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "outbox nao encontrado"));
        if (!podeConsultarPreview(actor, entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "preview de outbox fora do escopo permitido");
        }
        Map<String, Object> dados = AdminOutboxSanitizer.dadosSanitizados(entity.getPayloadJson(), objectMapper);
        return renderer.renderizar(entity, dados);
    }

    private boolean podeConsultarPreview(AdminUserPrincipal actor, OutboxEventoEntity entity) {
        if (actor.papeis().contains(PapelUsuario.ADMIN)) {
            return true;
        }
        return actor.papeis().contains(PapelUsuario.MODERADOR)
                && AdminOutboxSanitizer.eventoModeracao(entity.getTipoEvento());
    }
}
