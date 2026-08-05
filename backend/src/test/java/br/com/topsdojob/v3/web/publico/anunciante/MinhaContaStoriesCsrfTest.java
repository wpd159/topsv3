package br.com.topsdojob.v3.web.publico.anunciante;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.anunciante.MeusStoriesConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesDireitoService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesPublicacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.StoryEncerramentoService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = MinhaContaStoriesController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class MinhaContaStoriesCsrfTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private MeusStoriesConsultaService consultaService;

  @MockBean
  private MinhaContaStoriesPublicacaoService publicacaoService;

  @MockBean
  private MinhaContaStoriesDireitoService direitoService;

  @MockBean
  private StoryEncerramentoService encerramentoService;

  @Test
  void mutacoesSemCsrfSaoRecusadas() throws Exception {
    MockMultipartFile arquivo = new MockMultipartFile(
        "arquivo", "story-qa.jpg", "image/jpeg", new byte[] {1});
    mockMvc.perform(multipart("/api/public/minha-conta/stories")
            .file(arquivo)
            .part(new MockPart(
                "modoConteudo", "MIDIA_UPLOAD".getBytes(StandardCharsets.UTF_8)))
            .header("Idempotency-Key", "story-sem-csrf"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/public/minha-conta/stories/ativacoes")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "ativacao-sem-csrf")
            .content("{}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/public/minha-conta/stories/{id}", UUID.randomUUID()))
        .andExpect(status().isForbidden());
  }

  @Test
  void uploadCanonicoComCsrfChegaAoController() throws Exception {
    MockMultipartFile arquivo = new MockMultipartFile(
        "arquivo", "story-qa.jpg", "image/jpeg", new byte[] {1});
    mockMvc.perform(multipart("/api/public/minha-conta/stories")
            .file(arquivo)
            .part(new MockPart(
                "modoConteudo", "MIDIA_UPLOAD".getBytes(StandardCharsets.UTF_8)))
            .with(csrf())
            .header("Idempotency-Key", "story-com-csrf"))
        .andExpect(status().isOk());
  }
}
