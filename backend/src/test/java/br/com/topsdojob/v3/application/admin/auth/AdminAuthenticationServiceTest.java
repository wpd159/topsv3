package br.com.topsdojob.v3.application.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminLoginRequestDto;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

class AdminAuthenticationServiceTest {

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginValidoRetornaSessaoSemSenhaHashOuTokenNoBody() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        AdminUserPrincipal principal = principal();
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()));
        AdminAuthenticationService service = service(manager);

        var response = service.login(
                new AdminLoginRequestDto("ADMIN.LOCAL@EXAMPLE.INVALID", "valor-sintetico"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(response.autenticado()).isTrue();
        assertThat(response.email()).isEqualTo("admin.local@example.invalid");
        assertThat(response.papeis()).containsExactly("ADMIN");
        assertThat(response.permissoes()).contains("ADMIN_CONFIGURAR");
        assertThat(response.toString())
                .doesNotContain("valor-sintetico")
                .doesNotContain("$2a$")
                .doesNotContain("JSESSIONID")
                .doesNotContain("token");
    }

    @Test
    void loginInvalidoRetorna401Generico() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("detalhe interno"));
        AdminAuthenticationService service = service(manager);

        assertThatThrownBy(() -> service.login(
                new AdminLoginRequestDto("admin.local@example.invalid", "x"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void meSemAutenticacaoRetorna401() {
        AdminAuthenticationService service = service(mock(AuthenticationManager.class));

        assertThatThrownBy(() -> service.me(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void cincoFalhasBloqueiamLoginPorLoginNormalizadoEIpHash() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("detalhe interno"));
        AdminAuthenticationService service = service(manager);

        for (int index = 0; index < 5; index++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.10");
            assertThatThrownBy(() -> service.login(
                    new AdminLoginRequestDto("Admin.Local@Example.Invalid", "senha-invalida"),
                    request,
                    new MockHttpServletResponse()))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        MockHttpServletRequest mesmaContaOutroIp = new MockHttpServletRequest();
        mesmaContaOutroIp.setRemoteAddr("203.0.113.11");
        assertThatThrownBy(() -> service.login(
                new AdminLoginRequestDto("admin.local@example.invalid", "senha-invalida"),
                mesmaContaOutroIp,
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        MockHttpServletRequest outroLoginMesmoIp = new MockHttpServletRequest();
        outroLoginMesmoIp.setRemoteAddr("203.0.113.10");
        assertThatThrownBy(() -> service.login(
                new AdminLoginRequestDto("outro.admin@example.invalid", "senha-invalida"),
                outroLoginMesmoIp,
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void loginSucessoTrocaIdDaSessaoAntesDeSalvarContexto() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        AdminUserPrincipal principal = principal();
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()));
        AdminAuthenticationService service = service(manager);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        String sessionIdAnterior = session.getId();

        service.login(
                new AdminLoginRequestDto("admin.local@example.invalid", "valor-sintetico"),
                request,
                new MockHttpServletResponse());

        assertThat(request.getSession(false).getId()).isNotEqualTo(sessionIdAnterior);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void loginFalhoNaoAutenticaSessao() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("detalhe interno"));
        AdminAuthenticationService service = service(manager);

        assertThatThrownBy(() -> service.login(
                new AdminLoginRequestDto("admin.local@example.invalid", "senha-invalida"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void logoutInvalidaSessao() {
        AdminAuthenticationService service = service(mock(AuthenticationManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);

        var response = service.logout(request);

        assertThat(response.autenticado()).isFalse();
        assertThat(session.isInvalid()).isTrue();
    }

    private AdminAuthenticationService service(AuthenticationManager manager) {
        return new AdminAuthenticationService(
                manager,
                new HttpSessionSecurityContextRepository(),
                new AdminLoginLockoutService(new MetricaPublicaHashService("salt-sintetico", "local")),
                new PublicSessionRegistry());
    }

    private AdminUserPrincipal principal() {
        return new AdminUserPrincipal(
                UUID.fromString("00000000-0000-4000-8000-000000000901"),
                "Admin Sintetico Local",
                "admin.local@example.invalid",
                "$2a$10$hash-sintetico-nao-real",
                List.of(br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario.ADMIN),
                List.of(new AdminPermissionDto("ADMIN_CONFIGURAR", "Configuracao administrativa local")),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ADMIN_CONFIGURAR")),
                true);
    }
}
