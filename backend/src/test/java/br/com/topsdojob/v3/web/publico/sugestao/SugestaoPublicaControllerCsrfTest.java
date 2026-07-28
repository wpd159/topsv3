package br.com.topsdojob.v3.web.publico.sugestao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Criacao;
import br.com.topsdojob.v3.application.sugestao.SugestaoService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = SugestaoPublicaController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class SugestaoPublicaControllerCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SugestaoService service;

    @Test
    void envioExigeCsrf() throws Exception {
        when(service.criar(any(), anyString(), any(), anyString()))
                .thenReturn(new Criacao(
                        UUID.randomUUID(),
                        "#12345678",
                        "PENDENTE",
                        "Nova",
                        OffsetDateTime.parse("2026-07-28T12:00:00Z"),
                        false));
        String body = """
                {"tipo":"FEATURE","titulo":"Melhoria no painel","descricao":"Descricao suficiente"}
                """;

        mockMvc.perform(post("/api/public/sugestoes")
                        .with(csrf())
                        .header("Idempotency-Key", "sugestao-test-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/sugestoes")
                        .header("Idempotency-Key", "sugestao-test-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
