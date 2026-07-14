package br.com.topsdojob.v3.web.publico.pagamento;

import br.com.topsdojob.v3.application.publico.pagamento.EfiWebhookService;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiWebhookResultadoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/webhooks/efi")
public class EfiWebhookController {

    private final EfiWebhookService service;

    public EfiWebhookController(EfiWebhookService service) {
        this.service = service;
    }

    @PostMapping(
            path = {"", "/pix"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public EfiWebhookResultadoDto receber(
            @RequestParam String hmac,
            @RequestBody String payload,
            HttpServletRequest request) {
        String origem = request.getHeader("X-Real-IP");
        if (origem == null || origem.isBlank()) {
            origem = request.getRemoteAddr();
        }
        return service.receber(
                hmac,
                payload,
                origem,
                RequestIdContext.current(request));
    }
}
