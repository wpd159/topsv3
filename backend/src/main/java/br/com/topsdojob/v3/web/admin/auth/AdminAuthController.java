package br.com.topsdojob.v3.web.admin.auth;

import br.com.topsdojob.v3.application.admin.auth.AdminAuthenticationService;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminAuthStatusDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminLoginRequestDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminMeDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionsDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthenticationService authenticationService;

    public AdminAuthController(AdminAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
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
    public AdminMeDto me(Authentication authentication) {
        return authenticationService.me(authentication);
    }

    @GetMapping("/permissions")
    public AdminPermissionsDto permissions(Authentication authentication) {
        return authenticationService.permissions(authentication);
    }
}
