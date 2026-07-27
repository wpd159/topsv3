package br.com.topsdojob.v3.web.publico.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.chat.ChatInternoService;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatConversaDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatMensagemDto;
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
        controllers = ChatInternoController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class ChatInternoControllerCsrfTest {

    private static final UUID CONVERSA_ID =
            UUID.fromString("40000000-0000-4000-8000-000000000004");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatInternoService service;

    @Test
    void mutacoesSemCsrfSaoRecusadas() throws Exception {
        mockMvc.perform(post("/api/public/chat/conversas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"qa-b\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/public/chat/conversas/" + CONVERSA_ID + "/mensagens")
                        .header("Idempotency-Key", "chat-test-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corpo\":\"Mensagem QA\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/public/chat/conversas/" + CONVERSA_ID + "/leitura"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfValidoPermiteCriarConversaEEnviar() throws Exception {
        ChatConversaDto conversa = new ChatConversaDto(
                CONVERSA_ID,
                "qa-b",
                null,
                null,
                0);
        ChatMensagemDto mensagem = new ChatMensagemDto(
                UUID.fromString("50000000-0000-4000-8000-000000000005"),
                CONVERSA_ID,
                "qa-a",
                "Mensagem QA",
                OffsetDateTime.parse("2026-07-27T12:00:00Z"),
                null,
                true,
                false);
        when(service.iniciar(eq("qa-b"), any(), anyString())).thenReturn(conversa);
        when(service.enviar(
                eq(CONVERSA_ID),
                eq("Mensagem QA"),
                eq("chat-test-0001"),
                any(),
                anyString()))
                .thenReturn(mensagem);

        mockMvc.perform(post("/api/public/chat/conversas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"qa-b\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/chat/conversas/" + CONVERSA_ID + "/mensagens")
                        .with(csrf())
                        .header("Idempotency-Key", "chat-test-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corpo\":\"Mensagem QA\"}"))
                .andExpect(status().isOk());

        verify(service).iniciar(eq("qa-b"), any(), anyString());
        verify(service).enviar(
                eq(CONVERSA_ID),
                eq("Mensagem QA"),
                eq("chat-test-0001"),
                any(),
                anyString());
    }
}
