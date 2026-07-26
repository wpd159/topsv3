package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.ConfirmarIdadePublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.StatusIdadePublicaDto;
import br.com.topsdojob.v3.application.publico.service.IdadePublicaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/idade")
public class IdadePublicaController {

    private final IdadePublicaService idadeService;

    public IdadePublicaController(IdadePublicaService idadeService) {
        this.idadeService = idadeService;
    }

    @PostMapping("/confirmar")
    public ResponseEntity<StatusIdadePublicaDto> confirmar(
            @RequestBody(required = false) ConfirmarIdadePublicaRequestDto request,
            HttpServletRequest httpRequest) {
        IdadePublicaService.ConfirmacaoIdadeResult result = idadeService.confirmar(request, httpRequest);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
                .body(result.status());
    }

    @GetMapping("/status")
    public StatusIdadePublicaDto status(HttpServletRequest request) {
        return idadeService.status(request);
    }
}
