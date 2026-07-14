package br.com.topsdojob.v3.web.publico.auth;

import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthenticationService;
import br.com.topsdojob.v3.application.publico.auth.PublicAccountLifecycleService;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAccountActionDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicCodeRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicEmailRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicResetPasswordRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAuthErrorDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAuthStatusDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicDuplicidadeDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicLoginRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicProfileUpdateRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicRegisterRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicUserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicAuthController {

    private final PublicAuthenticationService authenticationService;
    private final PublicAccountLifecycleService accountLifecycleService;
    private final boolean sessionCookieSecure;

    public PublicAuthController(
            PublicAuthenticationService authenticationService,
            PublicAccountLifecycleService accountLifecycleService,
            @Value("${server.servlet.session.cookie.secure:true}") boolean sessionCookieSecure) {
        this.authenticationService = authenticationService;
        this.accountLifecycleService = accountLifecycleService;
        this.sessionCookieSecure = sessionCookieSecure;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<PublicUserDto> register(@RequestBody(required = false) PublicRegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
    }

    @PostMapping("/auth/login")
    public PublicUserDto login(
            @RequestBody(required = false) PublicLoginRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return authenticationService.login(request, httpRequest, httpResponse);
    }

    @GetMapping("/auth/me")
    public PublicUserDto me(Authentication authentication, CsrfToken csrfToken) {
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        return authenticationService.me(authentication);
    }

    @PatchMapping("/auth/me")
    public PublicUserDto updateProfile(
            @RequestBody(required = false) PublicProfileUpdateRequestDto request,
            Authentication authentication) {
        return authenticationService.updateProfile(request, authentication);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<PublicAuthStatusDto> logout(HttpServletRequest request) {
        PublicAuthStatusDto status = authenticationService.logout(request);
        ResponseCookie expiredSessionCookie = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .maxAge(Duration.ZERO)
                .httpOnly(true)
                .secure(sessionCookieSecure)
                .sameSite("Lax")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredSessionCookie.toString())
                .body(status);
    }

    @PostMapping("/auth/confirm")
    public PublicAccountActionDto confirm(@RequestBody(required = false) PublicCodeRequestDto request,
                                           HttpServletRequest httpRequest) {
        return accountLifecycleService.confirm(request, clientKey(httpRequest));
    }

    @PostMapping("/auth/resend-confirmation")
    public PublicAccountActionDto resend(@RequestBody(required = false) PublicEmailRequestDto request,
                                          HttpServletRequest httpRequest) {
        return accountLifecycleService.resend(request, clientKey(httpRequest));
    }

    @PostMapping("/auth/forgot-password")
    public PublicAccountActionDto forgot(@RequestBody(required = false) PublicEmailRequestDto request,
                                          HttpServletRequest httpRequest) {
        return accountLifecycleService.forgot(request, clientKey(httpRequest));
    }

    @PostMapping("/auth/validate-reset-code")
    public PublicAccountActionDto validateReset(@RequestBody(required = false) PublicCodeRequestDto request,
                                                 HttpServletRequest httpRequest) {
        return accountLifecycleService.validateReset(request, clientKey(httpRequest));
    }

    @PostMapping("/auth/reset-password")
    public PublicAccountActionDto reset(@RequestBody(required = false) PublicResetPasswordRequestDto request,
                                         HttpServletRequest httpRequest) {
        return accountLifecycleService.reset(request, clientKey(httpRequest));
    }

    @GetMapping("/usuarios/verificar-duplicidade")
    public PublicDuplicidadeDto duplicidade(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String telefone) {
        return authenticationService.duplicidade(email, username, telefone);
    }

    @ExceptionHandler(PublicAuthException.class)
    public ResponseEntity<PublicAuthErrorDto> handlePublicAuth(PublicAuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(new PublicAuthErrorDto(exception.status().value(), exception.getMessage()));
    }

    private String clientKey(HttpServletRequest request) {
        return request == null ? "unknown" : request.getRemoteAddr();
    }
}
