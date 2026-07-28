package br.com.topsdojob.v3.web.admin.sugestao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Pagina;
import br.com.topsdojob.v3.application.sugestao.SugestaoService;
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
        controllers = AdminSugestaoController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminSugestaoControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SugestaoService service;

    @Test
    void adminEModeradorComSuporteAtenderAcessamFila() throws Exception {
        when(service.listar(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new Pagina<>(List.of(), 0, 20, 0, 0));

        for (PapelUsuario papel : List.of(PapelUsuario.ADMIN, PapelUsuario.MODERADOR)) {
            mockMvc.perform(get("/api/admin/sugestoes")
                            .with(authentication(tokenFor(papel, "SUPORTE_ATENDER"))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void perfilSemPermissaoRecebe403EAnonimo401() throws Exception {
        mockMvc.perform(get("/api/admin/sugestoes")
                        .with(authentication(tokenFor(PapelUsuario.MODERADOR))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/sugestoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mutacaoExigePermissaoECsrf() throws Exception {
        UUID id = UUID.randomUUID();
        var autorizado = authentication(tokenFor(
                PapelUsuario.MODERADOR,
                "SUPORTE_ATENDER"));
        String body = """
                {"status":"EM_ANALISE","providencia":"Triagem iniciada"}
                """;

        mockMvc.perform(patch("/api/admin/sugestoes/{id}/status", id)
                        .with(autorizado)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/sugestoes/{id}/status", id)
                        .with(authentication(tokenFor(
                                PapelUsuario.MODERADOR,
                                "SUPORTE_ATENDER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/sugestoes/{id}/status", id)
                        .with(authentication(tokenFor(PapelUsuario.MODERADOR)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken tokenFor(
            PapelUsuario papel,
            String... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
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
        return new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                authorities);
    }
}
