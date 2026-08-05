package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
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
  private static final UUID ARQUIVO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

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
  void midiaUploadDiretaValidadaEhDisponivelSemReferenciaInternaNoDto() {
    StoryAnuncioEntity story = storyMidia(ARQUIVO_ID, AGORA.plusHours(24));
    ArquivoMidiaEntity arquivo = arquivo(StatusArquivoMidia.VALIDADO);
    when(arquivoRepository.findById(ARQUIVO_ID)).thenReturn(Optional.of(arquivo));

    MinhaContaStoryDto dto = service.consultar(story);

    assertThat(dto.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
    assertThat(dto.tipoMidia()).isEqualTo("FOTO");
    assertThat(dto.estadoMidia()).isEqualTo("DISPONIVEL");
    assertThat(dto.anuncioId()).isNull();
    assertThat(story.getAnuncioMidiaId()).isNull();
    assertThat(dto.getClass().getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("objectKey", "chaveObjeto", "bucket", "urlPrivada");
    verifyNoInteractions(midiaRepository);
  }

  @Test
  void midiaUploadDiretaPendenteEhIndisponivel() {
    StoryAnuncioEntity story = storyMidia(ARQUIVO_ID, AGORA.plusHours(24));
    when(arquivoRepository.findById(ARQUIVO_ID))
        .thenReturn(Optional.of(arquivo(StatusArquivoMidia.PENDENTE)));

    MinhaContaStoryDto dto = service.consultar(story);

    assertThat(dto.estadoMidia()).isEqualTo("INDISPONIVEL");
    verifyNoInteractions(midiaRepository);
  }

  @Test
  void modoAnuncioNaoExigeMidiaExclusiva() {
    MinhaContaStoryDto dto = service.consultar(
        storyAnuncio(ANUNCIO_ID, AGORA.plusHours(24)));

    assertThat(dto.modoConteudo()).isEqualTo("ANUNCIO");
    assertThat(dto.tipoMidia()).isNull();
    assertThat(dto.estadoMidia()).isNull();
    verifyNoInteractions(midiaRepository, arquivoRepository);
  }

  @Test
  void storyExpiradoNaoEhConfundidoComFalhaDeMidiaAtiva() {
    StoryAnuncioEntity expirado = storyMidia(ARQUIVO_ID, AGORA.minusSeconds(1));
    when(storyRepository.findByAnuncioIds(List.of(ANUNCIO_ID))).thenReturn(List.of(expirado));

    assertThat(service.consultarAtivos(List.of(ANUNCIO_ID))).isEmpty();
    verifyNoInteractions(midiaRepository, arquivoRepository);
  }

  @Test
  void mesmoUsuarioMantemStoryAnuncioAtivoEmCadaAnuncioNaConsulta() {
    StoryAnuncioEntity storyA = storyAnuncio(ANUNCIO_ID, AGORA.plusHours(24));
    StoryAnuncioEntity storyB = storyAnuncio(ANUNCIO_B_ID, AGORA.plusHours(24));

    when(storyRepository.findByAnuncioIds(List.of(ANUNCIO_ID, ANUNCIO_B_ID)))
        .thenReturn(List.of(storyA, storyB));

    var ativos = service.consultarAtivos(List.of(ANUNCIO_ID, ANUNCIO_B_ID));

    assertThat(ativos).containsOnlyKeys(ANUNCIO_ID, ANUNCIO_B_ID);
    assertThat(ativos.get(ANUNCIO_ID).storyId()).isEqualTo(storyA.getId());
    assertThat(ativos.get(ANUNCIO_B_ID).storyId()).isEqualTo(storyB.getId());
    assertThat(ativos.values())
        .extracting(MinhaContaStoryDto::estadoMidia)
        .containsOnlyNulls();
    verify(midiaRepository).findByIdIn(List.of());
    verify(arquivoRepository).findByIdIn(List.of());
  }

  private StoryAnuncioEntity storyAnuncio(UUID anuncioId, OffsetDateTime fimEm) {
    return StoryAnuncioEntity.criarAnuncio(
        UUID.randomUUID(),
        anuncioId,
        UUID.randomUUID(),
        "story-anuncio-dto-" + anuncioId,
        "a".repeat(64),
        AGORA.minusMinutes(1),
        fimEm,
        USUARIO_ID);
  }

  private StoryAnuncioEntity storyMidia(UUID arquivoId, OffsetDateTime fimEm) {
    return StoryAnuncioEntity.criarMidiaUpload(
        UUID.randomUUID(),
        arquivoId,
        UUID.randomUUID(),
        "story-midia-dto-" + arquivoId,
        "b".repeat(64),
        AGORA.minusMinutes(1),
        fimEm,
        USUARIO_ID);
  }

  private ArquivoMidiaEntity arquivo(StatusArquivoMidia status) {
    return ArquivoMidiaEntity.criarFixtureHomologacao(
        ARQUIVO_ID,
        "stories/arquivo-sintetico.jpg",
        "image/jpeg",
        status,
        AGORA.minusMinutes(2));
  }
}
