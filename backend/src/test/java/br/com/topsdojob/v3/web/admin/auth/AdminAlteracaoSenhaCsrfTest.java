package br.com.topsdojob.v3.web.admin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.AdminAuthenticationService;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.publico.auth.MinhaContaSegurancaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAccountActionDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminAuthController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminAlteracaoSenhaCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAuthenticationService authenticationService;

    @MockBean
    private MinhaContaSegurancaService segurancaService;

    @Test
    void alteracaoSemCsrfEhRecusadaAntesDoServico() throws Exception {
        mockMvc.perform(post("/api/admin/auth/password")
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isForbidden());

        verify(segurancaService, never())
                .alterarSenhaAdministrativa(any(), any(), anyString());
    }

    @Test
    void alteracaoComCsrfUsaPrincipalAdministrativoEContratoCanonico() throws Exception {
        when(segurancaService.alterarSenhaAdministrativa(any(), any(), anyString()))
                .thenReturn(new PublicAccountActionDto(
                        "Sua senha foi alterada com sucesso. Entre novamente."));

        mockMvc.perform(post("/api/admin/auth/password")
                        .with(authentication(adminAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Sua senha foi alterada com sucesso. Entre novamente."));

        verify(segurancaService)
                .alterarSenhaAdministrativa(any(), any(Authentication.class), anyString());
    }

    @Test
    void alteracaoSemSessaoAdministrativaEhRecusada() throws Exception {
        mockMvc.perform(post("/api/admin/auth/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isUnauthorized());

        verify(segurancaService, never())
                .alterarSenhaAdministrativa(any(), any(), anyString());
    }

    @Test
    void senhaAtualIncorretaRetornaMensagemFuncionalSemAlterarContrato() throws Exception {
        when(segurancaService.alterarSenhaAdministrativa(any(), any(), anyString()))
                .thenThrow(new PublicAuthException(
                        HttpStatus.UNAUTHORIZED,
                        "Senha atual incorreta."));

        mockMvc.perform(post("/api/admin/auth/password")
                        .with(authentication(adminAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Senha atual incorreta."));
    }

    private String payload() {
        return """
                {
                  "senhaAtual":"EXEMPLO_NAO_REAL",
                  "novaSenha":"EXEMPLO_NAO_REAL",
                  "confirmarSenha":"EXEMPLO_NAO_REAL"
                }
                """;
    }

    private Authentication adminAuthentication() {
        AdminUserPrincipal principal = new AdminUserPrincipal(
                UUID.fromString("00000000-0000-4000-8000-000000009101"),
                "Admin Sintético",
                "admin@example.invalid",
                "hash-sintetico",
                List.of(PapelUsuario.ADMIN),
                List.of(new AdminPermissionDto(
                        "ADMIN_CONFIGURAR",
                        "Permissão sintética")),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ADMIN_CONFIGURAR")),
                true);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
    }
}
