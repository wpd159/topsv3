package br.com.topsdojob.v3.web.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusStoriesConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesDireitoService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesPublicacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.StoryEncerramentoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeusStoriesPaginaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.StoryEncerramentoDto;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class MinhaContaStoriesControllerTest {

  private MeusStoriesConsultaService consultaService;
  private MinhaContaStoriesPublicacaoService publicacaoService;
  private StoryEncerramentoService encerramentoService;
  private MinhaContaStoriesController controller;
  private Authentication authentication;

  @BeforeEach
  void setUp() {
    consultaService = mock(MeusStoriesConsultaService.class);
    publicacaoService = mock(MinhaContaStoriesPublicacaoService.class);
    encerramentoService = mock(StoryEncerramentoService.class);
    authentication = mock(Authentication.class);
    controller = new MinhaContaStoriesController(
        consultaService,
        publicacaoService,
        mock(MinhaContaStoriesDireitoService.class),
        encerramentoService);
  }

  @Test
  void listaDaContaUsaSessaoEPossuiNoStore() {
    MeusStoriesPaginaDto esperado = new MeusStoriesPaginaDto(List.of(), 0, 20, 0, 0);
    when(consultaService.listar(0, 20, authentication)).thenReturn(esperado);

    var response = controller.listar(0, 20, authentication);

    assertThat(response.getBody()).isSameAs(esperado);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(consultaService).listar(0, 20, authentication);
  }

  @Test
  void publicacaoDiretaAceitaModoEUmArquivoSemIdentificadoresDoCliente() {
    MockMultipartFile arquivo = new MockMultipartFile(
        "arquivo", "story-qa.jpg", "image/jpeg", new byte[] {1, 2, 3});
    MockMultipartHttpServletRequest request = requestComPartes("modoConteudo", "arquivo");
    MinhaContaStoryDto esperado = new MinhaContaStoryDto(
        UUID.randomUUID(),
        null,
        "MIDIA_UPLOAD",
        "FOTO",
        "PUBLICADO",
        OffsetDateTime.parse("2026-08-04T12:00:00Z"),
        OffsetDateTime.parse("2026-08-05T12:00:00Z"),
        "DISPONIVEL");
    when(publicacaoService.publicar(
        eq("MIDIA_UPLOAD"),
        isNull(),
        eq(List.of(arquivo)),
        eq("intent-direto-1"),
        eq(authentication),
        anyString()))
        .thenReturn(esperado);

    var response = controller.publicar(
        "MIDIA_UPLOAD", null, List.of(arquivo), "intent-direto-1", authentication, request);

    assertThat(response.getBody()).isSameAs(esperado);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(publicacaoService).publicar(
        eq("MIDIA_UPLOAD"),
        isNull(),
        eq(List.of(arquivo)),
        eq("intent-direto-1"),
        eq(authentication),
        anyString());
  }

  @Test
  void modoAnuncioEncaminhaIdentificadorSemArquivo() {
    UUID anuncioId = UUID.randomUUID();
    MockMultipartHttpServletRequest request = requestComPartes("modoConteudo", "anuncioId");

    controller.publicar(
        "ANUNCIO", anuncioId.toString(), null, "intent-anuncio-1", authentication, request);

    verify(publicacaoService).publicar(
        eq("ANUNCIO"),
        eq(anuncioId),
        isNull(),
        eq("intent-anuncio-1"),
        eq(authentication),
        anyString());
  }

  @Test
  void rejeitaParteDesconhecidaOuCanalPorQueryAntesDoServico() {
    MockMultipartFile arquivo = new MockMultipartFile(
        "arquivo", "story-qa.jpg", "image/jpeg", new byte[] {1});
    MockMultipartHttpServletRequest parteDesconhecida = requestComPartes(
        "modoConteudo", "arquivo", "usuarioId");

    assertBadRequest(() -> controller.publicar(
        "MIDIA_UPLOAD", null, List.of(arquivo), "intent-direto-2", authentication,
        parteDesconhecida));

    MockMultipartHttpServletRequest query = requestComPartes("modoConteudo", "arquivo");
    query.setQueryString("objectKey=proibido");
    assertBadRequest(() -> controller.publicar(
        "MIDIA_UPLOAD", null, List.of(arquivo), "intent-direto-3", authentication, query));
    verifyNoInteractions(publicacaoService);
  }

  @Test
  void exclusaoLogicaEncaminhaStoryESessaoComNoStore() {
    UUID storyId = UUID.randomUUID();
    StoryEncerramentoDto esperado = new StoryEncerramentoDto(
        storyId,
        "REMOVIDO",
        OffsetDateTime.parse("2026-08-04T13:00:00Z"),
        "USUARIO",
        "EXCLUSAO_VOLUNTARIA",
        false,
        false);
    MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
    when(encerramentoService.encerrarProprio(eq(storyId), eq(authentication), anyString()))
        .thenReturn(esperado);

    var response = controller.encerrar(storyId, authentication, request);

    assertThat(response.getBody()).isSameAs(esperado);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(encerramentoService).encerrarProprio(eq(storyId), eq(authentication), anyString());
  }

  private MockMultipartHttpServletRequest requestComPartes(String... nomes) {
    MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
    for (String nome : nomes) {
      request.addPart(new MockPart(nome, nome.getBytes(StandardCharsets.UTF_8)));
    }
    return request;
  }

  private void assertBadRequest(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
