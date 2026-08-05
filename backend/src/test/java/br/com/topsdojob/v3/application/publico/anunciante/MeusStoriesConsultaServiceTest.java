package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuStoryGerenciadoDto;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeusStoriesConsultaServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-04T21:00:00Z");
  private static final UUID USUARIO_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");

  private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private MeusStoriesConsultaService service;

  @BeforeEach
  void setUp() {
    service = new MeusStoriesConsultaService(
        usuarioService,
        storyRepository,
        midiaRepository,
        arquivoRepository,
        anuncioRepository,
        Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC));
  }

  @Test
  void listaMidiasIndependentesSemAnuncioEmConsultasEmLote() {
    UUID arquivoAId = UUID.randomUUID();
    UUID arquivoBId = UUID.randomUUID();
    StoryAnuncioEntity storyA = direta(arquivoAId, AGORA.plusHours(23), "story-a");
    StoryAnuncioEntity storyB = direta(arquivoBId, AGORA.plusHours(22), "story-b");
    ArquivoMidiaEntity arquivoA = arquivo(arquivoAId, StatusArquivoMidia.VALIDADO);
    ArquivoMidiaEntity arquivoB = arquivo(arquivoBId, StatusArquivoMidia.VALIDADO);
    when(midiaRepository.findByIdIn(any())).thenReturn(List.of());
    when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivoA, arquivoB));
    when(anuncioRepository.findAllById(any())).thenReturn(List.of());

    List<MeuStoryGerenciadoDto> resposta = service.mapear(List.of(storyA, storyB));

    assertThat(resposta).hasSize(2).allSatisfy(item -> {
      assertThat(item.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
      assertThat(item.anuncioSlug()).isNull();
      assertThat(item.anuncioTitulo()).isNull();
      assertThat(item.estadoMidia()).isEqualTo("DISPONIVEL");
      assertThat(item.podeExcluir()).isTrue();
      assertThat(item.podeDescartar()).isFalse();
    });
    verify(midiaRepository, times(1)).findByIdIn(any());
    verify(arquivoRepository, times(1)).findByIdIn(any());
    verify(anuncioRepository, times(1)).findAllById(any());
  }

  @Test
  void storyLegadoPublicadoComArquivoPendenteApareceComoFalhaDescartavel() {
    UUID anuncioId = UUID.randomUUID();
    UUID vinculoId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    StoryAnuncioEntity legado = StoryAnuncioEntity.criarFixtureHomologacao(
        UUID.randomUUID(),
        vinculoId,
        AGORA.minusHours(2),
        AGORA.plusHours(22),
        0,
        USUARIO_ID,
        AGORA.minusHours(2));
    AnuncioMidiaEntity vinculo = AnuncioMidiaEntity.criarStoryUploadValidado(
        vinculoId, anuncioId, arquivoId, 0, AGORA.minusHours(2));
    ArquivoMidiaEntity arquivo = arquivo(arquivoId, StatusArquivoMidia.PENDENTE);
    when(midiaRepository.findByIdIn(any())).thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
    when(anuncioRepository.findAllById(any())).thenReturn(List.of());

    MeuStoryGerenciadoDto resposta = service.mapear(List.of(legado)).get(0);

    assertThat(resposta.status()).isEqualTo("PUBLICADO");
    assertThat(resposta.estadoMidia()).isEqualTo("FALHA_PUBLICACAO");
    assertThat(resposta.falhaTecnica()).isTrue();
    assertThat(resposta.podeExcluir()).isFalse();
    assertThat(resposta.podeDescartar()).isTrue();
    assertThat(resposta.anuncioSlug()).isNull();
    assertThat(resposta.toString())
        .doesNotContain("hml/")
        .doesNotContain("bucket")
        .doesNotContain("objectKey");
  }

  @Test
  void vigenciaEncerradaEhExibidaComoExpiradaSemAlterarRegistro() {
    UUID arquivoId = UUID.randomUUID();
    StoryAnuncioEntity expirado = direta(arquivoId, AGORA.minusSeconds(1), "story-expirado");
    when(midiaRepository.findByIdIn(any())).thenReturn(List.of());
    when(arquivoRepository.findByIdIn(any()))
        .thenReturn(List.of(arquivo(arquivoId, StatusArquivoMidia.VALIDADO)));
    when(anuncioRepository.findAllById(any())).thenReturn(List.of());

    MeuStoryGerenciadoDto resposta = service.mapear(List.of(expirado)).get(0);

    assertThat(resposta.status()).isEqualTo("EXPIRADO");
    assertThat(expirado.getStatus().name()).isEqualTo("PUBLICADO");
  }

  private StoryAnuncioEntity direta(UUID arquivoId, OffsetDateTime fim, String chave) {
    return StoryAnuncioEntity.criarMidiaUpload(
        UUID.randomUUID(),
        arquivoId,
        UUID.randomUUID(),
        chave,
        "d".repeat(64),
        AGORA.minusHours(1),
        fim,
        USUARIO_ID);
  }

  private ArquivoMidiaEntity arquivo(UUID id, StatusArquivoMidia status) {
    return ArquivoMidiaEntity.criarFixtureHomologacao(
        id,
        "hml/midias-pendentes/stories/arquivo-qa.mp4",
        "video/mp4",
        status,
        AGORA.minusHours(2));
  }
}
