package br.com.topsdojob.v3.web.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioCicloVidaService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioStoryService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhasMidiasService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class MeusAnunciosStoryControllerTest {

  private MeuAnuncioStoryService storyService;
  private MeusAnunciosController controller;
  private Authentication authentication;

  @BeforeEach
  void setUp() {
    storyService = mock(MeuAnuncioStoryService.class);
    authentication = mock(Authentication.class);
    controller = new MeusAnunciosController(
        mock(MeusAnunciosConsultaService.class),
        mock(MeuAnuncioAtualizacaoService.class),
        mock(MeuAnuncioCicloVidaService.class),
        mock(MinhasMidiasService.class),
        storyService);
  }

  @Test
  void encaminhaSomenteContratoCanonicoSemIdentidadeDoCliente() {
    MockMultipartHttpServletRequest request = multipart("modoConteudo", "ANUNCIO");
    MeuAnuncioStoryDto esperado = new MeuAnuncioStoryDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "ANUNCIO",
        null,
        "PUBLICADO",
        OffsetDateTime.parse("2026-08-01T12:00:00Z"),
        OffsetDateTime.parse("2026-08-02T12:00:00Z"),
        null);
    when(storyService.publicar(
        eq("anuncio-qa"), eq("ANUNCIO"), eq(null), eq("intent-1"), eq(authentication), any()))
        .thenReturn(esperado);

    var response = controller.publicarStory(
        "anuncio-qa", "ANUNCIO", null, "intent-1", authentication, request);

    org.assertj.core.api.Assertions.assertThat(response).isSameAs(esperado);
  }

  @Test
  void rejeitaUsuarioMidiaUrlObjectKeyEBucketForaDoContrato() {
    for (String campo : new String[] {"usuarioId", "mediaId", "url", "objectKey", "bucket"}) {
      MockMultipartHttpServletRequest request = multipart("modoConteudo", "MIDIA_UPLOAD");
      request.addPart(new MockPart(campo, "valor-proibido".getBytes(StandardCharsets.UTF_8)));

      assertStatus(() -> controller.publicarStory(
          "anuncio-qa", "MIDIA_UPLOAD", null, "intent-2", authentication, request),
          HttpStatus.BAD_REQUEST);
    }
    verify(storyService, never()).publicar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejeitaParametrosDeQueryComoCanalParalelo() {
    MockMultipartHttpServletRequest request = multipart("modoConteudo", "ANUNCIO");
    request.setQueryString("mediaId=nao-permitido");

    assertStatus(() -> controller.publicarStory(
        "anuncio-qa", "ANUNCIO", null, "intent-3", authentication, request),
        HttpStatus.BAD_REQUEST);
    verify(storyService, never()).publicar(any(), any(), any(), any(), any(), any());
  }

  private MockMultipartHttpServletRequest multipart(String nome, String valor) {
    MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
    request.addPart(new MockPart(nome, valor.getBytes(StandardCharsets.UTF_8)));
    return request;
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(status));
  }
}
