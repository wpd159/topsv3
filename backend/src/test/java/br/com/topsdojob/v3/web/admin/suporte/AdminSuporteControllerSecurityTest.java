package br.com.topsdojob.v3.web.admin.suporte;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.suporte.AdminSuporteService;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Indicadores;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Pagina;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminSuporteController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminSuporteControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminSuporteService service;

    @Test
    void adminEModeradorComPermissaoAcessamFila() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new Pagina<>(List.of(), 0, 20, 0, 0));
        when(service.indicadores()).thenReturn(new Indicadores(0, 0, 0, 0, 0, 0, 0));

        for (PapelUsuario papel : List.of(PapelUsuario.ADMIN, PapelUsuario.MODERADOR)) {
            mockMvc.perform(get("/api/admin/tickets").with(authentication(token(papel))))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/admin/tickets/indicadores").with(authentication(token(papel))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void usuarioESemPermissaoRecebem403() throws Exception {
        mockMvc.perform(get("/api/admin/tickets")
                        .with(user("usuario").roles("USUARIO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/tickets")
                        .with(authentication(token(PapelUsuario.MODERADOR, false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutacoesExigemCsrf() throws Exception {
        UUID ticketId = UUID.randomUUID();
        var autenticacao = authentication(token(PapelUsuario.ADMIN));

        mockMvc.perform(post("/api/admin/tickets/{id}/mensagens", ticketId)
                        .with(autenticacao)
                        .header("Idempotency-Key", "suporte-admin-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"Resposta QA\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/tickets/{id}/status", ticketId)
                        .with(authentication(token(PapelUsuario.ADMIN)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken token(PapelUsuario papel) {
        return token(papel, true);
    }

    private UsernamePasswordAuthenticationToken token(PapelUsuario papel, boolean comPermissao) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
        if (comPermissao) authorities.add(new SimpleGrantedAuthority("SUPORTE_ATENDER"));
        AdminUserPrincipal principal = new AdminUserPrincipal(
                UUID.randomUUID(),
                "Operador QA",
                "operador@example.invalid",
                "hash",
                List.of(papel),
                authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(value -> !value.startsWith("ROLE_"))
                        .map(value -> new AdminPermissionDto(value, value))
                        .toList(),
                authorities,
                true);
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), authorities);
    }
}
