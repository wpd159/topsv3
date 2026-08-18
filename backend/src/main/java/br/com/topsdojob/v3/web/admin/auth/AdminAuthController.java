package br.com.topsdojob.v3.web.admin.auth;

import br.com.topsdojob.v3.application.admin.auth.AdminAuthenticationService;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminAuthStatusDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminLoginRequestDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminMeDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionsDto;
import br.com.topsdojob.v3.application.publico.auth.MinhaContaSegurancaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.dto.MinhaContaAlterarSenhaRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAccountActionDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAuthErrorDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthenticationService authenticationService;
    private final MinhaContaSegurancaService segurancaService;

    public AdminAuthController(
            AdminAuthenticationService authenticationService,
            MinhaContaSegurancaService segurancaService) {
        this.authenticationService = authenticationService;
        this.segurancaService = segurancaService;
    }

    @PostMapping("/login")
    public AdminMeDto login(
            @RequestBody(required = false) AdminLoginRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return authenticationService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    public AdminAuthStatusDto logout(HttpServletRequest request) {
        return authenticationService.logout(request);
    }

    @GetMapping("/me")
    public AdminMeDto me(Authentication authentication, CsrfToken csrfToken) {
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        return authenticationService.me(authentication);
    }

    @GetMapping("/permissions")
    public AdminPermissionsDto permissions(Authentication authentication) {
        return authenticationService.permissions(authentication);
    }

    @PostMapping("/password")
    public PublicAccountActionDto alterarSenha(
            @RequestBody(required = false) MinhaContaAlterarSenhaRequestDto request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return segurancaService.alterarSenhaAdministrativa(
                request,
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
}
