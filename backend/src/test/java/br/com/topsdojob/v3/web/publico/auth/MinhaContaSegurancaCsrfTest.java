package br.com.topsdojob.v3.web.publico.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.auth.MinhaContaSegurancaService;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAccountActionDto;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = MinhaContaSegurancaController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class MinhaContaSegurancaCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MinhaContaSegurancaService service;

    @Test
    void mutacoesSemCsrfSaoRecusadas() throws Exception {
        mockMvc.perform(post("/api/public/minha-conta/seguranca/senha")
                        .with(authentication(authenticated()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"EXEMPLO_NAO_REAL","novaSenha":"EXEMPLO_NAO_REAL","confirmarSenha":"EXEMPLO_NAO_REAL"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/public/minha-conta/seguranca/exclusao")
                        .with(authentication(authenticated()))
                        .header("Idempotency-Key", "delete-account-csrf-4501")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"EXEMPLO_NAO_REAL","confirmacao":"EXCLUIR MINHA CONTA","cienteConsequencias":true}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void alteracaoComCsrfChegaAoServicoCanonico() throws Exception {
        when(service.alterarSenha(any(), any(), anyString()))
                .thenReturn(new PublicAccountActionDto("Senha alterada."));

        mockMvc.perform(post("/api/public/minha-conta/seguranca/senha")
                        .with(authentication(authenticated()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"EXEMPLO_NAO_REAL","novaSenha":"EXEMPLO_NAO_REAL","confirmarSenha":"EXEMPLO_NAO_REAL"}
                                """))
                .andExpect(status().isOk());

        verify(service).alterarSenha(any(), any(), anyString());
    }

    private UsernamePasswordAuthenticationToken authenticated() {
        return new UsernamePasswordAuthenticationToken(
                new PublicUserPrincipal(
                        UUID.fromString("00000000-0000-4000-8000-000000004501"),
                        "qa-preprod",
                        "qa@example.invalid"),
                null,
                List.of());
    }
}
