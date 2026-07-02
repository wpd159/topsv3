package br.com.topsdojob.v3.application.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminLoginRequestDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

class AdminAuthenticationServiceTest {

    @Test
    void loginValidoRetornaSessaoSemSenhaHashOuTokenNoBody() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        AdminUserPrincipal principal = principal();
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()));
        AdminAuthenticationService service =
                new AdminAuthenticationService(manager, new HttpSessionSecurityContextRepository());

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
        AdminAuthenticationService service =
                new AdminAuthenticationService(manager, new HttpSessionSecurityContextRepository());

        assertThatThrownBy(() -> service.login(
                new AdminLoginRequestDto("admin.local@example.invalid", "x"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void meSemAutenticacaoRetorna401() {
        AdminAuthenticationService service =
                new AdminAuthenticationService(mock(AuthenticationManager.class), new HttpSessionSecurityContextRepository());

        assertThatThrownBy(() -> service.me(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
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
