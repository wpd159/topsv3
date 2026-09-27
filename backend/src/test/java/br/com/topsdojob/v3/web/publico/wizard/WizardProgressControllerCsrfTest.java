package br.com.topsdojob.v3.web.publico.wizard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncResponse;
import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.AnuncioResponse;
import br.com.topsdojob.v3.application.wizard.WizardProgressSyncService;
import br.com.topsdojob.v3.application.publico.service.SolicitarAnuncioPublicoService;
import br.com.topsdojob.v3.web.publico.PublicAnunciarController;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {WizardProgressController.class, PublicAnunciarController.class},
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class WizardProgressControllerCsrfTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private WizardProgressSyncService service;

  @MockBean
  private SolicitarAnuncioPublicoService criacao;

  @Test
  void criacaoEncaminhaHeaderDeSessaoExatoComCsrf() throws Exception {
    mockMvc.perform(post("/api/public/anunciar").with(csrf())
            .header("X-Wizard-Session-Id", "wizard-create-123")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isCreated());
    verify(criacao).solicitar(any(), any(), eq("wizard-create-123"));
    verify(criacao, never()).solicitar(any(), any());
  }

  @Test
  void criacaoSemHeaderPreservaSobrecargaLegada() throws Exception {
    mockMvc.perform(post("/api/public/anunciar").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isCreated());
    verify(criacao).solicitar(any(), any());
    verify(criacao, never()).solicitar(any(), any(), any());
  }

  @Test
  void headerDeSessaoNaoDispensaCsrfNaCriacao() throws Exception {
    mockMvc.perform(post("/api/public/anunciar")
            .header("X-Wizard-Session-Id", "wizard-create-123")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(criacao);
  }

  @Test
  void recuperacaoRetornaSomenteIdentidadeEEstadoSemCache() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.recuperarAnuncio(eq("wizard-create-123"), any())).thenReturn(
        new AnuncioResponse(id, "anuncio-sintetico", "RASCUNHO", "NAO_ENVIADO"));
    mockMvc.perform(get("/api/public/wizard-progress/wizard-create-123/anuncio"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().json("""
            {"anuncioId":"%s","slug":"anuncio-sintetico","status":"RASCUNHO","statusModeracao":"NAO_ENVIADO"}
            """.formatted(id), true));
  }

  @Test
  void recuperacaoPropagaRecusaDeAutenticacaoMesmoNaRotaPublica() throws Exception {
    when(service.recuperarAnuncio(eq("wizard-create-123"), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica obrigatoria"));
    mockMvc.perform(get("/api/public/wizard-progress/wizard-create-123/anuncio"))
        .andExpect(status().isUnauthorized());
  }

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
