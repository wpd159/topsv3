package br.com.topsdojob.v3.web.publico.pagamento;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.pagamento.EfiPagamentoService;
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
        controllers = EfiPagamentoController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class EfiPagamentoControllerCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EfiPagamentoService service;

    @Test
    void criarCobrancaSemCsrfRetorna403() throws Exception {
        mockMvc.perform(post("/api/public/minha-conta/pagamentos/pix")
                        .header("Idempotency-Key", "pix-controller-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planoCreditoId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void criarCobrancaComCsrfChegaAoContrato() throws Exception {
        mockMvc.perform(post("/api/public/minha-conta/pagamentos/pix")
                        .with(csrf())
                        .header("Idempotency-Key", "pix-controller-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planoCreditoId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void conciliacaoSemCsrfRetorna403() throws Exception {
        mockMvc.perform(post(
                        "/api/public/minha-conta/pagamentos/00000000-0000-0000-0000-000000000001/conciliar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
