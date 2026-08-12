package br.com.topsdojob.v3.web.publico.pagamento;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.pagamento.EfiWebhookAutenticacaoException;
import br.com.topsdojob.v3.application.publico.pagamento.EfiWebhookService;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiWebhookResultadoDto;
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
        controllers = EfiWebhookController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class EfiWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EfiWebhookService service;

    @Test
    void somenteRotaPixEhExecutavelESemCsrf() throws Exception {
        when(service.receber(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new EfiWebhookResultadoDto(1, 1, 0, 0));

        mockMvc.perform(post("/api/public/webhooks/efi/pix")
                        .queryParam("hmac", "segredo-sintetico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pix\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void hmacAusenteRetorna403NeutroNoStoreComRequestId() throws Exception {
        String requestId = "request-webhook-missing-hmac-01";
        when(service.receber(isNull(), anyString(), anyString(), anyString()))
                .thenThrow(new EfiWebhookAutenticacaoException());

        mockMvc.perform(post("/api/public/webhooks/efi/pix")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pix\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.message")
                        .value("N\u00e3o foi poss\u00edvel validar a notifica\u00e7\u00e3o."))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(content().string(not(containsString("HMAC"))))
                .andExpect(content().string(not(containsString("segredo"))));
    }

    @Test
    void hmacInvalidoRetorna403SemExporValorRecebido() throws Exception {
        String requestId = "request-webhook-invalid-hmac-01";
        String hmacInvalido = "invalid-webhook-test-value";
        when(service.receber(eq(hmacInvalido), anyString(), anyString(), anyString()))
                .thenThrow(new EfiWebhookAutenticacaoException());

        mockMvc.perform(post("/api/public/webhooks/efi/pix")
                        .queryParam("hmac", hmacInvalido)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pix\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.message")
                        .value("N\u00e3o foi poss\u00edvel validar a notifica\u00e7\u00e3o."))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(content().string(not(containsString(hmacInvalido))))
                .andExpect(content().string(not(containsString("HMAC"))));
    }

    @Test
    void aliasAntigoNaoExisteNemRedireciona() throws Exception {
        mockMvc.perform(post("/api/public/webhooks/efi")
                        .with(csrf())
                        .queryParam("hmac", "segredo-sintetico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pix\":[]}"))
                .andExpect(status().isNotFound());
    }
}
