package br.com.topsdojob.v3.application.publico.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdadePublicaSerializacaoTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void idadeNulaEhOmitidaDoPayloadPublico() throws Exception {
    AnuncioCardPublicoDto card = new AnuncioCardPublicoDto(
        UUID.randomUUID(),
        "perfil",
        "Perfil",
        "Resumo",
        null,
        "ACOMPANHANTE_FEMININA",
        null,
        null,
        List.of(),
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        List.of(),
        null,
        null,
        null);
    StoryFeedItemDto story = new StoryFeedItemDto(
        "story",
        "anuncio",
        "perfil",
        "usuario",
        "Perfil",
        null,
        true,
        "PREVIEW_PUBLICA",
        "/restritas-borradas/preview.jpg",
        "FOTO",
        null);

    assertThat(objectMapper.writeValueAsString(card)).doesNotContain("\"idade\"");
    assertThat(objectMapper.writeValueAsString(story)).doesNotContain("\"idade\"");
  }

  @Test
  void todosOsContratosPublicosDeIdadeOmitiremNulo() {
    assertAgeIsNonNullOnly(AnuncioCardPublicoDto.class);
    assertAgeIsNonNullOnly(AnuncioDetalhePublicoDto.class);
    assertAgeIsNonNullOnly(StoryFeedBundleDto.class);
    assertAgeIsNonNullOnly(StoryFeedItemDto.class);
    assertAgeIsNonNullOnly(StoryViewerPublicoDto.class);
  }

  private void assertAgeIsNonNullOnly(Class<?> type) {
    try {
      JsonInclude annotation = type.getMethod("idade").getAnnotation(JsonInclude.class);
      assertThat(annotation).as(type.getSimpleName()).isNotNull();
      assertThat(annotation.value())
          .as(type.getSimpleName())
          .isEqualTo(JsonInclude.Include.NON_NULL);
    } catch (NoSuchMethodException exception) {
      throw new AssertionError(type.getSimpleName() + " sem accessor de idade", exception);
    }
  }
}
