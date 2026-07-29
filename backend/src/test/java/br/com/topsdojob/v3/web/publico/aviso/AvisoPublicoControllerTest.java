package br.com.topsdojob.v3.web.publico.aviso;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.aviso.AvisoDtos.Pagina;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.PublicoItem;
import br.com.topsdojob.v3.application.aviso.AvisoService;
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
    controllers = AvisoPublicoController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AvisoPublicoControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AvisoService service;

  @Test
  void listaPublicaEhAnonimaESanitizadaPorLocal() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    when(service.listarPublicos(eq("SITE"), eq(0), eq(5)))
        .thenReturn(new Pagina<>(
            List.of(new PublicoItem(
                UUID.randomUUID(),
                "Aviso QA",
                "Mensagem publica segura.",
                "SITE",
                "DIARIO",
                true,
                now.minusHours(1),
                now.plusHours(1),
                now)),
            0, 5, 1, 1, true, true));

    mockMvc.perform(get("/api/public/avisos")
            .queryParam("localExibicao", "SITE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens[0].titulo").value("Aviso QA"))
        .andExpect(jsonPath("$.itens[0].criadoPorNome").doesNotExist());
  }
}
