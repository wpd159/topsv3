package br.com.topsdojob.v3.web.admin.outbox;

import br.com.topsdojob.v3.application.admin.outbox.AdminOutboxSimulacaoService;
import br.com.topsdojob.v3.application.admin.outbox.dto.AdminOutboxSimularProcessamentoRequestDto;
import br.com.topsdojob.v3.application.admin.outbox.dto.AdminOutboxSimularProcessamentoResponseDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOutboxSimulacaoController {

    private final AdminOutboxSimulacaoService service;

    public AdminOutboxSimulacaoController(AdminOutboxSimulacaoService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/outbox/{id}/simular-processamento-local")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOutboxSimularProcessamentoResponseDto simularProcessamentoLocal(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminOutboxSimularProcessamentoRequestDto request,
            @AuthenticationPrincipal AdminUserPrincipal actor,
            HttpServletRequest httpRequest) {
        return service.simularProcessamentoLocal(id, request, actor, RequestIdContext.current(httpRequest));
    }
}
