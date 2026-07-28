package br.com.topsdojob.v3.web.publico.suporte;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.suporte.SuporteDtos.TicketDetalhe;
import br.com.topsdojob.v3.application.suporte.SuporteTicketService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = SuporteTicketController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class SuporteTicketControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SuporteTicketService service;

    @Test
    void aberturaExigeCsrf() throws Exception {
        mockMvc.perform(post("/api/public/suporte/tickets")
                        .header("Idempotency-Key", "suporte-public-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assunto": "Assunto QA",
                                  "categoria": "OUTROS",
                                  "descricao": "Descricao segura para o teste"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void aberturaComCsrfChegaAoContratoCanonico() throws Exception {
        when(service.criar(any(), any(), any(), any())).thenReturn((TicketDetalhe) null);

        mockMvc.perform(post("/api/public/suporte/tickets")
                        .with(csrf())
                        .header("Idempotency-Key", "suporte-public-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assunto": "Assunto QA",
                                  "categoria": "OUTROS",
                                  "descricao": "Descricao segura para o teste"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
