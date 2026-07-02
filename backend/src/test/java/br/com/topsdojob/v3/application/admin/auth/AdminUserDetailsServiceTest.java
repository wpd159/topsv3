package br.com.topsdojob.v3.application.admin.auth;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserDetailsService;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AdminUserDetailsServiceTest {

    @Test
    void carregaUsuarioAdminComPapeisPermissoesSemExporCredencialNoPrincipalPublico() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        CredencialUsuarioRepository credencialRepository = mock(CredencialUsuarioRepository.class);
        AdminRbacService rbacService = mock(AdminRbacService.class);
        UsuarioEntity usuario = entity(UsuarioEntity.class);
        set(usuario, "id", usuarioId);
        set(usuario, "nome", "Admin Sintetico Local");
        set(usuario, "emailNormalizado", "admin.local@example.invalid");
        set(usuario, "status", StatusUsuario.ATIVO);
        CredencialUsuarioEntity credencial = entity(CredencialUsuarioEntity.class);
        set(credencial, "usuarioId", usuarioId);
        set(credencial, "senhaHash", "$2a$10$hash-sintetico-nao-real");
        set(credencial, "algoritmo", "BCRYPT");

        when(usuarioRepository.findByEmailNormalizado("admin.local@example.invalid")).thenReturn(Optional.of(usuario));
        when(credencialRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(credencial));
        when(rbacService.papeis(usuarioId)).thenReturn(List.of(PapelUsuario.ADMIN));
        when(rbacService.permissoes(List.of(PapelUsuario.ADMIN)))
                .thenReturn(List.of(new AdminPermissionDto("ADMIN_CONFIGURAR", "Configuracao local")));

        AdminUserPrincipal principal = (AdminUserPrincipal) new AdminUserDetailsService(
                usuarioRepository,
                credencialRepository,
                rbacService)
                .loadUserByUsername("ADMIN.LOCAL@EXAMPLE.INVALID");

        assertThat(principal.usuarioId()).isEqualTo(usuarioId);
        assertThat(principal.papeis()).containsExactly(PapelUsuario.ADMIN);
        assertThat(principal.permissoes()).extracting(AdminPermissionDto::codigo).containsExactly("ADMIN_CONFIGURAR");
        assertThat(principal.getAuthorities()).extracting(Object::toString)
                .contains("ROLE_ADMIN", "ADMIN_CONFIGURAR");
    }

    @Test
    void usuarioSemPapelNaoAutentica() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        CredencialUsuarioRepository credencialRepository = mock(CredencialUsuarioRepository.class);
        AdminRbacService rbacService = mock(AdminRbacService.class);
        UsuarioEntity usuario = entity(UsuarioEntity.class);
        set(usuario, "id", usuarioId);
        set(usuario, "emailNormalizado", "admin.local@example.invalid");
        set(usuario, "status", StatusUsuario.ATIVO);
        CredencialUsuarioEntity credencial = entity(CredencialUsuarioEntity.class);
        set(credencial, "usuarioId", usuarioId);
        set(credencial, "senhaHash", "$2a$10$hash-sintetico-nao-real");
        set(credencial, "algoritmo", "BCRYPT");

        when(usuarioRepository.findByEmailNormalizado("admin.local@example.invalid")).thenReturn(Optional.of(usuario));
        when(credencialRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(credencial));
        when(rbacService.papeis(usuarioId)).thenReturn(List.of());

        AdminUserDetailsService service = new AdminUserDetailsService(usuarioRepository, credencialRepository, rbacService);

        assertThatThrownBy(() -> service.loadUserByUsername("admin.local@example.invalid"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginDesconhecidoUsaErroGenerico() {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        when(usuarioRepository.findByEmailNormalizado("ausente.local@example.invalid")).thenReturn(Optional.empty());

        AdminUserDetailsService service = new AdminUserDetailsService(
                usuarioRepository,
                mock(CredencialUsuarioRepository.class),
                mock(AdminRbacService.class));

        assertThatThrownBy(() -> service.loadUserByUsername("ausente.local@example.invalid"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("credenciais invalidas");
    }
}
