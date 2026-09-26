package br.com.topsdojob.v3.application.admin.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeStoryRegistroService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioEncerramentoConteudoServiceTest {

  @Mock private AnuncioRepository anuncios;
  @Mock private AnuncioStatusHistoricoRepository historicos;
  @Mock private AnuncioLocalizacaoRepository localizacoes;
  @Mock private DocumentoBuscaAnuncioRepository busca;
  @Mock private StoryAnuncioRepository stories;
  @Mock private StorySelecaoAdministrativaRepository storyAdministrativo;
  @Mock private ArquivoPublicidadeRegistroService arquivoPublicidade;
  @Mock private ArquivoPublicidadeStoryRegistroService arquivoStory;

  private AdminUsuarioEncerramentoConteudoService service;

  @BeforeEach
  void setUp() {
    service = new AdminUsuarioEncerramentoConteudoService(
        anuncios, historicos, localizacoes, busca, stories, storyAdministrativo,
        arquivoPublicidade, arquivoStory);
  }

  @Test
  void encerramentoDaContaExpiraStoryDiretoSemVinculoDeMidia() {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    UUID usuarioId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
        anuncioId,
        usuarioId,
        "qa-encerramento-story-direto",
        "Anuncio QA",
        "Descricao sintetica",
        StatusAnuncio.PUBLICADO,
        StatusModeracaoAnuncio.APROVADO,
        agora.minusDays(1));
    StoryAnuncioEntity story = StoryAnuncioEntity.criarAnuncio(
        UUID.randomUUID(),
        anuncioId,
        UUID.randomUUID(),
        "encerramento-story-direto",
        "e".repeat(64),
        agora.minusHours(1),
        agora.plusHours(23),
        usuarioId);
    Set<UUID> anuncioIds = Set.of(anuncioId);

    when(anuncios.findByUsuarioIdForLegalBlock(usuarioId)).thenReturn(List.of(anuncio));
    when(busca.findAllById(anuncioIds)).thenReturn(List.of());
    when(localizacoes.findByAnuncioIdIn(anuncioIds)).thenReturn(List.of());
    when(stories.findByCriadoPorForUpdate(usuarioId)).thenReturn(List.of(story));
    when(storyAdministrativo.bloquearAtivasDosAnuncios(anuncioIds)).thenReturn(List.of());

    var resultado = service.encerrar(usuarioId, UUID.randomUUID(), agora);

    assertThat(resultado.anunciosRemovidos()).isEqualTo(1);
    assertThat(resultado.storiesEncerrados()).isEqualTo(1);
    assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.REMOVIDO);
    assertThat(story.getStatus()).isEqualTo(StatusStoryAnuncio.EXPIRADO);
    verify(stories).saveAll(List.of(story));
    verify(arquivoPublicidade).registrarEstado(
        anuncioId, "CONTA_EXCLUIDA", null, agora);
    verify(arquivoStory).registrarEstadoPorUsuario(
        usuarioId, "CONTA_EXCLUIDA_STORY", null, agora);
  }

  @Test
  void encerramentoDaContaTambemRetiraStoryDeMidiaSemAnuncio() {
    OffsetDateTime agora = OffsetDateTime.parse("2026-09-25T10:00:00Z");
    UUID usuarioId = UUID.randomUUID();
    StoryAnuncioEntity story = StoryAnuncioEntity.criarMidiaUpload(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        "story-sintetico", "e".repeat(64), agora.minusHours(1), agora.plusHours(23), usuarioId);
    when(anuncios.findByUsuarioIdForLegalBlock(usuarioId)).thenReturn(List.of());
    when(stories.findByCriadoPorForUpdate(usuarioId)).thenReturn(List.of(story));

    var resultado = service.encerrar(usuarioId, usuarioId, agora);

    assertThat(resultado.storiesEncerrados()).isEqualTo(1);
    assertThat(story.getStatus()).isEqualTo(StatusStoryAnuncio.EXPIRADO);
    verify(stories).saveAll(List.of(story));
    verify(arquivoStory).registrarEstadoPorUsuario(
        usuarioId, "CONTA_EXCLUIDA_STORY", null, agora);
  }
}
