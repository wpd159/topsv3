package br.com.topsdojob.v3.application.admin.auth;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminAuthStatusDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminLoginRequestDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminMeDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionsDto;
import br.com.topsdojob.v3.security.admin.AdminUserDetailsService;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthenticationService {

    private static final String STATUS_LOGOUT_OK = "LOGOUT_OK";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AdminLoginLockoutService lockoutService;

    public AdminAuthenticationService(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AdminLoginLockoutService lockoutService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.lockoutService = lockoutService;
    }

    public AdminMeDto login(
            AdminLoginRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (request == null || isBlank(request.login()) || isBlank(request.senha())) {
            throw unauthorized();
        }
        String normalizedLogin = AdminUserDetailsService.normalizarLogin(request.login());
        AdminLoginLockoutService.LoginAttemptContext attemptContext =
                lockoutService.context(normalizedLogin, httpRequest);
        if (lockoutService.isBlocked(attemptContext)) {
            throw tooManyRequests();
        }
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    normalizedLogin,
                    request.senha()));
            rotateSessionId(httpRequest);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            lockoutService.registerSuccess(attemptContext);
            return me(authentication);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            lockoutService.registerFailure(attemptContext);
            throw unauthorized();
        }
    }

    public AdminMeDto me(Authentication authentication) {
        AdminUserPrincipal principal = principal(authentication);
        return new AdminMeDto(
                true,
                principal.usuarioId(),
                principal.nome(),
                principal.email(),
                principal.papeis().stream().map(Enum::name).toList(),
                principal.permissoes().stream().map(permissao -> permissao.codigo()).toList());
    }

    public AdminPermissionsDto permissions(Authentication authentication) {
        AdminUserPrincipal principal = principal(authentication);
        return new AdminPermissionsDto(List.copyOf(principal.permissoes()));
    }

    public AdminAuthStatusDto logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request != null) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        return new AdminAuthStatusDto(false, STATUS_LOGOUT_OK);
    }

    private AdminUserPrincipal principal(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminUserPrincipal principal)) {
            throw unauthorized();
        }
        return principal;
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciais invalidas");
    }

    private ResponseStatusException tooManyRequests() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "credenciais invalidas");
    }

    private void rotateSessionId(HttpServletRequest request) {
        if (request != null) {
            request.getSession();
            request.changeSessionId();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
