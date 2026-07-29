package br.com.topsdojob.v3.web.publico.wizard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncResponse;
import br.com.topsdojob.v3.application.wizard.WizardProgressSyncService;
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
    controllers = WizardProgressController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class WizardProgressControllerCsrfTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private WizardProgressSyncService service;

  @Test
  void sincronizacaoSemCsrfEhRecusada() throws Exception {
    mockMvc.perform(post("/api/public/wizard-progress/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void sincronizacaoComCsrfChegaAoServico() throws Exception {
    when(service.sincronizar(any(), any())).thenReturn(new SyncResponse(
        UUID.randomUUID(),
        "EM_PREENCHIMENTO",
        "PERFIL",
        OffsetDateTime.parse("2026-07-29T14:00:00Z")));

    mockMvc.perform(post("/api/public/wizard-progress/sync")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody()))
        .andExpect(status().isOk());
  }

  private String validBody() {
    return """
        {
          "sessionId": "wizard-create-123",
          "mode": "CREATE",
          "ultimoStep": "PERFIL",
          "status": "EM_PREENCHIMENTO"
        }
        """;
  }
}
