package br.com.topsdojob.v3.web.publico.chat;

import br.com.topsdojob.v3.application.publico.chat.ChatInternoService;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatConversaDetalheDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatConversaDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatEnviarMensagemRequestDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatMensagemDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatNaoLidasDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatNovaConversaRequestDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/chat")
public class ChatInternoController {

    private final ChatInternoService service;

    public ChatInternoController(ChatInternoService service) {
        this.service = service;
    }

    @GetMapping("/conversas")
    public List<ChatConversaDto> listar(Authentication authentication) {
        return service.listar(authentication);
    }

    @PostMapping("/conversas")
    public ChatConversaDto iniciar(
            @RequestBody ChatNovaConversaRequestDto request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return service.iniciar(
                request == null ? null : request.username(),
                authentication,
                RequestIdContext.current(servletRequest));
    }

    @GetMapping("/conversas/{conversaId}/mensagens")
    public ChatConversaDetalheDto detalhar(
            @PathVariable UUID conversaId,
            Authentication authentication) {
        return service.detalhar(conversaId, authentication);
    }

    @PostMapping("/conversas/{conversaId}/mensagens")
    public ChatMensagemDto enviar(
            @PathVariable UUID conversaId,
            @RequestBody ChatEnviarMensagemRequestDto request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return service.enviar(
                conversaId,
                request == null ? null : request.corpo(),
                idempotencyKey,
                authentication,
                RequestIdContext.current(servletRequest));
    }

    @PostMapping("/conversas/{conversaId}/leitura")
    public ChatNaoLidasDto marcarComoLida(
            @PathVariable UUID conversaId,
            Authentication authentication) {
        return service.marcarComoLida(conversaId, authentication);
    }

    @GetMapping("/nao-lidas")
    public ChatNaoLidasDto naoLidas(Authentication authentication) {
        return service.naoLidas(authentication);
    }
}
