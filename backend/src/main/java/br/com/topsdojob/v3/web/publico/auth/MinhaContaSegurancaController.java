package br.com.topsdojob.v3.web.publico.auth;

import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoElegibilidadeDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioExclusaoBloqueadaException;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.MinhaContaSegurancaService;
import br.com.topsdojob.v3.application.publico.auth.dto.MinhaContaAlterarSenhaRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.MinhaContaExcluirRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAccountActionDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAuthErrorDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/minha-conta/seguranca")
public class MinhaContaSegurancaController {

    private final MinhaContaSegurancaService service;

    public MinhaContaSegurancaController(MinhaContaSegurancaService service) {
        this.service = service;
    }

    @PostMapping("/senha")
    public PublicAccountActionDto alterarSenha(
            @RequestBody(required = false) MinhaContaAlterarSenhaRequestDto request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return service.alterarSenha(
                request,
                authentication,
                RequestIdContext.current(httpRequest));
    }

    @GetMapping("/exclusao")
    public AdminUsuarioExclusaoElegibilidadeDto elegibilidade(
            Authentication authentication) {
        return service.elegibilidade(authentication);
    }

    @PostMapping("/exclusao")
    public AdminUsuarioExclusaoResultadoDto excluir(
            @RequestBody(required = false) MinhaContaExcluirRequestDto request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return service.excluir(
                request,
                idempotencyKey,
                authentication,
                RequestIdContext.current(httpRequest));
    }

    @ExceptionHandler(PublicAuthException.class)
    public ResponseEntity<PublicAuthErrorDto> handlePublicAuth(
            PublicAuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(new PublicAuthErrorDto(
                        exception.status().value(),
                        exception.getMessage()));
    }

    @ExceptionHandler(AdminUsuarioExclusaoBloqueadaException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleBlockedDeletion(
            AdminUsuarioExclusaoBloqueadaException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of(
                "message", exception.getMessage(),
                "bloqueios", exception.bloqueios()));
    }
}
