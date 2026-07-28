package br.com.topsdojob.v3.web.publico.denuncia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.CriarDenunciaResponse;
import br.com.topsdojob.v3.application.denuncia.DenunciaPublicaService;
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
        controllers = DenunciaPublicaController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class DenunciaPublicaControllerCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DenunciaPublicaService service;

    @Test
    void visitantePodeDenunciarComCsrfMasSemCsrfRecebe403() throws Exception {
        when(service.criar(any(), anyString(), any(), any(), anyString()))
                .thenReturn(new CriarDenunciaResponse(
                        UUID.randomUUID(),
                        "#12345678",
                        "PENDENTE",
                        OffsetDateTime.parse("2026-07-28T12:00:00Z"),
                        false));
        String body = """
                {"anuncioId":"10000000-0000-4000-8000-000000000001","motivo":"SPAM"}
                """;

        mockMvc.perform(post("/api/public/denuncias/abrir")
                        .with(csrf())
                        .header("Idempotency-Key", "denuncia-test-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/denuncias/abrir")
                        .header("Idempotency-Key", "denuncia-test-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
