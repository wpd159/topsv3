package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeuAnuncioStoryConsultaServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-04T14:00:00Z");
  private static final UUID ANUNCIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID ANUNCIO_B_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
  private static final UUID USUARIO_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID MIDIA_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID MIDIA_B_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
  private static final UUID ARQUIVO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
  private static final UUID ARQUIVO_B_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");

  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private MeuAnuncioStoryConsultaService service;

  @BeforeEach
  void setUp() {
    service = new MeuAnuncioStoryConsultaService(
        storyRepository,
        midiaRepository,
        arquivoRepository,
        Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC));
  }

  @Test
  void midiaUploadValidadoEhDisponivelSemReferenciaInternaNoDto() {
    StoryAnuncioEntity story = story(MIDIA_ID, ModoConteudoStory.MIDIA_UPLOAD, AGORA.plusHours(24));
    AnuncioMidiaEntity midia = AnuncioMidiaEntity.criarStoryUploadValidado(
        MIDIA_ID, ANUNCIO_ID, ARQUIVO_ID, 0, AGORA.minusMinutes(1));
    ArquivoMidiaEntity arquivo = arquivo(StatusArquivoMidia.VALIDADO);
    when(midiaRepository.findById(MIDIA_ID)).thenReturn(Optional.of(midia));
    when(arquivoRepository.findById(ARQUIVO_ID)).thenReturn(Optional.of(arquivo));

    MeuAnuncioStoryDto dto = service.consultar(story);

    assertThat(dto.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
    assertThat(dto.tipoMidia()).isEqualTo("FOTO");
    assertThat(dto.estadoMidia()).isEqualTo("DISPONIVEL");
    assertThat(dto.getClass().getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("objectKey", "chaveObjeto", "bucket", "urlPrivada");
  }

  @Test
  void midiaUploadPendenteEhIndisponivelMesmoComReferenciaPresente() {
    StoryAnuncioEntity story = story(MIDIA_ID, ModoConteudoStory.MIDIA_UPLOAD, AGORA.plusHours(24));
    AnuncioMidiaEntity midia = AnuncioMidiaEntity.criarStoryUploadValidado(
        MIDIA_ID, ANUNCIO_ID, ARQUIVO_ID, 0, AGORA.minusMinutes(1));
    when(midiaRepository.findById(MIDIA_ID)).thenReturn(Optional.of(midia));
    when(arquivoRepository.findById(ARQUIVO_ID))
        .thenReturn(Optional.of(arquivo(StatusArquivoMidia.PENDENTE)));

    MeuAnuncioStoryDto dto = service.consultar(story);

    assertThat(dto.estadoMidia()).isEqualTo("INDISPONIVEL");
  }

  @Test
  void modoAnuncioNaoExigeMidiaExclusiva() {
    MeuAnuncioStoryDto dto = service.consultar(
        story(null, ModoConteudoStory.ANUNCIO, AGORA.plusHours(24)));

    assertThat(dto.modoConteudo()).isEqualTo("ANUNCIO");
    assertThat(dto.tipoMidia()).isNull();
    assertThat(dto.estadoMidia()).isNull();
    verifyNoInteractions(midiaRepository, arquivoRepository);
  }

  @Test
  void storyExpiradoNaoEhConfundidoComFalhaDeMidiaAtiva() {
    StoryAnuncioEntity expirado = story(MIDIA_ID, ModoConteudoStory.MIDIA_UPLOAD, AGORA.minusSeconds(1));
    when(storyRepository.findByAnuncioIds(List.of(ANUNCIO_ID))).thenReturn(List.of(expirado));

    assertThat(service.consultarAtivos(List.of(ANUNCIO_ID))).isEmpty();
    verifyNoInteractions(midiaRepository, arquivoRepository);
  }

  @Test
  void mesmoUsuarioMantemStoryAtivoEmCadaAnuncioNaConsulta() {
    StoryAnuncioEntity storyA = story(
        ANUNCIO_ID, MIDIA_ID, ModoConteudoStory.MIDIA_UPLOAD, AGORA.plusHours(24));
    StoryAnuncioEntity storyB = story(
        ANUNCIO_B_ID, MIDIA_B_ID, ModoConteudoStory.MIDIA_UPLOAD, AGORA.plusHours(24));
    AnuncioMidiaEntity midiaA = AnuncioMidiaEntity.criarStoryUploadValidado(
        MIDIA_ID, ANUNCIO_ID, ARQUIVO_ID, 0, AGORA.minusMinutes(1));
    AnuncioMidiaEntity midiaB = AnuncioMidiaEntity.criarStoryUploadValidado(
        MIDIA_B_ID, ANUNCIO_B_ID, ARQUIVO_B_ID, 0, AGORA.minusMinutes(1));

    when(storyRepository.findByAnuncioIds(List.of(ANUNCIO_ID, ANUNCIO_B_ID)))
        .thenReturn(List.of(storyA, storyB));
    when(midiaRepository.findByIdIn(any())).thenReturn(List.of(midiaA, midiaB));
    when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(
        arquivo(ARQUIVO_ID, StatusArquivoMidia.VALIDADO),
        arquivo(ARQUIVO_B_ID, StatusArquivoMidia.VALIDADO)));

    var ativos = service.consultarAtivos(List.of(ANUNCIO_ID, ANUNCIO_B_ID));

    assertThat(ativos).containsOnlyKeys(ANUNCIO_ID, ANUNCIO_B_ID);
    assertThat(ativos.get(ANUNCIO_ID).storyId()).isEqualTo(storyA.getId());
    assertThat(ativos.get(ANUNCIO_B_ID).storyId()).isEqualTo(storyB.getId());
    assertThat(ativos.values())
        .extracting(MeuAnuncioStoryDto::estadoMidia)
        .containsOnly("DISPONIVEL");
  }

  private StoryAnuncioEntity story(
      UUID midiaId,
      ModoConteudoStory modo,
      OffsetDateTime fimEm) {
    return story(ANUNCIO_ID, midiaId, modo, fimEm);
  }

  private StoryAnuncioEntity story(
      UUID anuncioId,
      UUID midiaId,
      ModoConteudoStory modo,
      OffsetDateTime fimEm) {
    return StoryAnuncioEntity.criarAutogestao(
        UUID.randomUUID(), anuncioId, midiaId, modo, UUID.randomUUID(),
        "story-owner-dto", "a".repeat(64), AGORA.minusMinutes(1), fimEm, USUARIO_ID);
  }

  private ArquivoMidiaEntity arquivo(StatusArquivoMidia status) {
    return arquivo(ARQUIVO_ID, status);
  }

  private ArquivoMidiaEntity arquivo(UUID arquivoId, StatusArquivoMidia status) {
    return ArquivoMidiaEntity.criarFixtureHomologacao(
        arquivoId, "stories/arquivo-sintetico.jpg", "image/jpeg", status, AGORA.minusMinutes(2));
  }
}
