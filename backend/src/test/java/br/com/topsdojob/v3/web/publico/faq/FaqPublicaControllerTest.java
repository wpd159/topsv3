package br.com.topsdojob.v3.web.publico.faq;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.faq.FaqDtos.Item;
import br.com.topsdojob.v3.application.faq.FaqService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = FaqPublicaController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class FaqPublicaControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private FaqService service;

  @Test
  void listaPublicaEhAnonimaEContemSomenteContratoPublicado() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    when(service.listarPublicadas()).thenReturn(List.of(new Item(
        UUID.randomUUID(),
        "Como funciona a FAQ?",
        "Resposta publica segura.",
        "GERAL",
        "Geral",
        "PUBLICADO",
        0,
        now,
        now,
        0)));

    mockMvc.perform(get("/api/public/faqs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PUBLICADO"))
        .andExpect(jsonPath("$[0].pergunta").value("Como funciona a FAQ?"));
  }
}
